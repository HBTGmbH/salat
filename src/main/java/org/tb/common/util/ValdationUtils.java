package org.tb.common.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.validator.GenericValidator;

@UtilityClass
public class ValdationUtils {

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

}
