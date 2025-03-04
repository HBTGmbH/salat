package org.tb.common.util;

import java.util.Collection;
import lombok.experimental.UtilityClass;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;

@UtilityClass
public class BusinessRuleCheckUtils {

  public static void isTrue(boolean expression, ErrorCode errorCode) {
    if (!expression) {
      throw new BusinessRuleException(errorCode);
    }
  }

  public static void isFalse(boolean expression, ErrorCode errorCode) {
    if (expression) {
      throw new BusinessRuleException(errorCode);
    }
  }

  public static void notEmpty(String value, ErrorCode errorCode) {
    if (value == null || value.trim().isEmpty()) {
      throw new BusinessRuleException(errorCode);
    }
  }

  public static void empty(Collection<?> collection, ErrorCode errorCode) {
    if (collection != null && !collection.isEmpty()) {
      throw new BusinessRuleException(errorCode);
    }
  }

}
