package de.hbt.salat.common.web;

public interface LoginSignProvider {

    String getEffectiveLoginSign();

    /**
     * The id of the login the caller acts as, {@code null} without one — what a record of another module stores to
     * name its owner, never the login name (#1204). For modules that may not import {@code auth}, such as
     * {@code common} (#1532).
     */
    Long getEffectiveUserId();
}
