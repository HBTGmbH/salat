package de.hbt.salat.secret.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import de.hbt.salat.secret.persistence.SecretRepository;
import de.hbt.salat.secret.service.SecretCipher.UnreadableSecretException;

/**
 * Encrypts the secrets of a key that is no longer active with the active one, at every start (#1432,
 * → ADR-0038 §3). That is the change of keys: add the new key, make it active, restart; the old one
 * can go once this has run.
 *
 * <p>Row by row, each in its own transaction, so one row that fails does not hold up the others. A
 * row whose key is not configured stays as it is: it is unreadable, and only entering the secret
 * again helps. The bytes are encrypted anew without being decoded, under the same associated data.
 *
 * <p>Not through {@link SecretService}: this is the module looking after its own rows at start,
 * without a person and without a request — and the audit columns name the system.
 */
@Slf4j
@Service
class SecretKeyRotation {

  private final SecretRepository repository;
  private final SecretCipher cipher;
  private final TransactionTemplate transaction;

  SecretKeyRotation(SecretRepository repository, SecretCipher cipher, PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.cipher = cipher;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void encryptWithActiveKey() {
    if (!cipher.canEncrypt()) {
      return;
    }
    var ids = repository.findIdsNotEncryptedWith(cipher.activeKeyId());
    if (ids.isEmpty()) {
      return;
    }
    int encrypted = 0;
    for (var id : ids) {
      if (Boolean.TRUE.equals(transaction.execute(status -> encryptWithActiveKey(id)))) {
        encrypted++;
      }
    }
    log.info("Secret store: {} of {} secrets encrypted with the active key {}", encrypted, ids.size(),
        cipher.activeKeyId());
  }

  private boolean encryptWithActiveKey(long id) {
    var secret = repository.findById(id).orElse(null);
    if (secret == null || cipher.activeKeyId().equals(secret.getKeyId())) {
      return false;
    }
    try {
      var plaintext = cipher.decrypt(secret.getKeyId(), secret.getPayload(), secret.associatedData());
      var sealed = cipher.encrypt(plaintext, secret.associatedData());
      secret.setKeyId(sealed.keyId());
      secret.setPayload(sealed.payload());
      return true;
    } catch (UnreadableSecretException ex) {
      log.warn("Secret {} stays with key {} - it cannot be read: {}", id, secret.getKeyId(), ex.getMessage());
      return false;
    }
  }
}
