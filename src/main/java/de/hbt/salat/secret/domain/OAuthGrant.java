package de.hbt.salat.secret.domain;

import java.time.Instant;
import java.util.Set;

/**
 * The tokens a provider handed out for an authorization code (#1417), before the owner has found out
 * who and what they are for. The owner completes them to {@link OAuthTokens} and stores those; the
 * grant itself is never stored.
 *
 * @param scopes the scopes the provider granted, empty when it did not name them
 */
public record OAuthGrant(String provider, String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                         Set<String> scopes) {

  public OAuthGrant {
    scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
  }

  /**
   * @param connection the account, the resource and who connected, as the owner found them out with
   *     the access token
   */
  public OAuthTokens toTokens(OAuthConnection connection) {
    return new OAuthTokens(accessToken, accessTokenExpiresAt, refreshToken, connection);
  }

  @Override
  public String toString() {
    return "OAuthGrant[provider=" + provider + ", expiresAt=" + accessTokenExpiresAt + ", scopes=" + scopes + "]";
  }
}
