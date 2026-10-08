package de.hbt.salat.secret.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.secret.service.SecretCipher.UnreadableSecretException;

/** AES-256-GCM with the keys of the environment (#1432, ADR-0038 §3). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SecretCipherTest {

  private static final String K1 = randomKey();
  private static final String K2 = randomKey();
  private static final byte[] PLAINTEXT = "jira-user:api-token".getBytes(StandardCharsets.UTF_8);
  private static final String ROW_1 = "secret:1:USERNAME_PASSWORD";

  @Test
  void what_was_encrypted_decrypts_again() {
    var cipher = cipher("k1", Map.of("k1", K1));

    var sealed = cipher.encrypt(PLAINTEXT, ROW_1);

    assertThat(sealed.keyId()).isEqualTo("k1");
    assertThat(cipher.decrypt("k1", sealed.payload(), ROW_1)).isEqualTo(PLAINTEXT);
  }

  @Test
  void the_payload_carries_no_plain_text() {
    var sealed = cipher("k1", Map.of("k1", K1)).encrypt(PLAINTEXT, ROW_1);

    assertThat(new String(sealed.payload(), StandardCharsets.ISO_8859_1)).doesNotContain("api-token");
  }

  @Test
  void every_value_gets_its_own_iv() {
    var cipher = cipher("k1", Map.of("k1", K1));

    var first = cipher.encrypt(PLAINTEXT, ROW_1).payload();
    var second = cipher.encrypt(PLAINTEXT, ROW_1).payload();

    assertThat(first).hasSize(12 + PLAINTEXT.length + 16);
    assertThat(Arrays.copyOf(first, 12)).isNotEqualTo(Arrays.copyOf(second, 12));
    assertThat(first).isNotEqualTo(second);
  }

  /** The associated data binds the cipher text to its row: copied elsewhere, it does not decrypt. */
  @Test
  void a_cipher_text_copied_into_another_row_does_not_decrypt() {
    var cipher = cipher("k1", Map.of("k1", K1));
    var sealed = cipher.encrypt(PLAINTEXT, ROW_1);

    assertThatThrownBy(() -> cipher.decrypt("k1", sealed.payload(), "secret:2:USERNAME_PASSWORD"))
        .isInstanceOf(UnreadableSecretException.class);
    assertThatThrownBy(() -> cipher.decrypt("k1", sealed.payload(), "secret:1:TOKEN"))
        .isInstanceOf(UnreadableSecretException.class);
  }

  @Test
  void a_changed_payload_does_not_decrypt() {
    var cipher = cipher("k1", Map.of("k1", K1));
    var payload = cipher.encrypt(PLAINTEXT, ROW_1).payload();
    payload[payload.length - 1] ^= 1;

    assertThatThrownBy(() -> cipher.decrypt("k1", payload, ROW_1)).isInstanceOf(UnreadableSecretException.class);
  }

  /** A copy of the database from another environment: its key is not here (ADR-0038 §4). */
  @Test
  void a_value_of_another_key_does_not_decrypt() {
    var elsewhere = cipher("k1", Map.of("k1", K1)).encrypt(PLAINTEXT, ROW_1);
    var here = cipher("k1", Map.of("k1", K2));

    assertThatThrownBy(() -> here.decrypt("k1", elsewhere.payload(), ROW_1))
        .isInstanceOf(UnreadableSecretException.class);
    assertThatThrownBy(() -> here.decrypt("k9", elsewhere.payload(), ROW_1))
        .isInstanceOf(UnreadableSecretException.class)
        .hasMessageContaining("k9");
  }

  @Test
  void a_key_that_is_no_longer_active_still_decrypts() {
    var old = cipher("k1", Map.of("k1", K1)).encrypt(PLAINTEXT, ROW_1);
    var cipher = cipher("k2", Map.of("k1", K1, "k2", K2));

    assertThat(cipher.decrypt("k1", old.payload(), ROW_1)).isEqualTo(PLAINTEXT);
    assertThat(cipher.encrypt(PLAINTEXT, ROW_1).keyId()).isEqualTo("k2");
  }

  @Test
  void without_a_key_the_application_starts_and_encrypts_nothing() {
    var cipher = cipher(null, Map.of());

    assertThat(cipher.canEncrypt()).isFalse();
    assertThatThrownBy(() -> cipher.encrypt(PLAINTEXT, ROW_1)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void a_key_that_is_not_base64_stops_the_start() {
    assertThatThrownBy(() -> cipher("k1", Map.of("k1", "kein Base64!")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not Base64");
  }

  @Test
  void a_key_that_is_not_256_bits_long_stops_the_start() {
    var key128 = Base64.getEncoder().encodeToString(new byte[16]);

    assertThatThrownBy(() -> cipher("k1", Map.of("k1", key128)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("256-bit")
        .hasMessageNotContaining(key128);
  }

  @Test
  void an_active_key_id_without_a_key_stops_the_start() {
    assertThatThrownBy(() -> cipher("k2", Map.of("k1", K1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("k2");
    assertThatThrownBy(() -> cipher("k1", Map.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void keys_without_an_active_key_id_stop_the_start() {
    assertThatThrownBy(() -> cipher(" ", Map.of("k1", K1))).isInstanceOf(IllegalStateException.class);
  }

  /** Spring turns an underscore in the name of an environment variable into a dot. */
  @Test
  void a_key_id_with_anything_but_lower_case_letters_and_digits_stops_the_start() {
    assertThatThrownBy(() -> cipher("k.1", Map.of("k.1", K1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lower case letters and digits");
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
