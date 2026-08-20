package org.tb.common.util;

import static java.lang.Math.abs;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DurationUtils {

  public static String format(Duration duration) {
    return format(duration, true);
  }

  public static String format(Duration duration, boolean printZero) {
    if(duration == null || duration.isZero()) {
      return printZero ? "0:00" : "";
    }
    StringBuilder sb = new StringBuilder();
    if(duration.isNegative()) {
      sb.append('-');
    }
    sb.append(abs(duration.toHours())).append(':');
    sb.append("%02d".formatted(abs(duration.toMinutesPart())));
    return sb.toString();
  }

  public static Duration parseDuration(String value) {
    if(value == null || value.trim().isEmpty()) {
      return Duration.ZERO;
    }
    boolean negative = value.startsWith("-");
    if(negative) {
      value = value.substring(1);
    }
    long minutes = 0;
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

  private static final Pattern FLEX_HOURS_AND_MINUTES = Pattern.compile("^(\\d{1,3})h(\\d{1,2})m?$");
  private static final Pattern FLEX_HOURS = Pattern.compile("^(\\d{1,3})h$");
  private static final Pattern FLEX_MINUTES = Pattern.compile("^(\\d{1,4})m$");
  private static final Pattern FLEX_COLON = Pattern.compile("^(\\d{1,3}):(\\d{1,2})$");
  private static final Pattern FLEX_COLON_HOURS = Pattern.compile("^(\\d{1,3}):$");
  private static final Pattern FLEX_COLON_MINUTES = Pattern.compile("^:(\\d{1,2})$");
  private static final Pattern FLEX_DECIMAL = Pattern.compile("^(\\d{1,3})[.,](\\d{1,2})$");
  private static final Pattern FLEX_DIGITS = Pattern.compile("^\\d{1,4}$");

  /**
   * Parses the tolerant duration input formats offered by the time input widget (#830) and returns
   * the total number of minutes.
   *
   * <p>Accepted, all case insensitive and whitespace tolerant:
   * <pre>
   *   1:30  1:3  1:  :30       →  90, 63, 60, 30
   *   2h30  2h30m  2h  90m     →  150, 150, 120, 90
   *   1,5   1.5   0,25         →  90, 90, 15   (decimal hours, as produced by decimalFormat)
   *   8                        →  480          (one digit  = hours)
   *   30                       →  30           (two digits = minutes)
   *   130   1230               →  90, 750      (three or four digits = H:MM / HH:MM)
   * </pre>
   *
   * <p>The digit-only rules deliberately mirror the historic client side mask, so that habits built
   * up with the old {@code durationBlur} keep working: {@code 8} means eight hours, {@code 30} means
   * thirty minutes.
   *
   * <p>Returns an empty result for anything that cannot be understood — callers keep their existing
   * validation error in that case instead of silently booking a wrong value. Negative values are not
   * supported here; overtime style fields keep using {@link #parseDuration(String)}.
   */
  public static OptionalLong parseFlexibleMinutes(String value) {
    if(value == null) {
      return OptionalLong.empty();
    }
    var normalized = value.trim().toLowerCase().replace(" ", "");
    if(normalized.isEmpty()) {
      return OptionalLong.empty();
    }

    var hoursAndMinutes = FLEX_HOURS_AND_MINUTES.matcher(normalized);
    if(hoursAndMinutes.matches()) {
      return OptionalLong.of(minutes(hoursAndMinutes.group(1), hoursAndMinutes.group(2)));
    }
    var hours = FLEX_HOURS.matcher(normalized);
    if(hours.matches()) {
      return OptionalLong.of(Long.parseLong(hours.group(1)) * MINUTES_PER_HOUR);
    }
    var minutesOnly = FLEX_MINUTES.matcher(normalized);
    if(minutesOnly.matches()) {
      return OptionalLong.of(Long.parseLong(minutesOnly.group(1)));
    }
    var colon = FLEX_COLON.matcher(normalized);
    if(colon.matches()) {
      return OptionalLong.of(minutes(colon.group(1), colon.group(2)));
    }
    var colonHours = FLEX_COLON_HOURS.matcher(normalized);
    if(colonHours.matches()) {
      return OptionalLong.of(Long.parseLong(colonHours.group(1)) * MINUTES_PER_HOUR);
    }
    var colonMinutes = FLEX_COLON_MINUTES.matcher(normalized);
    if(colonMinutes.matches()) {
      return OptionalLong.of(Long.parseLong(colonMinutes.group(1)));
    }
    var decimal = FLEX_DECIMAL.matcher(normalized);
    if(decimal.matches()) {
      var decimalHours = new BigDecimal(decimal.group(1) + "." + decimal.group(2));
      return OptionalLong.of(decimalHours.multiply(BigDecimal.valueOf(MINUTES_PER_HOUR))
          .setScale(0, RoundingMode.HALF_UP)
          .longValueExact());
    }
    if(FLEX_DIGITS.matcher(normalized).matches()) {
      return OptionalLong.of(switch (normalized.length()) {
        case 1 -> Long.parseLong(normalized) * MINUTES_PER_HOUR;
        case 2 -> Long.parseLong(normalized);
        case 3 -> minutes(normalized.substring(0, 1), normalized.substring(1));
        default -> minutes(normalized.substring(0, 2), normalized.substring(2));
      });
    }
    return OptionalLong.empty();
  }

  private static long minutes(String hours, String minutes) {
    return Long.parseLong(hours) * MINUTES_PER_HOUR + Long.parseLong(minutes);
  }

  public static boolean validateDuration(String value) {
    if(value == null || value.trim().isEmpty()) {
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

  public static String formatWithWorkingdays(Duration duration, Duration dailyWorkingTime) {
    long trainingDays = 0;
    long trainingHours = 0;
    long trainingMinutes = 0;
    long totalMinutes = duration.toMinutes();
    long dailyMinutes = dailyWorkingTime.toMinutes();
    if (dailyMinutes != 0) {
      trainingDays = totalMinutes / dailyMinutes;
      long restMinutes = totalMinutes % dailyMinutes;
      trainingHours = restMinutes / MINUTES_PER_HOUR;
      trainingMinutes = restMinutes % MINUTES_PER_HOUR;
    }
    return "%02d:%02d:%02d".formatted(trainingDays, trainingHours, trainingMinutes);
  }

  public static String decimalFormat(Duration duration) {
    if(duration == null) {
      return "";
    }
    if(duration.isZero()) {
      return "0,00";
    }
    StringBuilder sb = new StringBuilder();
    if(duration.isNegative()) {
      sb.append('-');
    }
    sb.append(abs(duration.toHours())).append(',');
    int minutesDecimal = BigDecimal.valueOf(duration.toMinutesPart())
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), RoundingMode.HALF_UP)
        .intValueExact();
    sb.append("%02d".formatted(abs(minutesDecimal)));
    return sb.toString();
  }

}
