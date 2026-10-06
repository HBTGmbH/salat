package de.hbt.salat.common.exception;

import java.util.List;

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

  /** Several findings at once, e.g. one per faulty line of an import. */
  public InvalidDataException(List<ServiceFeedbackMessage> messages) {
    super(messages);
  }

}
