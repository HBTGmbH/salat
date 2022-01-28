package org.tb.exception;

public class LogicException extends RuntimeException {
  public LogicException(String message, Throwable e) {
    super(message, e);
  }
  public LogicException(Throwable e) {
    super(e);
  }
}
