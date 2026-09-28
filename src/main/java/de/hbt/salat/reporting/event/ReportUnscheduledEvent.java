package de.hbt.salat.reporting.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import de.hbt.salat.reporting.domain.ScheduledReportJob;

public class ReportUnscheduledEvent extends ApplicationEvent {

  @Getter
  private final ScheduledReportJob scheduledReportJob;

  public ReportUnscheduledEvent(Object source, ScheduledReportJob scheduledReportJob) {
    super(source);
    this.scheduledReportJob = scheduledReportJob;
  }

}
