package org.tb.reporting.service;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.reporting.domain.ScheduledReportJob;
import org.tb.reporting.persistence.ScheduledReportJobRepository;

@SpringBootTest(classes = {ScheduledReportJobScheduler.class})
public class ScheduledReportJobSchedulerTest {

  @Autowired
  private ScheduledReportJobScheduler scheduledReportJobScheduler;

  @MockitoBean
  private TaskScheduler taskScheduler;

  @MockitoBean
  private ScheduledReportJobRepository scheduledReportJobRepository;

  @MockitoBean
  private ScheduledReportJobService scheduledReportJobService;

  @Test
  void testScheduleAllJobsOnStartup_WithEnabledJobs() {
    // Arrange
    ScheduledReportJob job1 = new ScheduledReportJob(1L);
    job1.setName("Test Job 1");
    job1.setEnabled(true);
    job1.setCronExpression("0 0 * * * ?");

    ScheduledReportJob job2 = new ScheduledReportJob(2L);
    //job2.setId(2L);
    job2.setName("Test Job 2");
    job2.setCronExpression("0 15 * * * ?");

    when(scheduledReportJobRepository.findByEnabledTrue()).thenReturn(List.of(job1, job2));
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));

    // Act
    scheduledReportJobScheduler.scheduleAllJobsOnStartup();

    // Assert
    verify(taskScheduler, times(1)).schedule(any(Runnable.class),
        (Trigger) argThat(trigger -> "0 0 * * * ?".equals(trigger.toString())));
    verify(taskScheduler, times(1)).schedule(any(Runnable.class),
        (Trigger) argThat(trigger -> "0 15 * * * ?".equals(trigger.toString())));
  }

  @Test
  void testScheduleAllJobsOnStartup_NoEnabledJobs() {
    // Arrange
    when(scheduledReportJobRepository.findByEnabledTrue()).thenReturn(List.of());

    // Act
    scheduledReportJobScheduler.scheduleAllJobsOnStartup();

    // Assert
    verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
  }

  @Test
  void testScheduleAllJobsOnStartup_JobWithInvalidCron() {
    // Arrange
    ScheduledReportJob job = new ScheduledReportJob(1L);
    job.setName("Invalid Cron Job");
    job.setEnabled(true);
    job.setCronExpression("invalid-cron");

    when(scheduledReportJobRepository.findByEnabledTrue()).thenReturn(List.of(job));
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));

    // Act
    scheduledReportJobScheduler.scheduleAllJobsOnStartup();

    // Assert
    verifyNoInteractions(taskScheduler);
  }

  @Test
  void testScheduleAllJobsOnStartup_DefaultCronUsedForBlankCron() {
    // Arrange
    ScheduledReportJob job = new ScheduledReportJob(1L);
    job.setName("Blank Cron Job");
    job.setEnabled(true);
    job.setCronExpression("");

    when(scheduledReportJobRepository.findByEnabledTrue()).thenReturn(List.of(job));
    when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));

    // Act
    scheduledReportJobScheduler.scheduleAllJobsOnStartup();

    // Assert
    verify(taskScheduler, times(1)).schedule(any(Runnable.class),
        (Trigger) argThat(trigger -> "0 0 5 * * ?".equals(trigger.toString())));
  }
}