package org.tb.exception;

import org.tb.ErrorCode;

public class InvalidDataException extends ErrorCodeException {

  public InvalidDataException(ErrorCode errorCode) {
    super(errorCode);
  }

  public InvalidDataException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }

}
