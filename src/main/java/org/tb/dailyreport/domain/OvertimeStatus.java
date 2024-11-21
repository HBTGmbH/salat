package org.tb.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class OvertimeStatus {

  private OvertimeStatusInfo total, currentMonth;

  @Data
  public static class OvertimeStatusInfo {
    private LocalDate begin, end;
    private long days, hours, minutes;
    private Duration duration;
    private boolean negative;
  }

}
