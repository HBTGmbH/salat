package de.hbt.salat.jira.oauth;

import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_DENIED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_NOT_CONFIGURED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_REAUTH_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_STATE_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_TOKEN_REQUEST_FAILED;
import static de.hbt.salat.common.exception.ErrorCode.SC_SECRET_UNREADABLE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.SalatProperties.Jira.OAuth;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.jira.oauth.JiraOAuthStateCookie.Pending;
import de.hbt.salat.jira.oauth.JiraOAuthTokenClient.TokenRequestException;
import de.hbt.salat.secret.domain.SecretStatus;
import de.hbt.salat.secret.domain.Token;
import de.hbt.salat.secret.domain.VersionedSecret;
import de.hbt.salat.secret.service.SecretService;

/**
 * OAuth 2.0 (3LO) against Atlassian (#1417, → ADR-0038 §§5–8): the authorization code flow with PKCE
 * between the start and the callback of a replication, and a fresh access token for every run from
 * the refresh token it stored.
 *
 * <p>The routes belong to the replication, which knows who may connect; this service knows the
 * attempt only by the owner it was given ({@code jira-replication:<id>}). The refresh token is a
 * {@link Token} in the module {@code secret}, written and read through {@link SecretService} alone:
 * storing it first is the replication's, replacing it with the rotated one is this service's.
 *
 * <p>Management only, like the replications: the hourly run reaches this as the job user.
 */
@Slf4j
@Service
@Authorized(requiresManager = true)
public class JiraOAuthService {

  /** The cookie that carries a connection attempt to the callback. */
  public static final String STATE_COOKIE = JiraOAuthStateCookie.NAME;

  private static final int RANDOM_BYTES = 32;
  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private final SalatProperties properties;
  private final SecretService secretService;
  private final JiraOAuthStateCookie stateCookie;
  private final JiraOAuthTokenClient tokenClient;
  private final AuthorizedUser authorizedUser;
  private final SecureRandom random = new SecureRandom();

  /**
   * One lock per secret (ADR-0038 §5). Like the lock of ADR-0028 it assumes one instance of the
   * application; the entries are as many as there are connections and stay.
   */
  private final Map<Long, ReentrantLock> renewalLocks = new ConcurrentHashMap<>();

  JiraOAuthService(SalatProperties properties, SecretService secretService, JiraOAuthStateCookie stateCookie,
                   JiraOAuthTokenClient tokenClient, AuthorizedUser authorizedUser) {
    this.properties = properties;
    this.secretService = secretService;
    this.stateCookie = stateCookie;
    this.tokenClient = tokenClient;
    this.authorizedUser = authorizedUser;
  }

  /**
   * Whether a replication can be connected: the app is registered at Atlassian and the environment
   * provides a key to store the tokens with. Without either the form does not offer to connect.
   */
  public boolean isAvailable() {
    return secretService.isAvailable() && registration() != null;
  }

  /**
   * Starts an attempt: where to send the browser, and the cookie that has to go with it.
   *
   * @param owner what the connection will belong to, checked again in the callback
   * @param scopes what to ask for — no more than is needed
   * @param parameters what Atlassian wants in addition: {@code audience} and {@code prompt}
   * @throws BusinessRuleException {@code JI-0050} without a registration or a key
   */
  public JiraOAuthAuthorization authorize(String owner, Collection<String> scopes, Map<String, String> parameters) {
    var registration = requireRegistration();
    var state = randomToken();
    var codeVerifier = randomToken();
    var url = UriComponentsBuilder.fromUriString(registration.getAuthorizationUri())
        .queryParam("client_id", registration.getClientId())
        .queryParam("response_type", "code")
        .queryParam("redirect_uri", registration.getRedirectUri())
        .queryParam("scope", String.join(" ", scopes))
        .queryParam("state", state)
        .queryParam("code_challenge", codeChallenge(codeVerifier))
        .queryParam("code_challenge_method", "S256");
    parameters.forEach(url::queryParam);
    var expiresAt = now().plus(JiraOAuthStateCookie.MAX_AGE);
    var cookie = stateCookie.write(new Pending(owner, state, codeVerifier, authorizedUser.getLoginSign(), expiresAt));
    return new JiraOAuthAuthorization(url.encode().build().toUriString(), cookie);
  }

  /**
   * Who the callback is for — read from the cookie, once it has been checked.
   *
   * @throws BusinessRuleException {@code JI-0051} when the cookie is missing, expired or changed, or
   *     belongs to another person or another attempt
   */
  public String ownerOf(String cookieValue, String state) {
    return verify(cookieValue, state).owner();
  }

  /**
   * Checks the callback and exchanges its code for the first tokens. Outside a transaction: the
   * response time of Atlassian must not hold a database connection.
   *
   * @param error what Atlassian sent instead of a code, {@code access_denied} when the person declined
   * @throws BusinessRuleException {@code JI-0051} as with {@link #ownerOf}, {@code JI-0052} when the
   *     person declined or there is no code, {@code JI-0053} when Atlassian refused the code
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public JiraOAuthGrant complete(String cookieValue, String state, String code, String error) {
    var pending = verify(cookieValue, state);
    if (error != null || code == null || code.isBlank()) {
      log.info("OAuth connection for {} not granted by {}: {}", pending.owner(), pending.loginSign(), error);
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_DENIED);
    }
    var registration = requireRegistration();
    try {
      var tokens = tokenClient.exchange(registration, code, pending.codeVerifier());
      return new JiraOAuthGrant(tokens.accessToken(), tokens.refreshToken(), tokens.scopes());
    } catch (TokenRequestException ex) {
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_TOKEN_REQUEST_FAILED, ex.error());
    }
  }

  /** The cookie of the attempt removed — the callback sends this whatever came of it. */
  public ResponseCookie clearedCookie() {
    return stateCookie.clear();
  }

