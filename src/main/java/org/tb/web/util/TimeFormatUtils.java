package org.tb.web.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TimeFormatUtils {

  private static NumberFormat hoursDecimalFormat = NumberFormat.getNumberInstance(Locale.GERMAN);
  private static NumberFormat timeMinutesFormat = NumberFormat.getNumberInstance(Locale.GERMAN);

  static {
    hoursDecimalFormat.setMinimumFractionDigits(2);
    hoursDecimalFormat.setMaximumFractionDigits(2);
    hoursDecimalFormat.setRoundingMode(RoundingMode.HALF_UP);
    timeMinutesFormat.setMinimumIntegerDigits(2);
  }

  public static synchronized String decimalFormatHours(double hoursDecimal) {
    return hoursDecimalFormat.format(hoursDecimal);
  }

  public static synchronized String decimalFormatMinutes(double minutesDecimal) {
    return hoursDecimalFormat.format(minutesDecimal / 60);
  }

  public static synchronized String decimalFormatHoursAndMinutes(long hours, long minutes) {
    double hoursDecimal = ((double)minutes / 60) + hours;
    return hoursDecimalFormat.format(hoursDecimal);
  }

  public static synchronized String timeFormatHoursAndMinutes(long hours, long minutes) {
    return hours + ":" + timeMinutesFormat.format(Math.abs(minutes));
  }

  public static synchronized String timeFormatMinutes(long minutes) {
    return timeFormatHoursAndMinutes(minutes / 60, minutes % 60);
  }

  public static synchronized String timeFormatHours(double hoursDecimal) {
    BigDecimal minutesValue = BigDecimal.valueOf(hoursDecimal * 60).setScale(0, RoundingMode.HALF_UP);
    return timeFormatMinutes(minutesValue.longValue());
  }

}
