package org.tb.common.util;

import java.time.LocalDate;
import lombok.experimental.UtilityClass;
import org.apache.commons.validator.GenericValidator;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;

@UtilityClass
public class DataValidationUtils {

  public static void validDateRange(LocalDate begin, LocalDate end, ErrorCode errorCode) {
    if (end != null && end.isBefore(begin)) {
      throw new InvalidDataException(errorCode);
    }
  }

  public static void notNull(Object object, ErrorCode errorCode) {
    if (object == null) {
      throw new InvalidDataException(errorCode);
    }
  }

  public static void isTrue(boolean expression, ErrorCode errorCode) {
    if (expression == false) {
      throw new InvalidDataException(errorCode);
    }
  }

  public static void lengthIsInRange(String value, int min, int max, ErrorCode errorCode) {
    if(value == null || value.length() < min || value.length() > max) {
      throw new InvalidDataException(errorCode);
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
