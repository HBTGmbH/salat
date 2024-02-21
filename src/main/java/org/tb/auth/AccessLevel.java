package org.tb.auth;

public enum AccessLevel {

    EXECUTE(0),
    READ(1),
    WRITE(2),
    DELETE(3);

    private int level;

    private AccessLevel(int level) {
        this.level = level;
    }

    public boolean satisfies(AccessLevel accessLevel) {
        return this.level >= accessLevel.level;
    }

}
