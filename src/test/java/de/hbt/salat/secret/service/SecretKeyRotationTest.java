package de.hbt.salat.secret.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.secret.domain.Secret;
import de.hbt.salat.secret.domain.SecretType;
import de.hbt.salat.secret.persistence.SecretRepository;

/** The change of keys at start (#1432, ADR-0038 §3): rows of a former key get the active one. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SecretKeyRotationTest {

  private static final String OLD = randomKey();
  private static final String NEW = randomKey();
  private static final byte[] PLAINTEXT = "token".getBytes(StandardCharsets.UTF_8);

  private final SecretRepository repository = mock(SecretRepository.class);

  @Test
  void a_secret_of_the_former_key_is_encrypted_with_the_active_one() {
    var before = cipher("k1", Map.of("k1", OLD));
    var secret = secret(1L, before);
    var after = cipher("k2", Map.of("k1", OLD, "k2", NEW));
    given(after, secret);

    new SecretKeyRotation(repository, after, mock(PlatformTransactionManager.class)).encryptWithActiveKey();

    assertThat(secret.getKeyId()).isEqualTo("k2");
    assertThat(after.decrypt("k2", secret.getPayload(), secret.associatedData())).isEqualTo(PLAINTEXT);
    // the former key can go now: the row no longer needs it
    var withoutOld = cipher("k2", Map.of("k2", NEW));
    assertThat(withoutOld.decrypt("k2", secret.getPayload(), secret.associatedData())).isEqualTo(PLAINTEXT);
  }

  /** Only entering the secret again helps there; the row stays as it is. */
  @Test
  void a_secret_of_an_unknown_key_stays_as_it_is() {
    var elsewhere = cipher("prod", Map.of("prod", OLD));
    var secret = secret(1L, elsewhere);
    var payload = secret.getPayload();
    var here = cipher("k2", Map.of("k2", NEW));
    given(here, secret);

    new SecretKeyRotation(repository, here, mock(PlatformTransactionManager.class)).encryptWithActiveKey();

    assertThat(secret.getKeyId()).isEqualTo("prod");
    assertThat(secret.getPayload()).isSameAs(payload);
  }

  private void given(SecretCipher cipher, Secret secret) {
    when(repository.findIdsNotEncryptedWith(cipher.activeKeyId())).thenReturn(List.of(secret.getId()));
    when(repository.findById(secret.getId())).thenReturn(Optional.of(secret));
  }

  private static Secret secret(long id, SecretCipher cipher) {
    var secret = new Secret();
    ReflectionTestUtils.setField(secret, AuditedEntity.class, "id", id, Long.class);
    secret.setType(SecretType.TOKEN);
    var sealed = cipher.encrypt(PLAINTEXT, secret.associatedData());
    secret.setKeyId(sealed.keyId());
    secret.setPayload(sealed.payload());
    return secret;
  }

  private static SecretCipher cipher(String activeKeyId, Map<String, String> keys) {
    var properties = new SalatProperties();
    properties.getSecret().setActiveKeyId(activeKeyId);
    properties.getSecret().setKeys(keys);
    return new SecretCipher(properties);
  }

  private static String randomKey() {
    var key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }
}
