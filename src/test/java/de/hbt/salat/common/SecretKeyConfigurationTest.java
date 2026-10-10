package de.hbt.salat.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The keys of the secret store come from the environment only (#1432, ADR-0038 §3): a key in a
 * checked-in profile would sit in the repository next to every copy of the cipher text.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SecretKeyConfigurationTest {

  @Test
  void no_profile_names_a_key_of_the_secret_store() {
    assertThat(ProfileProperties.all())
        .filteredOn(entry -> entry.name().startsWith("salat.secret."))
        .isEmpty();
  }

  /** Client id and client secret of an OAuth app come from the environment only (#1417, ADR-0038 §7). */
  @Test
  void no_profile_names_the_credentials_of_an_oauth_app() {
    assertThat(ProfileProperties.all())
        .filteredOn(entry -> entry.name().startsWith("salat.oauth.")
            && (entry.name().endsWith(".client-id") || entry.name().endsWith(".client-secret")))
        .isEmpty();
  }

  /** The start logs every property; the key and other secrets must not be among them. */
  @Test
  void the_configuration_log_leaves_secrets_out_whatever_their_spelling() {
    assertThat(ConfigurationLogger.isConfidential("SALAT_SECRET_KEYS_K1")).isTrue();
    assertThat(ConfigurationLogger.isConfidential("salat.secret.keys.k1")).isTrue();
    assertThat(ConfigurationLogger.isConfidential("SPRING_DATASOURCE_PASSWORD")).isTrue();
    assertThat(ConfigurationLogger.isConfidential("SALAT_UISTATE_SIGNINGKEY")).isTrue();
    assertThat(ConfigurationLogger.isConfidential("salat.ui-state.signing-key")).isTrue();
    assertThat(ConfigurationLogger.isConfidential("SALAT_OAUTH_CLIENTS_ATLASSIAN_CLIENTSECRET")).isTrue();
    assertThat(ConfigurationLogger.isConfidential("salat.oauth.clients.atlassian.client-secret")).isTrue();
    assertThat(ConfigurationLogger.isConfidential("spring.datasource.url")).isFalse();
  }
}
