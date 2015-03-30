package org.tb.exception;

public abstract class TechnicalException extends RuntimeException {
	
	public TechnicalException(String message) {
		super(message);
	}
	
	public TechnicalException(String message, Throwable cause) {
		super(message, cause);
	}

}
