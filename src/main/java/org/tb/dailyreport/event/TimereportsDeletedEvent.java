package org.tb.dailyreport.event;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class TimereportsDeletedEvent {

  private final List<TimereportDeleteId> ids;

  @Data
  public static class TimereportDeleteId {

    private final long timereportId;
    private final long employeeorderId;
    private final LocalDate referenceDay;

  }

}
