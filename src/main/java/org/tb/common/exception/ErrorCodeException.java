package org.tb.common.exception;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import org.tb.common.ErrorCode;

@Getter
public class ErrorCodeException extends RuntimeException {

  private final ErrorCode errorCode;
  private final List<Object> arguments;

  public ErrorCodeException(ErrorCode errorCode, Object... arguments) {
    super(errorCode.toString());
    this.errorCode = errorCode;
    this.arguments = arguments != null ? Arrays.asList(arguments) : List.of();
  }

  public ErrorCodeException(ErrorCode errorCode) {
    super(errorCode.toString());
    this.errorCode = errorCode;
    this.arguments = List.of();
  }

  public ErrorCodeException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.toString(), cause);
    this.errorCode = errorCode;
    this.arguments = List.of();
  }

}
