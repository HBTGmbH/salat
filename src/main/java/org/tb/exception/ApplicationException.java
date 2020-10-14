package org.tb.exception;

public abstract class ApplicationException extends Exception {
    private static final long serialVersionUID = 138293870343026629L;

    public ApplicationException(String message) {
        super(message);
    }

}
