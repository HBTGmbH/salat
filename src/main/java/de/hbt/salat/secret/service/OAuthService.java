package de.hbt.salat.secret.service;

import static de.hbt.salat.common.exception.ErrorCode.SC_NO_KEY;
import static de.hbt.salat.common.exception.ErrorCode.SC_OAUTH_DENIED;
import static de.hbt.salat.common.exception.ErrorCode.SC_OAUTH_NOT_CONFIGURED;
import static de.hbt.salat.common.exception.ErrorCode.SC_OAUTH_REAUTH_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.SC_OAUTH_STATE_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.SC_OAUTH_TOKEN_REQUEST_FAILED;
import static de.hbt.salat.common.exception.ErrorCode.SC_SECRET_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.SalatProperties.OAuth.Client;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.secret.domain.OAuthAuthorization;
import de.hbt.salat.secret.domain.OAuthGrant;
import de.hbt.salat.secret.domain.OAuthTokens;
import de.hbt.salat.secret.domain.SecretStatus;
import de.hbt.salat.secret.domain.SecretType;
import de.hbt.salat.secret.persistence.SecretRepository;
import de.hbt.salat.secret.service.OAuthStateCookie.Pending;
import de.hbt.salat.secret.service.OAuthTokenClient.TokenRequestException;
import de.hbt.salat.secret.service.SecretCipher.UnreadableSecretException;

/**
 * Outgoing OAuth (#1417, → ADR-0038 §§5–8): the authorization code flow with PKCE between the start
 * and the callback of an owner, and the renewal of the tokens it stored.
 *
 * <p>The routes belong to the owner, who knows who may connect; this service knows nothing of the
 * owner but the name it gave the attempt ({@code jira-replication:<id>}). Storing the tokens is the
 * owner's as well — through {@link SecretService}, once it has found out who and what they are for.
 *
 * <p>Management only, like {@link SecretService}: so far every connection belongs to a JIRA
 * replication, and the hourly run reaches this as the job user.
 */
@Slf4j
@Service
@Authorized(requiresManager = true)
public class OAuthService {

  /** The cookie that carries a connection attempt to the callback; the owner's callback reads it. */
  public static final String STATE_COOKIE = OAuthStateCookie.NAME;

  private static final int RANDOM_BYTES = 32;
  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private final SalatProperties properties;
  private final SecretRepository repository;
  private final SecretCipher cipher;
  private final OAuthStateCookie stateCookie;
  private final OAuthTokenClient tokenClient;
  private final AuthorizedUser authorizedUser;
  private final TransactionTemplate transaction;
  private final SecureRandom random = new SecureRandom();

  /**
   * One lock per secret (ADR-0038 §5). Like the lock of ADR-0028 it assumes one instance of the
   * application; the entries are as many as there are connections and stay.
   */
  private final Map<Long, ReentrantLock> renewalLocks = new ConcurrentHashMap<>();

