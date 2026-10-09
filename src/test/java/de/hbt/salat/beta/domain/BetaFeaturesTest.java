package de.hbt.salat.beta.domain;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import de.hbt.salat.common.beta.BetaFeature;
import de.hbt.salat.common.beta.BetaFeatureContributor;

/**
 * Since #1447 the set holds keys and leaves the question which keys are still a beta to the caller,
 * so the round trip is tested with a stand-in beta.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BetaFeaturesTest {

  private static final Predicate<String> ONLY_TEST_BETA = "test-beta"::equals;

  @Test
  public void should_be_empty_when_nothing_is_enabled() {
    assertThat(BetaFeatures.none().toMap()).isEmpty();
    assertThat(BetaFeatures.ofKeys(List.of(), ONLY_TEST_BETA).keys()).isEmpty();
    assertThat(BetaFeatures.ofKeys(null, ONLY_TEST_BETA).keys()).isEmpty();
  }

  /** "timeinput" is what users who tried #830 still have stored; the beta ended with #1248. */
  @Test
  public void should_ignore_keys_of_features_that_no_longer_exist() {
    var stored = Map.<String, Object>of("enabled", List.of("timeinput", "test-beta"));

    var features = BetaFeatures.from(stored, ONLY_TEST_BETA);

    assertThat(features.keys()).containsExactly("test-beta");
    assertThat(features.toMap()).isEqualTo(Map.of("enabled", List.of("test-beta")));
  }

  @Test
  public void should_tolerate_a_missing_or_malformed_section() {
    assertThat(BetaFeatures.from(null, ONLY_TEST_BETA).keys()).isEmpty();
    assertThat(BetaFeatures.from(Map.of(), ONLY_TEST_BETA).keys()).isEmpty();
    assertThat(BetaFeatures.from(Map.of("enabled", "test-beta"), ONLY_TEST_BETA).keys()).isEmpty();
  }

  @Test
  public void with_adds_a_key_once() {
    var features = BetaFeatures.none().with("test-beta");

    assertThat(features.with("test-beta")).isEqualTo(features);
    assertThat(features.keys()).isEqualTo(Set.of("test-beta"));
  }

  /**
   * The settings page builds the message keys of each switch at runtime, which the template check in
   * {@code MessageResourcesConsistencyTest} cannot see. The betas come from the modules (#1447), so
   * the check finds every {@link BetaFeatureContributor} on the class path and builds it without
   * Spring. Without a contribution this passes trivially; it is here for the next beta.
   */
  @Test
  public void every_contributed_beta_has_its_switch_texts_in_both_bundles() throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AssignableTypeFilter(BetaFeatureContributor.class));
    var features = new ArrayList<BetaFeature>();
    for (var candidate : scanner.findCandidateComponents("de.hbt.salat")) {
      var contributor = (BetaFeatureContributor) Class.forName(candidate.getBeanClassName())
          .getDeclaredConstructor().newInstance();
      features.addAll(contributor.getBetaFeatures());
    }
    for (String bundle : List.of("/de/hbt/salat/web/MessageResources.properties",
        "/de/hbt/salat/web/MessageResources_en.properties")) {
      var properties = new Properties();
      try (Reader reader = new InputStreamReader(getClass().getResourceAsStream(bundle), UTF_8)) {
        properties.load(reader);
      }
      for (BetaFeature feature : features) {
        assertThat(properties).as(bundle).containsKeys(feature.labelKey(), feature.helpKey());
      }
    }
  }

}