  /**
   * A fresh access token for one run (ADR-0038 §5). Only the refresh token is stored; every call
   * renews with it, and the access token lives in memory as long as the caller uses it — an hour at
   * most.
   *
   * <p>Atlassian rotates the refresh token with every renewal, and the new one is stored right away,
   * not at the end of the run: a run that aborts would otherwise take the only valid refresh token
   * with it. One lock per secret keeps two callers — the hourly run and a field catalogue opened at
   * the same time — from renewing with the same refresh token; the second one reads the one the first
   * has just stored. The version is the safety net behind the lock.
   *
   * <p>Outside a transaction: no database connection is held while Atlassian is asked. Reading and
   * writing are short transactions of {@link SecretService}.
   *
   * @throws BusinessRuleException {@code JI-0049} when the connection has to be established again —
   *     Atlassian refused to renew it, or the refresh token cannot be read with the keys there are;
   *     {@code JI-0053} when Atlassian could not be asked; {@code SC-0001} without any key
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public String accessToken(long secretId) {
    var registration = requireRegistration();
    var lock = renewalLocks.computeIfAbsent(secretId, id -> new ReentrantLock());
    lock.lock();
    try {
      var stored = readRefreshToken(secretId);
      var refreshToken = ((Token) stored.value()).token();
      JiraOAuthTokenClient.Tokens response;
      try {
        response = tokenClient.refresh(registration, refreshToken);
      } catch (TokenRequestException ex) {
        if (ex.isInvalidGrant()) {
          requireReauthentication(secretId, stored);
          throw new BusinessRuleException(JI_REPLICATION_OAUTH_REAUTH_REQUIRED);
        }
        throw new BusinessRuleException(JI_REPLICATION_OAUTH_TOKEN_REQUEST_FAILED, ex.error());
      }
      // A provider that does not rotate returns none; then the old one stays valid.
      if (response.refreshToken() != null && !response.refreshToken().equals(refreshToken)) {
        secretService.replaceIfUnchanged(secretId, stored.version(), new Token(response.refreshToken()),
            SecretStatus.VALID);
      }
      return response.accessToken();
    } finally {
      lock.unlock();
    }
  }

  /**
   * {@code invalid_grant}: the refresh token is used up, expired or revoked (ADR-0038 §6). It goes;
   * the connection with the replication stays, so the form can say which account to connect again.
   */
  private void requireReauthentication(long secretId, VersionedSecret stored) {
    secretService.replaceIfUnchanged(secretId, stored.version(), new Token(null), SecretStatus.REAUTH_REQUIRED);
    log.warn("Secret {}: Atlassian refused the refresh token (invalid_grant) - the connection has to be "
        + "established again", secretId);
  }

  /**
   * The stored refresh token. One that cannot be read is not written back as {@code REAUTH_REQUIRED}:
   * a key missing by mistake would otherwise end every connection for good, although the payload is
   * intact.
   */
  private VersionedSecret readRefreshToken(long secretId) {
    VersionedSecret stored;
    try {
      stored = secretService.readVersioned(secretId);
    } catch (ErrorCodeException ex) {
      if (ex.getMessages().stream().anyMatch(message -> message.getErrorCode() == SC_SECRET_UNREADABLE)) {
        throw new BusinessRuleException(JI_REPLICATION_OAUTH_REAUTH_REQUIRED);
      }
      throw ex;
    }
    if (!(stored.value() instanceof Token token) || token.token() == null
        || stored.status() == SecretStatus.REAUTH_REQUIRED) {
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_REAUTH_REQUIRED);
    }
    return stored;
  }

  private Pending verify(String cookieValue, String state) {
    var pending = stateCookie.read(cookieValue);
    if (pending == null
        || !pending.expiresAt().isAfter(now())
        || state == null
        || !MessageDigest.isEqual(state.getBytes(StandardCharsets.US_ASCII),
            pending.state().getBytes(StandardCharsets.US_ASCII))
        || !Objects.equals(pending.loginSign(), authorizedUser.getLoginSign())) {
      log.info("OAuth callback of {} rejected: no matching connection attempt", authorizedUser.getLoginSign());
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_STATE_INVALID);
    }
    return pending;
  }

  private OAuth requireRegistration() {
    var registration = registration();
    if (registration == null || !secretService.isAvailable()) {
      throw new BusinessRuleException(JI_REPLICATION_OAUTH_NOT_CONFIGURED);
    }
    return registration;
  }

  /** The registration, if it is complete. */
  private OAuth registration() {
    var registration = properties.getJira().getOauth();
    if (registration == null || isBlank(registration.getClientId()) || isBlank(registration.getClientSecret())
        || isBlank(registration.getRedirectUri()) || isBlank(registration.getAuthorizationUri())
        || isBlank(registration.getTokenUri())) {
      return null;
    }
    return registration;
  }

  private String randomToken() {
    var bytes = new byte[RANDOM_BYTES];
    random.nextBytes(bytes);
    return BASE64_URL.encodeToString(bytes);
  }

  /** PKCE with {@code S256} (RFC 7636): the hash of the verifier goes out now, the verifier with the code. */
  static String codeChallenge(String codeVerifier) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
      return BASE64_URL.encodeToString(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static Instant now() {
    return Instant.now(ClockProvider.getClock());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
