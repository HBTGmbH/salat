package de.hbt.salat.secret.service;

import static de.hbt.salat.common.exception.ErrorCode.SE_NO_KEY;
import static de.hbt.salat.common.exception.ErrorCode.SE_SECRET_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.SE_SECRET_UNREADABLE;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.secret.domain.Secret;
import de.hbt.salat.secret.domain.SecretStatus;
import de.hbt.salat.secret.domain.SecretSummary;
import de.hbt.salat.secret.domain.SecretValue;
import de.hbt.salat.secret.domain.UsernamePassword;
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
   * @throws BusinessRuleException {@code SE-0001} without a key
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
   * @throws BusinessRuleException {@code SE-0001} without a key
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
   * @throws BusinessRuleException {@code SE-0001} without any key, {@code SE-0002} when the secret
   *     cannot be decrypted with the keys there are — it has to be entered again
   */
  @Transactional(readOnly = true)
  public SecretValue read(long id) {
    var secret = load(id);
    try {
      return open(secret);
    } catch (UnreadableSecretException ex) {
      log.warn("Secret {} cannot be read: {}", id, ex.getMessage());
      throw new BusinessRuleException(cipher.canEncrypt() ? SE_SECRET_UNREADABLE : SE_NO_KEY);
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
    return new SecretSummary(id, secret.getType(), secret.getStatus(), value != null, username);
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
      throw new BusinessRuleException(SE_NO_KEY);
    }
  }

  private Secret load(long id) {
    return repository.findById(id).orElseThrow(() -> new InvalidDataException(SE_SECRET_NOT_FOUND));
  }
}
