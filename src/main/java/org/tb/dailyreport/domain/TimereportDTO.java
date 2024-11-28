package org.tb.dailyreport.domain;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;
import org.tb.order.domain.OrderType;

/**
 * Timereports are very sensitive information. Thus we need to care about time reports in a special kind of way
 * to protect the data from unintended access or modification. This is why we are using a special class to
 * copy the entity data to a DTO and use it outside the {@link org.tb.dailyreport.service.TimereportService}.
 * Only {@link org.tb.dailyreport.service.TimereportService} should use {@link Timereport} directly.
 */
@Data
@Builder
public class TimereportDTO implements Serializable {

  private final long id;
  private final LocalDate referenceday;
  private final long employeeId;
  private final String employeeName;
  private final String employeeSign;
  private final long employeecontractId;
  private final String completeOrderSign;
  private final String customerorderSign;
  private final String customerorderDescription;
  private final long suborderId;
  private final String suborderDescription;
  private final OrderType orderType;
  private final String customerShortname;
  private final long customerorderId;
  private final long employeeorderId;
  private final Duration duration;
  private final String taskdescription;
  private final String status;
  private final boolean training;
  private final int sequencenumber;
  private final String releasedby;
  private final LocalDateTime released;
  private final String acceptedby;
  private final LocalDateTime accepted;
  private final String timeReportAsString;
  private final String employeeOrderAsString;
  private final boolean billable;
  private final LocalDateTime created;
  private final LocalDateTime lastupdate;
  private final String createdby;
  private final String lastupdatedby;
  private final boolean fitsToContract;
  private final long durationhours;
  private final long durationminutes;
  private final boolean holiday;

  public boolean matches5MinuteSchema() {
    return duration.toMinutesPart() % 5 == 0;
  }

}
