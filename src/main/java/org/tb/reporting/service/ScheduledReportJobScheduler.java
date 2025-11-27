package org.tb.reporting.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.AbstractRequestAttributes;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.reporting.domain.ScheduledReportJob;
import org.tb.reporting.event.ReportScheduledEvent;
import org.tb.reporting.event.ReportUnscheduledEvent;
import org.tb.reporting.persistence.ScheduledReportJobRepository;

/**
 * Schedules each enabled ScheduledReportJob according to its cronExpression.
 * Falls back to the global default cron if a job has no expression set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledReportJobScheduler {

  private final TaskScheduler taskScheduler;
  private final ScheduledReportJobRepository scheduledReportJobRepository;
  private final ScheduledReportJobService scheduledReportJobService;
  private final ConfigurableListableBeanFactory beanFactory;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;

  @Value("${salat.reporting.scheduler.cron:0 0 5 * * ?}")
  private String defaultCron;

  private final Set<ScheduledReportJobCronTask> scheduledTasks = new HashSet<>();

  @EventListener(ContextRefreshedEvent.class)
  public void scheduleAllJobsOnStartup() {
    List<ScheduledReportJob> jobs = scheduledReportJobRepository.findByEnabledTrue();
    log.info("Scheduling {} enabled ScheduledReportJobs", jobs.size());
    for (ScheduledReportJob job : jobs) {
      scheduleOrUnschedule(job);
    }
  }

  public ScheduledReportJobCronTask getScheduledTask(long jobId) {
    return scheduledTasks.stream()
        .filter(task -> Objects.equals(task.getJobId(), jobId))
        .findFirst()
        .orElse(null);
  }

  @EventListener
  synchronized void unscheduleJob(ReportUnscheduledEvent event) {
    var jobId = event.getScheduledReportJob().getId();
    unscheduleJob(jobId);
  }

  @EventListener
  void scheduleOrUnschedule(ReportScheduledEvent event) {
    var job = event.getScheduledReportJob();
    scheduleOrUnschedule(job);
  }

  private synchronized void unscheduleJob(long jobId) {
    var tasks = scheduledTasks.stream()
        .filter(t -> Objects.equals(t.getJobId(), jobId))
        .collect(Collectors.toSet());
    tasks.forEach(ScheduledReportJobCronTask::cancel);
    scheduledTasks.removeAll(tasks);
    tasks.forEach(
        task -> log.info("Unscheduled job id={}, expression={}", task.getJobId(), task.getCronExpression())
    );
  }

  private synchronized void scheduleJob(ScheduledReportJob job) {
    String cron = (job.getCronExpression() == null || job.getCronExpression().isBlank()) ? defaultCron : job.getCronExpression();
    try {
      CronTrigger trigger = new CronTrigger(cron);

      // Cancel previously scheduled job(s) if exists
      unscheduleJob(job.getId());

      ScheduledFuture<?> future = taskScheduler.schedule(
          () -> {
            runInTemporarySessionScope(() -> {
              try {
                scheduledReportJobService.executeScheduledReportJobById(job.getId());
              } catch (Exception e) {
                log.error("Failed to execute scheduled report job: {} (ID: {})", job.getName(), job.getId(), e);
              }
            });
          },
          trigger
      );

      if (future != null) {
        scheduledTasks.add(new ScheduledReportJobCronTask(job.getId(), job.getCronExpression(), future));
        String humanDesc = scheduledReportJobService.getHumanReadableCron(cron);
        log.info("Scheduled job id={} name='{}' with cron '{}' ({})", job.getId(), job.getName(), cron, humanDesc);
      } else {
        log.warn("TaskScheduler returned null future when scheduling job id={} name='{}'", job.getId(), job.getName());
      }
    } catch (IllegalArgumentException ex) {
      log.error("Invalid cron expression '{}' for job id={} name='{}'. Skipping scheduling.", cron, job.getId(), job.getName(), ex);
    }
  }

  private void scheduleOrUnschedule(ScheduledReportJob job) {
    if (job.isEnabled()) {
      scheduleJob(job);
    } else {
      unscheduleJob(job.getId());
    }
  }

  private void runInTemporarySessionScope(Runnable task) {
    try {
      // Initialize a temporary session scope for the duration of this scheduled execution
      setRequestAttributes(new SchedulerMockRequestAttributes(), true);
      initializeAuthorizedUserForJobExecution();
      task.run();
    } finally {
      destroyAuthorizedUserForJobExecution();
      resetRequestAttributes();
    }
  }

  private void initializeAuthorizedUserForJobExecution() {
    // Initialize a synthetic AuthorizedUser within this session scope
    AuthorizedUser systemUser = authorizedUserProvider.getObject();
    systemUser.initForJob();
  }

  private void destroyAuthorizedUserForJobExecution() {
    try {
      beanFactory.destroyScopedBean("authorizedUser");
    } catch (Exception ignored) {
      // ignore cleanup issues
    }
  }

  @RequiredArgsConstructor
  @Getter
  public static class ScheduledReportJobCronTask {
    private final long jobId;
    private final String cronExpression;
    private final ScheduledFuture<?> future;

    public void cancel() {
      future.cancel(false);
    }

    public @Nullable Instant nextExecution() {
      if (future != null && !future.isCancelled()) {
        long delay = future.getDelay(TimeUnit.SECONDS);
        if (delay > 0) {
          return Instant.now().plusSeconds(delay);
        }
      }
      return null;
    }

  }

  public static class SchedulerMockRequestAttributes extends AbstractRequestAttributes {
    private final Map<Integer, Map<String, Object>> attributes = new HashMap<>();
    private final String mockSessionId = UUID.randomUUID().toString();
    private final Object mutex = this;

    @Override
    protected void updateAccessedSessionAttributes() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable Object getAttribute(String name, int scope) {
      return attributes.computeIfAbsent(scope, k -> new HashMap<>()).get(name);
    }

    @Override
    public void setAttribute(String name, Object value, int scope) {
      attributes.computeIfAbsent(scope, k -> new HashMap<>()).put(name, value);
    }

    @Override
    public void removeAttribute(String name, int scope) {
      attributes.computeIfAbsent(scope, k -> new HashMap<>()).remove(name);
    }

    @Override
    public String[] getAttributeNames(int scope) {
      return attributes.computeIfAbsent(scope, k -> new HashMap<>()).keySet().toArray(new String[0]);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback, int scope) {
      throw new UnsupportedOperationException(name + "#" + callback + "#" + scope);
    }

    @Override
    public @Nullable Object resolveReference(String key) {
      throw new UnsupportedOperationException(key);
    }

    @Override
    public String getSessionId() {
      return mockSessionId;
    }

    @Override
    public Object getSessionMutex() {
      return mutex;
    }
  }

}
