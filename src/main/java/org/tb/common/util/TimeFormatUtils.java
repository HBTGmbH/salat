package org.tb.common.util;

import java.math.BigDecimal;
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

  @Deprecated
  public static String decimalFormatHours(double hoursDecimal) {
    return hoursDecimalFormatHolder.get().format(hoursDecimal);
  }

  @Deprecated
  public static String decimalFormatMinutes(double minutesDecimal) {
    return hoursDecimalFormatHolder.get().format(minutesDecimal / 60);
  }

  public static String decimalFormatHoursAndMinutes(long hours, long minutes) {
    double hoursDecimal = ((double)minutes / 60) + hours;
    return hoursDecimalFormatHolder.get().format(hoursDecimal);
  }

  public static String timeFormatHoursAndMinutes(long hours, long minutes) {
    return hours + ":" + timeMinutesFormatHolder.get().format(Math.abs(minutes));
  }

  public static String timeFormatMinutes(long minutes) {
    return timeFormatHoursAndMinutes(minutes / 60, minutes % 60);
  }

  @Deprecated
  public static String timeFormatHours(double hoursDecimal) {
    BigDecimal minutesValue = BigDecimal.valueOf(hoursDecimal * 60).setScale(0, RoundingMode.HALF_UP);
    return timeFormatMinutes(minutesValue.longValue());
  }

}
