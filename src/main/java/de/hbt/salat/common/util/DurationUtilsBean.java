package de.hbt.salat.common.util;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component("durationUtils")
public class DurationUtilsBean {

  public String format(Duration duration) {
    return DurationUtils.format(duration);
  }

  /** {@code HH:mm}, as the CSV import reads and writes it (#1398); empty for none. */
  public String formatHoursMinutes(Duration duration) {
    return duration == null ? "" : DurationUtils.formatHoursMinutes(duration);
  }

}
