package de.hbt.salat.secret.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.SalatProperties;

/**
 * AES-256-GCM with the keys of the environment (#1432, → ADR-0038 §3).
 *
 * <p>A random 96-bit IV per value, put in front of the cipher text. The associated data binds a
 * value to its row, so a cipher text copied elsewhere does not decrypt. One symmetric key is enough:
 * the same application encrypts and decrypts, a key pair would protect against nothing.
 *
 * <p>The keys are checked when the application starts. A key that is not Base64, not 256 bits long,
 * or an active key id without a key stops the start, so the mistake shows at deployment rather than
 * at the first replication. Without any key the application starts, and stores no secret.
 *
 * <p>Neither a key nor a value appears in a message of this class.
 */
@Slf4j
@Component
class SecretCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int KEY_BYTES = 32;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  /** Spring turns an underscore in the name of an environment variable into a dot. */
  private static final Pattern KEY_ID = Pattern.compile("[a-z0-9]+");

  private final SecureRandom random = new SecureRandom();
  private final Map<String, SecretKey> keys;
  private final String activeKeyId;

  SecretCipher(SalatProperties properties) {
    var configuration = properties.getSecret();
    keys = readKeys(configuration.getKeys());
    activeKeyId = readActiveKeyId(configuration.getActiveKeyId(), keys);
    if (activeKeyId == null) {
      log.warn("No key configured for the secret store (salat.secret): secrets are neither stored nor read");
    } else {
      log.info("Secret store: active key {}, known keys {}", activeKeyId, keys.keySet());
    }
  }

  private static Map<String, SecretKey> readKeys(Map<String, String> configured) {
    var result = new HashMap<String, SecretKey>();
    if (configured == null) {
      return result;
    }
    configured.forEach((id, value) -> {
      if (!KEY_ID.matcher(id).matches()) {
        throw new IllegalStateException("salat.secret.keys: the key id '" + id
            + "' may consist of lower case letters and digits only");
      }
      byte[] bytes;
      try {
        bytes = Base64.getDecoder().decode(value == null ? "" : value.trim());
      } catch (IllegalArgumentException ex) {
        throw new IllegalStateException("salat.secret.keys." + id + " is not Base64");
      }
      if (bytes.length != KEY_BYTES) {
        throw new IllegalStateException("salat.secret.keys." + id + " is not a 256-bit key");
      }
      result.put(id, new SecretKeySpec(bytes, "AES"));
      Arrays.fill(bytes, (byte) 0);
    });
    return Map.copyOf(result);
  }

  private static String readActiveKeyId(String configured, Map<String, SecretKey> keys) {
    if (configured == null || configured.isBlank()) {
      if (!keys.isEmpty()) {
        throw new IllegalStateException("salat.secret.active-key-id is missing, although keys are configured");
      }
      return null;
    }
    var id = configured.trim();
    if (!keys.containsKey(id)) {
      throw new IllegalStateException("salat.secret.active-key-id names the key '" + id
          + "', which is not configured");
    }
    return id;
  }

  /** Whether new values can be encrypted — there is an active key. */
  boolean canEncrypt() {
    return activeKeyId != null;
  }

  String activeKeyId() {
    return activeKeyId;
  }

  /** Whether a value encrypted with this key can be decrypted here. */
  boolean knows(String keyId) {
    return keys.containsKey(keyId);
  }

  Sealed encrypt(byte[] plaintext, String associatedData) {
    if (activeKeyId == null) {
      throw new IllegalStateException("No key configured for the secret store");
    }
    var iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    try {
      var cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
      var encrypted = cipher.doFinal(plaintext);
      var payload = new byte[IV_BYTES + encrypted.length];
      System.arraycopy(iv, 0, payload, 0, IV_BYTES);
      System.arraycopy(encrypted, 0, payload, IV_BYTES, encrypted.length);
      return new Sealed(activeKeyId, payload);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Encrypting a secret failed", ex);
    }
  }

  /**
   * @throws UnreadableSecretException when the key is not configured, the payload was changed or
   *     belongs to another row
   */
  byte[] decrypt(String keyId, byte[] payload, String associatedData) {
    var key = keys.get(keyId);
    if (key == null) {
      throw new UnreadableSecretException("the key '" + keyId + "' is not configured");
    }
    if (payload == null || payload.length <= IV_BYTES) {
      throw new UnreadableSecretException("the payload is too short");
    }
    try {
      var cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
      cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
      return cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
    } catch (GeneralSecurityException ex) {
      throw new UnreadableSecretException("the payload does not decrypt with the key '" + keyId + "'");
    }
  }

  /** A value encrypted with the key {@code keyId}. */
  record Sealed(String keyId, byte[] payload) {

  }

  /** A value that cannot be decrypted here. The message names the reason, never the content. */
  static class UnreadableSecretException extends RuntimeException {

    UnreadableSecretException(String reason) {
      super(reason);
    }
  }
}
