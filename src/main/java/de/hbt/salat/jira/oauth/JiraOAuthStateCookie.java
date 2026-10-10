package de.hbt.salat.jira.oauth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import de.hbt.salat.secret.service.SecretService;

/**
 * Carries {@code state} and the PKCE verifier of one connection attempt from the start to the callback
 * (#1417, → ADR-0038 §8) — in the browser, not in the {@code HttpSession} (ADR-0013).
 *
 * <p>Encrypted by the module {@code secret} with the key of the environment under its own associated
 * data, so neither a stored secret nor anything else decrypts as a cookie. {@code __Host-}: only over
 * HTTPS, only for this host, for the whole path. {@code SameSite=Lax}, because the callback is a
 * navigation from the site of Atlassian; {@code Strict} would leave the cookie behind.
 *
 * <p>Two attempts in two tabs share the one cookie, and the first one fails with a message that says
 * so. A table with a cleanup job would be the price of avoiding that.
 */
@Component
class JiraOAuthStateCookie {

  static final String NAME = "__Host-salat-oauth";

  /** Long enough to sign in at Atlassian, short enough that a forgotten attempt does not linger. */
  static final Duration MAX_AGE = Duration.ofMinutes(10);

  private static final String CONTEXT = "oauth-state";

  private final SecretService secretService;

  JiraOAuthStateCookie(SecretService secretService) {
    this.secretService = secretService;
  }

  ResponseCookie write(Pending pending) {
    var value = secretService.sealTransient(encode(pending), CONTEXT);
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
    var bytes = secretService.openTransient(value, CONTEXT);
    if (bytes == null) {
      return null;
    }
    try {
      return decode(bytes);
    } catch (RuntimeException ex) {
      // Decrypted, but not the fields of a cookie: not ours either.
      return null;
    }
  }

  private static byte[] encode(Pending pending) {
    var bytes = new ByteArrayOutputStream();
    try (var out = new DataOutputStream(bytes)) {
      out.writeUTF(pending.owner());
      out.writeUTF(pending.state());
      out.writeUTF(pending.codeVerifier());
      out.writeUTF(pending.loginSign());
      out.writeLong(pending.expiresAt().toEpochMilli());
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return bytes.toByteArray();
  }

  private static Pending decode(byte[] bytes) {
    try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      return new Pending(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), Instant.ofEpochMilli(in.readLong()));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
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
   * @param owner what the connection will belong to, {@code jira-replication:<id>}
   * @param loginSign the person who started the attempt; only they can finish it
   */
  record Pending(String owner, String state, String codeVerifier, String loginSign, Instant expiresAt) {

    @Override
    public String toString() {
      return "Pending[owner=" + owner + ", loginSign=" + loginSign + "]";
    }
  }
}
