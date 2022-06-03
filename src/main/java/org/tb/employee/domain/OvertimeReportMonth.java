package org.tb.employee.domain;

import java.time.Duration;
import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OvertimeReportMonth implements Comparable<OvertimeReportMonth> {

  private final YearMonth yearMonth;
  private final Duration actual;
  private final Duration adjustment;
  private final Duration sum;
  private final Duration target;
  private final Duration diff;
  private final Duration diffCumulative;

  @Override
  public int compareTo(OvertimeReportMonth o) {
    return yearMonth.compareTo(o.yearMonth);
  }
}
