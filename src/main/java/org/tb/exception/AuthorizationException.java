package org.tb.exception;

public class AuthorizationException extends RuntimeException {
  public AuthorizationException(String message) {
    super(message);
  }
  public AuthorizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
