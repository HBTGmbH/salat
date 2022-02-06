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

}
