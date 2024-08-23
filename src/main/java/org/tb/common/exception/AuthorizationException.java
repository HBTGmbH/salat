package org.tb.common.exception;

import org.tb.common.ErrorCode;

public class AuthorizationException extends ErrorCodeException {

  public AuthorizationException(ErrorCode errorCode) {
    super(errorCode);
  }

  public AuthorizationException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }

  public AuthorizationException(ErrorCode errorCode, Object... arguments) {
    super(errorCode, arguments);
  }

}
