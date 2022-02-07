package org.tb.exception;

import lombok.Getter;
import org.tb.ErrorCode;

@Getter
public class ErrorCodeException extends RuntimeException {

  private final ErrorCode errorCode;

  public ErrorCodeException(ErrorCode errorCode) {
    super(errorCode.toString());
    this.errorCode = errorCode;
  }

  public ErrorCodeException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.toString(), cause);
    this.errorCode = errorCode;
  }

}
