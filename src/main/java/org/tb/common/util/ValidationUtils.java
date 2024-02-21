package org.tb.common.util;

import java.time.LocalDate;
import lombok.experimental.UtilityClass;
import org.apache.commons.validator.GenericValidator;

@UtilityClass
public class ValidationUtils {

  public static boolean isValidInteger(String value) {
    try {
      Integer.parseInt(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public static boolean isPositiveInteger(String value) {
    try {
      int intValue = Integer.parseInt(value);
      return intValue >= 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public static boolean isInRange(int value, int min, int max) {
    return GenericValidator.isInRange(value, min, max);
  }

  public static boolean isInRange(LocalDate value, LocalDate min, LocalDate max) {
    if (min == null && max == null) return true;
    if (min == null && !value.isAfter(max)) return true;
    if (max == null && !value.isBefore(min)) return true;
    return !value.isAfter(max) && !value.isBefore(min);
  }

}
