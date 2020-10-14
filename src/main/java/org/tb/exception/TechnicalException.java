package org.tb.exception;

public abstract class TechnicalException extends RuntimeException {
    private static final long serialVersionUID = -4369282670953242771L;

    public TechnicalException(String message) {
        super(message);
    }

    public TechnicalException(String message, Throwable cause) {
        super(message, cause);
    }

}
