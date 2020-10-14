package org.tb.web.action;

public class InvalidAccessTokenException extends Exception {

    private static final long serialVersionUID = 1924730270348279212L;

    public InvalidAccessTokenException(String message) {
        super(message);
    }
}
