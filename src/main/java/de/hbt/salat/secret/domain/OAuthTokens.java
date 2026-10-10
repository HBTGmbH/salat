package de.hbt.salat.secret.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * An OAuth connection, one secret (#1417, → ADR-0038 §1): access and refresh token are renewed
 * together and written together, so they are not two secrets but one.
 *
 * <p>After {@code invalid_grant} the tokens are gone and only the {@link #connection()} remains,
 * so the form can still say which account has to be connected again (ADR-0038 §6).
 *
 * @param accessToken {@code null} once the provider refused to renew it
 * @param accessTokenExpiresAt when the access token expires; {@code null} without one
 * @param refreshToken {@code null} once the provider refused it
 */
public record OAuthTokens(
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken,
    OAuthConnection connection
) implements SecretValue {

  /**
   * How long an access token has to remain valid to be handed out (ADR-0038 §5) — long enough for
   * a call that started with it to finish.
   */
  public static final Duration MINIMUM_VALIDITY = Duration.ofMinutes(5);

  @Override
  public SecretType type() {
    return SecretType.OAUTH;
  }

  public boolean hasTokens() {
    return accessToken != null && refreshToken != null;
  }

  /** Whether the access token can still be used at {@code now}, with {@link #MINIMUM_VALIDITY} to spare. */
  public boolean isUsableAt(Instant now) {
    return accessToken != null && accessTokenExpiresAt != null
        && accessTokenExpiresAt.isAfter(now.plus(MINIMUM_VALIDITY));
  }

  /**
   * The tokens a renewal returned. A provider that rotates refresh tokens hands out a new one with
   * every renewal; one that does not leaves the old one valid.
   *
   * @param refreshToken {@code null} when the provider returned none, then the old one stays
   * @param scopes {@code null} or empty when the provider did not repeat them, then they stay
   */
  public OAuthTokens renewed(String accessToken, Instant expiresAt, String refreshToken, Set<String> scopes) {
    var granted = scopes == null || scopes.isEmpty() ? connection : connection.withScopes(scopes);
    return new OAuthTokens(accessToken, expiresAt, refreshToken != null ? refreshToken : this.refreshToken, granted);
  }

  /** The connection without its tokens, as it stays after the provider refused to renew them. */
  public OAuthTokens withoutTokens() {
    return new OAuthTokens(null, null, null, connection);
  }

  @Override
  public String toString() {
    return "OAuthTokens[expiresAt=" + accessTokenExpiresAt + ", connection=" + connection + "]";
  }
}
