package org.tb.reporting.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.CronType;
import com.cronutils.parser.CronParser;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;
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

  private final Map<Long, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

  @EventListener(ContextRefreshedEvent.class)
  public void scheduleAllJobsOnStartup() {
    List<ScheduledReportJob> jobs = scheduledReportJobRepository.findByEnabledTrue();
    log.info("Scheduling {} enabled ScheduledReportJobs", jobs.size());
    for (ScheduledReportJob job : jobs) {
      scheduleOrUnschedule(job);
    }
  }

  private synchronized void scheduleJob(ScheduledReportJob job) {
    String cron = (job.getCronExpression() == null || job.getCronExpression().isBlank()) ? defaultCron : job.getCronExpression();
    try {
      CronTrigger trigger = new CronTrigger(cron);

      // Cancel previously scheduled instance if exists
      ScheduledFuture<?> existing = scheduledTasks.remove(job.getId());
      if (existing != null) {
        existing.cancel(false);
      }

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
        scheduledTasks.put(job.getId(), future);
        String humanDesc = getHumanReadableCron(cron);
        log.info("Scheduled job id={} name='{}' with cron '{}' ({})", job.getId(), job.getName(), cron, humanDesc);
      } else {
        log.warn("TaskScheduler returned null future when scheduling job id={} name='{}'", job.getId(), job.getName());
      }
    } catch (IllegalArgumentException ex) {
      log.error("Invalid cron expression '{}' for job id={} name='{}'. Skipping scheduling.", cron, job.getId(), job.getName(), ex);
    }
  }

  private String getHumanReadableCron(String cronExpr) {
    try {
      CronDefinition def = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
      CronParser parser = new CronParser(def);
      Cron cron = parser.parse(cronExpr);
      cron.validate();
      CronDescriptor descriptor = CronDescriptor.instance(Locale.ENGLISH);
      return descriptor.describe(cron);
    } catch (Exception e) {
      return "unrecognized/invalid cron pattern";
    }
  }

  @EventListener
  synchronized void unscheduleJob(ReportUnscheduledEvent event) {
    var jobId = event.getScheduledReportJob().getId();
    unscheduleJob(jobId);
  }

  private synchronized void unscheduleJob(long jobId) {
    ScheduledFuture<?> existing = scheduledTasks.remove(jobId);
    if (existing != null) {
      existing.cancel(false);
      log.info("Unscheduled job id={}", jobId);
    }
  }

  @EventListener
  void scheduleOrUnschedule(ReportScheduledEvent event) {
    var job = event.getScheduledReportJob();
    scheduleOrUnschedule(job);
  }

  private void scheduleOrUnschedule(ScheduledReportJob job) {
    if (job.isEnabled()) {
      scheduleJob(job);
    } else {
      unscheduleJob(job.getId());
    }
  }

  private void runInTemporarySessionScope(Runnable task) {
    // Initialize a temporary session scope for the duration of this scheduled execution
    Scope previousSessionScope = beanFactory.getRegisteredScope(WebApplicationContext.SCOPE_SESSION);
    var temporarySessionScope = new SimpleThreadScope();
    beanFactory.registerScope(WebApplicationContext.SCOPE_SESSION, temporarySessionScope);

    try {
      initializeAuthorizedUserForJobExecution();
      task.run();
    } finally {
      // Clean up session-scoped beans and restore original session scope
      try {
        beanFactory.destroyScopedBean("authorizedUser");
      } catch (Exception ignored) {
        // ignore cleanup issues
      }
      if (previousSessionScope != null) {
        beanFactory.registerScope(WebApplicationContext.SCOPE_SESSION, previousSessionScope);
      }
    }
  }

  private void initializeAuthorizedUserForJobExecution() {
    // Initialize a synthetic AuthorizedUser within this session scope
    AuthorizedUser systemUser = authorizedUserProvider.getObject();
    systemUser.initForJob();
  }

}
