package org.tb.common.exception;

public class InvalidDataException extends ErrorCodeException {

  public InvalidDataException(ErrorCode errorCode) {
    super(errorCode);
  }

  public InvalidDataException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }

  public InvalidDataException(ErrorCode errorCode, Object... arguments) {
    super(errorCode, arguments);
  }

}
