package org.tb.common.exception;

public class BusinessRuleException extends ErrorCodeException {

  public BusinessRuleException(ErrorCode errorCode) {
    super(errorCode);
  }

  public BusinessRuleException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }

  public BusinessRuleException(ErrorCode errorCode, Object... arguments) {
    super(errorCode, arguments);
  }

}
