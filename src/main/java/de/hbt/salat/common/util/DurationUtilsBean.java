package de.hbt.salat.common.util;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component("durationUtils")
public class DurationUtilsBean {

  public String format(Duration duration) {
    return DurationUtils.format(duration);
  }

}
