package org.tb.auth.domain;

import java.util.Set;

public enum AccessLevel {

    EXECUTE(),
    READ(EXECUTE),
    WRITE(READ, EXECUTE),
    DELETE(WRITE, READ, EXECUTE),
    LOGIN;

    private Set<AccessLevel> includedAccessLevels;

    private AccessLevel(AccessLevel... includedAccessLevels) {
        this.includedAccessLevels = Set.of(includedAccessLevels);
    }

    public boolean satisfies(AccessLevel accessLevel) {
        return this == accessLevel || includedAccessLevels.contains(accessLevel);
    }

}
