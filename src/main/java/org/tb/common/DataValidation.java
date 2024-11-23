package org.tb.common;

import java.time.LocalDate;
import lombok.experimental.UtilityClass;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;

@UtilityClass
public class DataValidation {

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

}
