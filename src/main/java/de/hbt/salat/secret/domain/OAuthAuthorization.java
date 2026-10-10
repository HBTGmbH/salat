package de.hbt.salat.secret.domain;

import org.springframework.http.ResponseCookie;

/**
 * The start of a connection attempt (#1417): where the browser goes to sign in at the provider, and
 * the cookie that brings {@code state} and the PKCE verifier back to the callback (ADR-0038 §8).
 */
public record OAuthAuthorization(String redirectUrl, ResponseCookie cookie) {

  @Override
  public String toString() {
    return "OAuthAuthorization[]";
  }
}
