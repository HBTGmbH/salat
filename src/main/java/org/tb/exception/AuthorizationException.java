package org.tb.exception;

import org.tb.ErrorCode;

public class AuthorizationException extends ErrorCodeException {

  public AuthorizationException(ErrorCode errorCode) {
    super(errorCode);
  }

  public AuthorizationException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }

}
