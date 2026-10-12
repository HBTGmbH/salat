package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
import java.time.YearMonth;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OvertimeReportMonth {

  private final YearMonth yearMonth;
  private final Duration actual;
  /** the time surcharge credit (#1469), part of {@code sum} and {@code diff} */
  private final Duration surcharge;
  private final Duration adjustment;
  private final Duration sum;
  private final Duration target;
  private final Duration diff;
  private final Duration diffCumulative;

}
