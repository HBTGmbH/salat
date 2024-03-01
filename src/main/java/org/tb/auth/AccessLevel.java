package org.tb.auth;

import java.util.Set;

public enum AccessLevel {

    EXECUTE(),
    READ(EXECUTE),
    WRITE(READ, EXECUTE),
    DELETE(WRITE, READ, EXECUTE),
    RELEASE,
    ACCEPT,
    REOPEN,
    LOGIN;

    private Set<AccessLevel> includedAccessLevels;

    private AccessLevel(AccessLevel... includedAccessLevels) {
        this.includedAccessLevels = Set.of(includedAccessLevels);
    }

    public boolean satisfies(AccessLevel accessLevel) {
        return this == accessLevel || includedAccessLevels.contains(accessLevel);
    }

}
