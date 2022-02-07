package org.tb.service;

import lombok.experimental.UtilityClass;
import org.tb.exception.InvalidDataException;

@UtilityClass
public class DataValidation {

  public static void notNull(Object object, String message) {
    if (object == null) {
      throw new InvalidDataException(message);
    }
  }

  public static void isTrue(boolean expression, String message) {
    if (expression == false) {
      throw new InvalidDataException(message);
    }
  }

  public static void isInRange(double value, double min, double max, String message) {
    if(value < min || value > max) {
      throw new InvalidDataException(message);
    }
  }

  public static void lengthIsInRange(String value, int min, int max, String message) {
    if(value == null || value.length() < min || value.length() > max) {
      throw new InvalidDataException(message);
    }
  }

}
