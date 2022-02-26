package org.tb.common.util;

import static java.lang.Math.abs;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import java.time.Duration;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DurationUtils {

  public String format(Duration duration) {
    if(duration == null) {
      return "";
    }
    if(duration.isZero()) {
      return "0:00";
    }
    StringBuilder sb = new StringBuilder();
    if(duration.isNegative()) {
      sb.append('-');
    }
    sb.append(abs(duration.toHours())).append(':');
    sb.append(String.format("%02d", abs(duration.toMinutesPart())));
    return sb.toString();
  }

  public Duration parseDuration(String value) {
    if(value == null || value.isEmpty()) {
      return Duration.ZERO;
    }
    boolean negative = value.startsWith("-");
    if(negative) {
      value = value.substring(1);
    }
    int minutes = 0;
    if(value.contains(":")) {
      String[] split = value.split(":");
      if(!split[0].isEmpty()) {
        minutes = Integer.parseInt(split[0]) * MINUTES_PER_HOUR;
      }
      if(split.length > 1 && !split[1].isEmpty()) {
        minutes += Integer.parseInt(split[1]);
      }
    } else {
      minutes = Integer.parseInt(value) * MINUTES_PER_HOUR;
    }
    if(negative) {
      minutes = minutes * -1;
    }

    return Duration.ofMinutes(minutes);
  }

  public boolean validateDuration(String value) {
    if(value == null || value.isEmpty()) {
      return true;
    }
    // cut leading minues
    if(value.startsWith("-")) {
      value = value.substring(1);
    }
    try {
      // check if hours:minutes
      if(value.contains(":")) {
        String[] split = value.split(":");
        if(split.length > 2) {
          return false;
        }
        if(!split[0].isEmpty()) {
          int hours = Integer.parseInt(split[0]);
          // negative value is not allowed. Minues sign must be present at the very start of the value, which was already cut
          if(hours < 0) {
            return false;
          }
        }
        if(split.length == 2 && !split[1].isEmpty()) {
          int minutes = Integer.parseInt(split[1]);
          // negative value is not allowed. Minues sign must be present at the very start of the value, which was already cut
          if(minutes < 0) {
            return false;
          }
          // minutes may be no more than 59
          if(minutes > 59) {
            return false;
          }
        }
      // check hours only as no ":" in value
      } else {
        int hours = Integer.parseInt(value);
        // negative value is not allowed. Minues sign must be present at the very start of the value, which was already cut
        if(hours < 0) {
          return false;
        }
      }
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

}
