package de.hbt.salat.settings.domain;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * There is currently no {@link BetaFeature} constant, so only the behaviour that does not need one
 * is covered here. The next beta brings back the round trip and {@link BetaFeatures#with}.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BetaFeaturesTest {

  @Test
  public void should_be_empty_when_nothing_is_enabled() {
    assertThat(BetaFeatures.none().toMap()).isEmpty();
    assertThat(BetaFeatures.ofKeys(List.of()).enabled()).isEmpty();
    assertThat(BetaFeatures.ofKeys(null).enabled()).isEmpty();
  }

  /** "timeinput" is what users who tried #830 still have stored; the beta ended with #1248. */
  @Test
  public void should_ignore_keys_of_features_that_no_longer_exist() {
    var stored = Map.<String, Object>of("enabled", List.of("timeinput", "removed-beta"));

    var features = BetaFeatures.from(stored);

    assertThat(features.enabled()).isEmpty();
    assertThat(features.toMap()).isEmpty();
  }

  @Test
  public void should_tolerate_a_missing_or_malformed_section() {
    assertThat(BetaFeatures.from(null).enabled()).isEmpty();
    assertThat(BetaFeatures.from(Map.of()).enabled()).isEmpty();
    assertThat(BetaFeatures.from(Map.of("enabled", "timeinput")).enabled()).isEmpty();
  }

  /**
   * The settings page builds the message keys of each switch at runtime, which the template check in
   * {@code MessageResourcesConsistencyTest} cannot see. Without a constant this passes trivially; it
   * is here for the next beta.
   */
  @Test
  public void every_feature_has_its_switch_texts_in_both_bundles() throws IOException {
    for (String bundle : List.of("/de/hbt/salat/web/MessageResources.properties",
        "/de/hbt/salat/web/MessageResources_en.properties")) {
      var properties = new Properties();
      try (Reader reader = new InputStreamReader(getClass().getResourceAsStream(bundle), UTF_8)) {
        properties.load(reader);
      }
      for (BetaFeature feature : BetaFeature.values()) {
        assertThat(properties).as(bundle).containsKeys(feature.labelKey(), feature.helpKey());
      }
    }
  }

  @Test
  public void unknown_or_missing_keys_resolve_to_nothing() {
    assertThat(BetaFeature.ofKey("timeinput")).isEmpty();
    assertThat(BetaFeature.ofKey(null)).isEmpty();
    assertThat(BetaFeature.ofKey("")).isEmpty();
  }

}
