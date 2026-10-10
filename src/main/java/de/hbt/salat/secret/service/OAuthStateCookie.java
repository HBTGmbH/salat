package de.hbt.salat.secret.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Carries {@code state} and the PKCE verifier of one connection attempt from the start to the callback
 * (#1417, → ADR-0038 §8) — in the browser, not in the {@code HttpSession} (ADR-0013).
 *
 * <p>Encrypted with the key of the secret store under its own associated data, so neither a secret
 * row nor anything else decrypts as a cookie. {@code __Host-}: only over HTTPS, only for this host,
 * for the whole path. {@code SameSite=Lax}, because the callback is a navigation from the site of the
 * provider; {@code Strict} would leave the cookie behind.
 *
 * <p>Two attempts in two tabs share the one cookie, and the first one fails with a message that says
 * so. A table with a cleanup job would be the price of avoiding that.
 */
@Component
class OAuthStateCookie {

  static final String NAME = "__Host-salat-oauth";

  /** Long enough to sign in at the provider, short enough that a forgotten attempt does not linger. */
  static final Duration MAX_AGE = Duration.ofMinutes(10);

  private static final String ASSOCIATED_DATA = "oauth-state";
  private static final char SEPARATOR = '.';

  private final SecretCipher cipher;

  OAuthStateCookie(SecretCipher cipher) {
    this.cipher = cipher;
  }

  ResponseCookie write(Pending pending) {
    var sealed = cipher.encrypt(SecretCodec.write(
        pending.provider(),
        pending.owner(),
        pending.state(),
        pending.codeVerifier(),
        pending.loginSign(),
        pending.expiresAt().toString()), ASSOCIATED_DATA);
    var value = sealed.keyId() + SEPARATOR + Base64.getUrlEncoder().withoutPadding().encodeToString(sealed.payload());
    return cookie(value, MAX_AGE);
  }

  /** Removes the cookie: the callback reads it once, whatever comes of it. */
  ResponseCookie clear() {
    return cookie("", Duration.ZERO);
  }

  /**
   * @return {@code null} when the value is missing, was changed, or was not written by this
   *     application with a key it still has
   */
  Pending read(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    var separator = value.indexOf(SEPARATOR);
    if (separator <= 0) {
      return null;
    }
    try {
      var payload = Base64.getUrlDecoder().decode(value.substring(separator + 1));
      var fields = SecretCodec.read(cipher.decrypt(value.substring(0, separator), payload, ASSOCIATED_DATA));
      return new Pending(
          SecretCodec.field(fields, 0),
          SecretCodec.field(fields, 1),
          SecretCodec.field(fields, 2),
          SecretCodec.field(fields, 3),
          SecretCodec.field(fields, 4),
          Instant.parse(SecretCodec.field(fields, 5)));
    } catch (RuntimeException ex) {
      // Not decryptable, not Base64, or not the fields of a cookie: in every case not ours.
      return null;
    }
  }

  private static ResponseCookie cookie(String value, Duration maxAge) {
    return ResponseCookie.from(NAME, value)
        .path("/")
        .secure(true)
        .httpOnly(true)
        .sameSite("Lax")
        .maxAge(maxAge)
        .build();
  }

  /**
   * One connection attempt.
   *
   * @param provider the registration the attempt goes to
   * @param owner what the secret will belong to, {@code jira-replication:<id>} — named by the owner
   * @param loginSign the person who started the attempt; only they can finish it
   */
  record Pending(String provider, String owner, String state, String codeVerifier, String loginSign,
                 Instant expiresAt) {

    @Override
    public String toString() {
      return "Pending[provider=" + provider + ", owner=" + owner + ", loginSign=" + loginSign + "]";
    }
  }
}
