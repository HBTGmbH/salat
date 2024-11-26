package org.tb.common.exception;

import java.util.List;

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

  public BusinessRuleException(List<ServiceFeedbackMessage> messages) {
    super(messages);
  }

}
