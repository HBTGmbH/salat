package org.tb.auth;

public enum AccessLevel {

    READ,
    WRITE,
    DELETE;

    public boolean satisfies(AccessLevel accessLevel) {
        return switch (accessLevel) {
            case READ -> true;
            case WRITE -> this == WRITE || this == DELETE;
            case DELETE -> this == DELETE;
            default -> false;
        };
    }

}
