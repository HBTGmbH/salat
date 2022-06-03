package org.tb.employee.domain;

import java.time.Duration;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OvertimeReportTotal {

  private final Duration actual;
  private final Duration adjustment;
  private final Duration sum;
  private final Duration target;
  private final Duration diff;

}
