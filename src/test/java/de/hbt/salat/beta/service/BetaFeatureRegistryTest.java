package de.hbt.salat.beta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.beta.BetaFeature;
import de.hbt.salat.common.beta.BetaFeatureContributor;

/** The betas come from the modules (#1447); the registry only collects them. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class BetaFeatureRegistryTest {

  private static final BetaFeature FIRST = new BetaFeature("first-beta", 10);
  private static final BetaFeature SECOND = new BetaFeature("second-beta", 5);

  @Test
  void collects_the_betas_of_every_module_by_key() {
    var registry = new BetaFeatureRegistry(List.of(() -> List.of(SECOND), () -> List.of(FIRST)));

    assertThat(registry.all()).containsExactly(FIRST, SECOND);
    assertThat(registry.find("second-beta")).contains(SECOND);
    assertThat(registry.isKnown("removed-beta")).isFalse();
  }

  @Test
  void without_contributions_there_is_no_beta() {
    var registry = new BetaFeatureRegistry(List.of());

    assertThat(registry.all()).isEmpty();
    assertThat(registry.isKnown("first-beta")).isFalse();
  }

  /** The key is what a person's switch is stored under; two modules must not share it. */
  @Test
  void a_key_contributed_twice_stops_the_start() {
    BetaFeatureContributor one = () -> List.of(FIRST);
    BetaFeatureContributor other = () -> List.of(new BetaFeature("first-beta", 3));

    assertThatThrownBy(() -> new BetaFeatureRegistry(List.of(one, other)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("first-beta");
  }

  @Test
  void a_beta_needs_a_well_formed_key_and_at_least_one_use() {
    assertThatThrownBy(() -> new BetaFeature("Favoriten zuerst", 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BetaFeature(null, 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BetaFeature("favoritesfirst", 0)).isInstanceOf(IllegalArgumentException.class);
    assertThat(new BetaFeature("favoritesfirst", 1).labelKey()).isEqualTo("main.settings.beta.favoritesfirst.label");
  }
}
