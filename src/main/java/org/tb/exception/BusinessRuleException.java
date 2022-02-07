package org.tb.exception;

import org.tb.ErrorCode;

public class BusinessRuleException extends ErrorCodeException {

  public BusinessRuleException(ErrorCode errorCode) {
    super(errorCode);
  }

  public BusinessRuleException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }

}
