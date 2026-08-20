package org.tb.common.util;

import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TimeFormatUtils {

  private static final ThreadLocal<NumberFormat> hoursDecimalFormatHolder = ThreadLocal.withInitial(() -> {
    NumberFormat hoursDecimalFormat = NumberFormat.getNumberInstance(Locale.GERMAN);
    hoursDecimalFormat.setMinimumFractionDigits(2);
    hoursDecimalFormat.setMaximumFractionDigits(2);
    hoursDecimalFormat.setRoundingMode(RoundingMode.HALF_UP);
    return hoursDecimalFormat;
  });

  private static final ThreadLocal<NumberFormat> timeMinutesFormatHolder = ThreadLocal.withInitial(() -> {
    NumberFormat timeMinutesFormat = NumberFormat.getNumberInstance(Locale.GERMAN);
    timeMinutesFormat.setMinimumIntegerDigits(2);
    return timeMinutesFormat;
  });

  public static String decimalFormatHoursAndMinutes(long hours, long minutes) {
    double hoursDecimal = ((double)minutes / MINUTES_PER_HOUR) + hours;
    return hoursDecimalFormatHolder.get().format(hoursDecimal);
  }

  public static String timeFormatHoursAndMinutes(long hours, long minutes) {
    return hours + ":" + timeMinutesFormatHolder.get().format(Math.abs(minutes));
  }

  public static String decimalFormatMinutes(long minutes) {
    return decimalFormatHoursAndMinutes(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR);
  }

  public static String timeFormatMinutes(long minutes) {
    return timeFormatHoursAndMinutes(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR);
  }

  private static final Pattern TIME_SEPARATED = Pattern.compile("^(\\d{1,2})[:.,](\\d{1,2})$");
  private static final Pattern TIME_DIGITS = Pattern.compile("^\\d{1,4}$");

  /**
   * Parses the tolerant time-of-day formats offered by the time input widget (#830).
   *
   * <pre>
   *   8:30  08:30  8.30  8,30   →  08:30   (a separator always divides hours from minutes)
   *   8     08     13         →  08:00, 08:00, 13:00
   *   830   0830   1830       →  08:30, 08:30, 18:30
   * </pre>
   *
   * <p>Note that the digit rules differ from {@link DurationUtils#parseFlexibleMinutes(String)} on
   * purpose: for a time of day two digits are an hour ({@code 13} is one in the afternoon), while for
   * a duration they are minutes ({@code 13} is a quarter of an hour). Likewise a decimal separator
   * divides hours from minutes here — {@code 8.30} is half past eight — whereas for a duration it
   * introduces decimal hours.
   *
   * <p>Returns an empty result for anything out of range or not understood; callers keep their
   * existing fallback so that a typo never silently becomes a different time.
   */
  public static Optional<LocalTime> parseFlexibleTimeOfDay(String value) {
    if(value == null) {
      return Optional.empty();
    }
    var normalized = value.trim().replace(" ", "");
    if(normalized.isEmpty()) {
      return Optional.empty();
    }

    var separated = TIME_SEPARATED.matcher(normalized);
    if(separated.matches()) {
      return timeOf(Integer.parseInt(separated.group(1)), Integer.parseInt(separated.group(2)));
    }
    if(TIME_DIGITS.matcher(normalized).matches()) {
      return switch (normalized.length()) {
        case 1, 2 -> timeOf(Integer.parseInt(normalized), 0);
        case 3 -> timeOf(Integer.parseInt(normalized.substring(0, 1)),
            Integer.parseInt(normalized.substring(1)));
        default -> timeOf(Integer.parseInt(normalized.substring(0, 2)),
            Integer.parseInt(normalized.substring(2)));
      };
    }
    return Optional.empty();
  }

  private static Optional<LocalTime> timeOf(int hour, int minute) {
    if(hour > 23 || minute > 59) {
      return Optional.empty();
    }
    return Optional.of(LocalTime.of(hour, minute));
  }

}
