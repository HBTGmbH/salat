package de.hbt.salat.secret.service;

import static de.hbt.salat.common.exception.ErrorCode.SC_NO_KEY;
import static de.hbt.salat.common.exception.ErrorCode.SC_SECRET_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.SC_SECRET_UNREADABLE;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;

import java.util.Base64;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.secret.domain.OAuthTokens;
import de.hbt.salat.secret.domain.Secret;
import de.hbt.salat.secret.domain.SecretStatus;
import de.hbt.salat.secret.domain.SecretSummary;
import de.hbt.salat.secret.domain.SecretValue;
import de.hbt.salat.secret.domain.UsernamePassword;
import de.hbt.salat.secret.domain.VersionedSecret;
import de.hbt.salat.secret.persistence.SecretRepository;
import de.hbt.salat.secret.service.SecretCipher.UnreadableSecretException;

/**
 * The one way to store and read a secret (#1432, → ADR-0038). Nothing else writes a secret to the
 * database, and nothing stores one in plain text.
 *
 * <p>Secrets are addressed by their id. An owner remembers it and is responsible for deleting the
 * secret with itself — this module does not know who refers to a secret.
 *
 * <p>Management only: so far every secret belongs to a JIRA replication, which only the management
 * maintains, and the hourly run reaches this as the job user ({@code AuthorizedUser#initForJob}).
 * Creating, replacing and deleting are logged with the sign and the id, never with the content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
@Authorized(requiresManager = true)
public class SecretService {

  /**
   * Keeps transient values apart from stored ones: the associated data of a stored secret is
   * {@code secret:<id>:<type>}, that of a transient one starts with this.
   */
  private static final String TRANSIENT_PREFIX = "transient:";

  private final SecretRepository repository;
  private final SecretCipher cipher;
  private final AuthorizedUser authorizedUser;

  /**
   * Whether secrets can be stored — the environment provides an active key. Without one a form does
   * not offer the input, and anything that needs a secret fails with a message that says why.
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public boolean isAvailable() {
    return cipher.canEncrypt();
  }

  /**
   * @return the id the owner remembers
   * @throws BusinessRuleException {@code SC-0001} without a key
   */
  public long create(SecretValue value) {
    requireKey();
    var secret = new Secret();
    secret.setType(value.type());
    secret.setStatus(SecretStatus.VALID);
    secret.setKeyId(cipher.activeKeyId());
    // The associated data names the id, which exists after the insert. The empty payload never
    // reaches a commit: it is replaced in the same transaction.
    secret.setPayload(new byte[0]);
    secret = repository.save(secret);
    seal(secret, value);
    log.info("Secret {} ({}) created by {}", secret.getId(), secret.getType(), authorizedUser.getLoginSign());
    return secret.getId();
  }

  /**
   * Replaces the content, of another type if need be, and makes the secret valid again.
   *
   * @throws BusinessRuleException {@code SC-0001} without a key
   */
  public void replace(long id, SecretValue value) {
    requireKey();
    var secret = load(id);
    secret.setType(value.type());
    secret.setStatus(SecretStatus.VALID);
    seal(secret, value);
    log.info("Secret {} ({}) replaced by {}", id, secret.getType(), authorizedUser.getLoginSign());
  }

  /**
   * The content, decrypted.
   *
   * @throws BusinessRuleException {@code SC-0001} without any key, {@code SC-0002} when the secret
   *     cannot be decrypted with the keys there are — it has to be entered again
   */
  @Transactional(readOnly = true)
  public SecretValue read(long id) {
    var secret = load(id);
    try {
      return open(secret);
    } catch (UnreadableSecretException ex) {
      log.warn("Secret {} cannot be read: {}", id, ex.getMessage());
      throw new BusinessRuleException(cipher.canEncrypt() ? SC_SECRET_UNREADABLE : SC_NO_KEY);
    }
  }

  /**
   * The content with the version it was read at, for {@link #replaceIfUnchanged} (#1417).
   *
   * @throws BusinessRuleException as {@link #read}
   */
  @Transactional(readOnly = true)
  public VersionedSecret readVersioned(long id) {
    var secret = load(id);
    try {
      return new VersionedSecret(open(secret), secret.getStatus(), secret.getUpdatecounter());
    } catch (UnreadableSecretException ex) {
      log.warn("Secret {} cannot be read: {}", id, ex.getMessage());
      throw new BusinessRuleException(cipher.canEncrypt() ? SC_SECRET_UNREADABLE : SC_NO_KEY);
    }
  }

  /**
   * Replaces the content and sets the status, but only if the secret is still at the version it was
   * read at (#1417): the renewal of OAuth tokens asks the provider between reading and writing, and
   * a secret replaced or deleted in the meantime must not be overwritten with what it returned.
   *
   * @throws BusinessRuleException {@code XX-0003} when the secret has changed since, {@code SC-0001}
   *     without a key
   */
  public void replaceIfUnchanged(long id, int expectedVersion, SecretValue value, SecretStatus status) {
    requireKey();
    var secret = load(id);
    if (!Objects.equals(secret.getUpdatecounter(), expectedVersion)) {
      throw new BusinessRuleException(XX_CONCURRENT_MODIFICATION);
    }
    secret.setType(value.type());
    secret.setStatus(status);
    seal(secret, value);
    log.info("Secret {} ({}) replaced by {}, status {}", id, secret.getType(), authorizedUser.getLoginSign(), status);
  }

  /**
   * Encrypts a value that is not stored here but handed out for a short while — the state of an OAuth
   * connection attempt in a cookie (#1417, ADR-0038 §8). With the key of the environment, under
   * associated data of its own, so neither a stored secret nor another kind of value decrypts as it.
   *
   * @param context the associated data, e.g. {@code oauth-state}
   * @return the key id and the Base64url-encoded cipher text, separated by a dot
   * @throws BusinessRuleException {@code SC-0001} without a key
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public String sealTransient(byte[] plaintext, String context) {
    requireKey();
    var sealed = cipher.encrypt(plaintext, TRANSIENT_PREFIX + context);
    return sealed.keyId() + '.' + Base64.getUrlEncoder().withoutPadding().encodeToString(sealed.payload());
  }

  /**
   * @return the value {@link #sealTransient} encrypted under the same context, or {@code null} when it
   *     was changed, sealed under another context, or with a key that is not configured
   */
  @Transactional(propagation = Propagation.SUPPORTS)
  public byte[] openTransient(String sealed, String context) {
    if (sealed == null) {
      return null;
    }
    var separator = sealed.indexOf('.');
    if (separator <= 0) {
      return null;
    }
    try {
      var payload = Base64.getUrlDecoder().decode(sealed.substring(separator + 1));
      return cipher.decrypt(sealed.substring(0, separator), payload, TRANSIENT_PREFIX + context);
    } catch (IllegalArgumentException | UnreadableSecretException ex) {
      return null;
    }
  }

  /** Everything about the secret but the secret itself, for a form. */
  @Transactional(readOnly = true)
  public SecretSummary getSummary(long id) {
    var secret = load(id);
    SecretValue value;
    try {
      value = open(secret);
    } catch (UnreadableSecretException ex) {
      value = null;
    }
    var username = value instanceof UsernamePassword usernamePassword ? usernamePassword.username() : null;
    var connection = value instanceof OAuthTokens tokens ? tokens.connection() : null;
    return new SecretSummary(id, secret.getType(), secret.getStatus(), value != null, username, connection);
  }

  public void delete(long id) {
    var secret = load(id);
    repository.delete(secret);
    log.info("Secret {} ({}) deleted by {}", id, secret.getType(), authorizedUser.getLoginSign());
  }

  private void seal(Secret secret, SecretValue value) {
    var sealed = cipher.encrypt(SecretCodec.encode(value), secret.associatedData());
    secret.setKeyId(sealed.keyId());
    secret.setPayload(sealed.payload());
  }

  private SecretValue open(Secret secret) {
    var plaintext = cipher.decrypt(secret.getKeyId(), secret.getPayload(), secret.associatedData());
    return SecretCodec.decode(secret.getType(), plaintext);
  }

  private void requireKey() {
    if (!cipher.canEncrypt()) {
      throw new BusinessRuleException(SC_NO_KEY);
    }
  }

  private Secret load(long id) {
    return repository.findById(id).orElseThrow(() -> new InvalidDataException(SC_SECRET_NOT_FOUND));
  }
}
