package org.tb.settings.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class BetaFeaturesTest {

  @Test
  public void should_round_trip_through_the_preference_map() {
    var features = BetaFeatures.ofKeys(List.of("timeinput"));

    assertThat(features.has(BetaFeature.TIME_INPUT)).isTrue();
    assertThat(BetaFeatures.from(features.toMap())).isEqualTo(features);
  }

  @Test
  public void should_be_empty_when_nothing_is_enabled() {
    assertThat(BetaFeatures.none().toMap()).isEmpty();
    assertThat(BetaFeatures.ofKeys(List.of()).enabled()).isEmpty();
    assertThat(BetaFeatures.ofKeys(null).enabled()).isEmpty();
  }

  @Test
  public void should_read_the_enabled_keys_as_deserialized_by_jackson() {
    var stored = Map.<String, Object>of("enabled", List.of("timeinput"));

    assertThat(BetaFeatures.from(stored).has(BetaFeature.TIME_INPUT)).isTrue();
  }

  @Test
  public void should_ignore_keys_of_features_that_no_longer_exist() {
    var stored = Map.<String, Object>of("enabled", List.of("timeinput", "removed-beta"));

    assertThat(BetaFeatures.from(stored).enabled()).containsExactly(BetaFeature.TIME_INPUT);
  }

  @Test
  public void should_tolerate_a_missing_or_malformed_section() {
    assertThat(BetaFeatures.from(null).enabled()).isEmpty();
    assertThat(BetaFeatures.from(Map.of()).enabled()).isEmpty();
    assertThat(BetaFeatures.from(Map.of("enabled", "timeinput")).enabled()).isEmpty();
  }

  @Test
  public void with_should_add_a_feature_without_touching_the_original() {
    var empty = BetaFeatures.none();

    var enabled = empty.with(BetaFeature.TIME_INPUT);

    assertThat(empty.enabled()).isEmpty();
    assertThat(enabled.enabled()).isEqualTo(Set.of(BetaFeature.TIME_INPUT));
    assertThat(enabled.with(BetaFeature.TIME_INPUT)).isSameAs(enabled);
  }

  @Test
  public void keys_should_be_resolvable_by_their_stored_value() {
    assertThat(BetaFeature.ofKey("timeinput")).contains(BetaFeature.TIME_INPUT);
    assertThat(BetaFeature.ofKey("nope")).isEmpty();
    assertThat(BetaFeature.ofKey(null)).isEmpty();
    assertThat(BetaFeature.ofKey("")).isEmpty();
  }

}
