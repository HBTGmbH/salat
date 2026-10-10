package de.hbt.salat.jira.oauth;

import java.time.Instant;
import java.util.Set;
import de.hbt.salat.secret.domain.OAuthConnection;
import de.hbt.salat.secret.domain.OAuthTokens;

/**
 * The tokens Atlassian handed out for an authorization code (#1417), before the replication has found
 * out who and what they are for. It completes them to {@link OAuthTokens} and stores those in the module
 * {@code secret}; the grant itself is never stored.
 *
 * @param scopes the scopes the provider granted, empty when it did not name them
 */
public record JiraOAuthGrant(String provider, String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                         Set<String> scopes) {

  public JiraOAuthGrant {
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
    return "JiraOAuthGrant[provider=" + provider + ", expiresAt=" + accessTokenExpiresAt + ", scopes=" + scopes + "]";
  }
}
