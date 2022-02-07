package org.tb.service;

import lombok.experimental.UtilityClass;
import org.tb.ErrorCode;
import org.tb.exception.InvalidDataException;

@UtilityClass
public class DataValidation {

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

  public static void isInRange(double value, double min, double max, ErrorCode errorCode) {
    if(value < min || value > max) {
      throw new InvalidDataException(errorCode);
    }
  }

  public static void lengthIsInRange(String value, int min, int max, ErrorCode errorCode) {
    if(value == null || value.length() < min || value.length() > max) {
      throw new InvalidDataException(errorCode);
    }
  }

}
