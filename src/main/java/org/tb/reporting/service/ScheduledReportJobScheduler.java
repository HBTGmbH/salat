package org.tb.reporting.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.CronType;
import com.cronutils.parser.CronParser;
import java.util.Locale;
import org.tb.reporting.domain.ScheduledReportJob;
import org.tb.reporting.persistence.ScheduledReportJobRepository;

/**
 * Schedules each enabled ScheduledReportJob according to its cronExpression.
 * Falls back to the global default cron if a job has no expression set.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledReportJobScheduler {

  private final TaskScheduler taskScheduler;
  private final ScheduledReportJobRepository scheduledReportJobRepository;
  private final ReportSchedulerService reportSchedulerService;

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
            try {
              reportSchedulerService.executeScheduledReportJobById(job.getId());
            } catch (Exception e) {
              log.error("Error running ScheduledReportJob id={} name={}", job.getId(), job.getName(), e);
            }
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
      log.error("Invalid cron expression '{}' for job id={} name='{}'. Skipping scheduling.", cron, job.getId(), job.getName());
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

  public synchronized void unscheduleJob(Long jobId) {
    ScheduledFuture<?> existing = scheduledTasks.remove(jobId);
    if (existing != null) {
      existing.cancel(false);
      log.info("Unscheduled job id={}", jobId);
    }
  }

  public void scheduleOrUnschedule(ScheduledReportJob job) {
    if (job.isEnabled()) {
      scheduleJob(job);
    } else {
      unscheduleJob(job.getId());
    }
  }
}
