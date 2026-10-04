package de.hbt.salat.auth.domain;

import org.springframework.security.core.GrantedAuthority;

/**
 * The id of the {@link SalatUser} a request is authenticated as (#1330), carried in the
 * {@code Authentication} next to the roles. The login is read from the database at every
 * authentication anyway, to turn its status into roles; the id comes along with it, so
 * {@link AuthorizedUser#getEffectiveUserId()} needs no lookup of its own.
 *
 * <p>No role: nothing checks for this authority, {@code hasRole} never matches it.
 */
public record LoginIdAuthority(long userId) implements GrantedAuthority {

  @Override
  public String getAuthority() {
    return "LOGIN_ID_" + userId;
  }
}
