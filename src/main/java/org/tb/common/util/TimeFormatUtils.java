package org.tb.common.util;

import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
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

}