  OAuthService(SalatProperties properties, SecretRepository repository, SecretCipher cipher,
               OAuthStateCookie stateCookie, OAuthTokenClient tokenClient, AuthorizedUser authorizedUser,
               PlatformTransactionManager transactionManager) {
    this.properties = properties;
    this.repository = repository;
    this.cipher = cipher;
    this.stateCookie = stateCookie;
    this.tokenClient = tokenClient;
    this.authorizedUser = authorizedUser;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  /**
   * Whether a connection to this provider can be made: the app is registered there and the
   * environment provides a key to store the tokens with. Without either a form does not offer to
   * connect.
   */
  public boolean isAvailable(String provider) {
    return cipher.canEncrypt() && client(provider) != null;
  }

  /**
   * Starts an attempt: where to send the browser, and the cookie that has to go with it.
   *
   * @param owner what the connection will belong to, checked again in the callback
   * @param scopes what to ask for — no more than is needed
   * @param parameters what the provider wants in addition, e.g. {@code audience} and {@code prompt}
   * @throws BusinessRuleException {@code SC-0005} without a registration, {@code SC-0001} without a key
   */
  public OAuthAuthorization authorize(String provider, String owner, Collection<String> scopes,
                                      Map<String, String> parameters) {
    var client = requireClient(provider);
    var state = randomToken();
    var codeVerifier = randomToken();
    var url = UriComponentsBuilder.fromUriString(client.getAuthorizationUri())
        .queryParam("client_id", client.getClientId())
        .queryParam("response_type", "code")
        .queryParam("redirect_uri", client.getRedirectUri())
        .queryParam("scope", String.join(" ", scopes))
        .queryParam("state", state)
        .queryParam("code_challenge", codeChallenge(codeVerifier))
        .queryParam("code_challenge_method", "S256");
    parameters.forEach(url::queryParam);
    var expiresAt = now().plus(OAuthStateCookie.MAX_AGE);
    var cookie = stateCookie.write(
        new Pending(provider, owner, state, codeVerifier, authorizedUser.getLoginSign(), expiresAt));
    return new OAuthAuthorization(url.encode().build().toUriString(), cookie);
  }

  /**
   * Who the callback is for — read from the cookie, once it has been checked.
   *
   * @throws BusinessRuleException {@code SC-0006} when the cookie is missing, expired, changed, or
   *     belongs to another provider, another person or another attempt
   */
  public String ownerOf(String provider, String cookieValue, String state) {
    return verify(provider, cookieValue, state).owner();
  }

  /**
   * Checks the callback and exchanges its code for the first tokens. Outside a transaction: the
   * provider's response time must not hold a database connection.
   *
   * @param error what the provider sent instead of a code, {@code access_denied} when the person
   *     declined
   * @throws BusinessRuleException {@code SC-0006} as with {@link #ownerOf}, {@code SC-0007} when the
   *     person declined or there is no code, {@code SC-0008} when the provider refused the code
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public OAuthGrant complete(String provider, String cookieValue, String state, String code, String error) {
    var pending = verify(provider, cookieValue, state);
    if (error != null || code == null || code.isBlank()) {
      log.info("OAuth connection to {} for {} not granted by {}: {}", provider, pending.owner(),
          pending.loginSign(), error);
      throw new BusinessRuleException(SC_OAUTH_DENIED);
    }
    var client = requireClient(provider);
    try {
      var tokens = tokenClient.exchange(client, code, pending.codeVerifier());
      return new OAuthGrant(provider, tokens.accessToken(), tokens.expiresAt(), tokens.refreshToken(), tokens.scopes());
    } catch (TokenRequestException ex) {
      throw new BusinessRuleException(SC_OAUTH_TOKEN_REQUEST_FAILED, ex.error());
    }
  }

  /** The cookie of the attempt removed — the callback sends this whatever came of it. */
  public ResponseCookie clearedCookie() {
    return stateCookie.clear();
  }

  /**
   * The tokens of a connection with an access token that is valid for at least
   * {@link OAuthTokens#MINIMUM_VALIDITY}, renewed if need be (ADR-0038 §5): a second caller waits for
   * the first one's renewal and takes its tokens, so a rotating refresh token is used only once.
   *
   * <p>Outside a transaction: no database connection is held while the provider is asked. The rows
   * are read and written in short transactions of their own, the version as a safety net behind the
   * lock.
   *
   * @throws BusinessRuleException {@code SC-0004} when the connection has to be established again —
   *     the provider refused to renew it, or it cannot be read with the keys there are; {@code SC-0008}
   *     when the provider could not be asked; {@code SC-0001} without any key
   * @throws InvalidDataException {@code SC-0003} when the secret does not exist
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public OAuthTokens currentTokens(long secretId) {
    var current = read(secretId);
    if (current.tokens().isUsableAt(now())) {
      return current.tokens();
    }
    var lock = renewalLocks.computeIfAbsent(secretId, id -> new ReentrantLock());
    lock.lock();
    try {
      var fresh = read(secretId);
      if (fresh.tokens().isUsableAt(now())) {
        // Renewed by the caller this one waited for.
        return fresh.tokens();
      }
      return renew(secretId, fresh);
    } finally {
      lock.unlock();
    }
  }

  private OAuthTokens renew(long secretId, Snapshot snapshot) {
    var connection = snapshot.tokens().connection();
    var client = requireClient(connection.provider());
    OAuthTokenClient.Tokens response;
    try {
      response = tokenClient.refresh(client, snapshot.tokens().refreshToken());
    } catch (TokenRequestException ex) {
      if (ex.isInvalidGrant()) {
        requireReauthentication(secretId, snapshot);
        throw new BusinessRuleException(SC_OAUTH_REAUTH_REQUIRED);
      }
      throw new BusinessRuleException(SC_OAUTH_TOKEN_REQUEST_FAILED, ex.error());
    }
    var renewed = snapshot.tokens().renewed(response.accessToken(), response.expiresAt(), response.refreshToken(),
        response.scopes());
    write(secretId, snapshot.version(), renewed, SecretStatus.VALID);
    log.info("Secret {} (OAUTH): tokens renewed, valid until {}", secretId, renewed.accessTokenExpiresAt());
    return renewed;
  }

  /**
   * {@code invalid_grant}: the refresh token is used up, expired or revoked (ADR-0038 §6). The tokens
   * go, the connection stays, so the form can say which account to connect again.
   */
  private void requireReauthentication(long secretId, Snapshot snapshot) {
    write(secretId, snapshot.version(), snapshot.tokens().withoutTokens(), SecretStatus.REAUTH_REQUIRED);
    log.warn("Secret {} (OAUTH): the provider refused to renew the tokens (invalid_grant) - the connection "
        + "has to be established again", secretId);
  }

  private Snapshot read(long secretId) {
    if (!cipher.canEncrypt()) {
      throw new BusinessRuleException(SC_NO_KEY);
    }
    return transaction.execute(status -> {
      var secret = repository.findById(secretId).orElseThrow(() -> new InvalidDataException(SC_SECRET_NOT_FOUND));
      if (secret.getType() != SecretType.OAUTH || secret.getStatus() == SecretStatus.REAUTH_REQUIRED) {
        throw new BusinessRuleException(SC_OAUTH_REAUTH_REQUIRED);
      }
      try {
        var plaintext = cipher.decrypt(secret.getKeyId(), secret.getPayload(), secret.associatedData());
        var tokens = (OAuthTokens) SecretCodec.decode(SecretType.OAUTH, plaintext);
        if (!tokens.hasTokens()) {
          throw new BusinessRuleException(SC_OAUTH_REAUTH_REQUIRED);
        }
        return new Snapshot(tokens, secret.getUpdatecounter());
      } catch (UnreadableSecretException ex) {
        // Not written back as REAUTH_REQUIRED: a key missing by mistake would otherwise end every
        // connection for good, although the payload is intact.
        log.warn("Secret {} (OAUTH) cannot be read: {}", secretId, ex.getMessage());
        throw new BusinessRuleException(SC_OAUTH_REAUTH_REQUIRED);
      }
    });
  }

  private void write(long secretId, Integer expectedVersion, OAuthTokens tokens, SecretStatus status) {
    transaction.executeWithoutResult(tx -> {
      var secret = repository.findById(secretId).orElseThrow(() -> new InvalidDataException(SC_SECRET_NOT_FOUND));
      if (!Objects.equals(secret.getUpdatecounter(), expectedVersion)) {
        // Replaced or reconnected while the provider was asked; the lock rules out a second renewal.
        throw new BusinessRuleException(XX_CONCURRENT_MODIFICATION);
      }
      var sealed = cipher.encrypt(SecretCodec.encode(tokens), secret.associatedData());
      secret.setKeyId(sealed.keyId());
      secret.setPayload(sealed.payload());
      secret.setStatus(status);
    });
  }

  private Pending verify(String provider, String cookieValue, String state) {
    var pending = stateCookie.read(cookieValue);
    if (pending == null
        || !provider.equals(pending.provider())
        || !pending.expiresAt().isAfter(now())
        || state == null
        || !MessageDigest.isEqual(state.getBytes(StandardCharsets.US_ASCII),
            pending.state().getBytes(StandardCharsets.US_ASCII))
        || !Objects.equals(pending.loginSign(), authorizedUser.getLoginSign())) {
      log.info("OAuth callback of {} to {} rejected: no matching connection attempt", authorizedUser.getLoginSign(),
          provider);
      throw new BusinessRuleException(SC_OAUTH_STATE_INVALID);
    }
    return pending;
  }

  private Client requireClient(String provider) {
    if (!cipher.canEncrypt()) {
      throw new BusinessRuleException(SC_NO_KEY);
    }
    var client = client(provider);
    if (client == null) {
      throw new BusinessRuleException(SC_OAUTH_NOT_CONFIGURED);
    }
    return client;
  }

  /** The registration, if it is complete. */
  private Client client(String provider) {
    var client = properties.getOauth().getClients().get(provider);
    if (client == null || isBlank(client.getClientId()) || isBlank(client.getClientSecret())
        || isBlank(client.getRedirectUri()) || isBlank(client.getAuthorizationUri())
        || isBlank(client.getTokenUri())) {
      return null;
    }
    return client;
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

  private record Snapshot(OAuthTokens tokens, Integer version) {

  }
}
