package org.tb.service;

import lombok.experimental.UtilityClass;
import org.tb.ErrorCode;
import org.tb.exception.BusinessRuleException;

@UtilityClass
public class BusinessRuleChecks {

  public static void isTrue(boolean expression, ErrorCode errorCode) {
    if (expression == false) {
      throw new BusinessRuleException(errorCode);
    }
  }

  public static void notEmpty(String value, ErrorCode errorCode) {
    if (value == null || value.trim().isEmpty()) {
      throw new BusinessRuleException(errorCode);
    }
  }
}
