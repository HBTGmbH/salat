package org.tb.service;

import lombok.experimental.UtilityClass;
import org.tb.exception.InvalidDataException;
import org.tb.exception.LogicException;

@UtilityClass
public class BusinessRuleChecks {

  public static void isTrue(boolean expression, String message) {
    if (expression == false) {
      throw new LogicException(message);
    }
  }

  public static void notEmpty(Object object, String message) {
    if (object == null) {
      throw new LogicException(message);
    }
  }
}
