package de.hbt.salat.reporting.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import de.hbt.salat.reporting.domain.ScheduledReportJob;

public class ReportScheduledEvent extends ApplicationEvent {

  @Getter
  private final ScheduledReportJob scheduledReportJob;

  public ReportScheduledEvent(Object source, ScheduledReportJob scheduledReportJob) {
    super(source);
    this.scheduledReportJob = scheduledReportJob;
  }

}
