package org.tb.dailyreport.domain;

import java.time.Duration;
import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OvertimeReportMonth {

  private final YearMonth yearMonth;
  private final Duration actual;
  private final Duration adjustment;
  private final Duration sum;
  private final Duration target;
  private final Duration diff;
  private final Duration diffCumulative;

}
