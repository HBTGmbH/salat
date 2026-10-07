package de.hbt.salat.favorites.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** The favourite settings of a person in the preference store (#1414). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class FavoritePreferencesTest {

  @Test
  void nothing_stored_means_ordered_by_use_and_ten_in_the_short_list() {
    assertThat(FavoritePreferences.from(Map.of())).isEqualTo(FavoritePreferences.defaults());
    assertThat(FavoritePreferences.defaults().listSize()).isEqualTo(10);
  }

  @Test
  void a_stored_value_is_read_back() {
    var preferences = FavoritePreferences.from(Map.of("sortOrder", "custom", "listSize", "25"));

    assertThat(preferences).isEqualTo(new FavoritePreferences(FavoriteSortOrder.CUSTOM, 25));
    assertThat(FavoritePreferences.from(preferences.toMap())).isEqualTo(preferences);
  }

  /** A broken value costs only itself. */
  @Test
  void a_malformed_or_out_of_range_value_falls_back_to_its_default_alone() {
    assertThat(FavoritePreferences.from(Map.of("sortOrder", "custom", "listSize", "viele")))
        .isEqualTo(new FavoritePreferences(FavoriteSortOrder.CUSTOM, 10));
    assertThat(FavoritePreferences.from(Map.of("sortOrder", "quer", "listSize", "99")))
        .isEqualTo(FavoritePreferences.defaults());
  }

  @Test
  void defaults_leave_nothing_behind() {
    assertThat(FavoritePreferences.defaults().toMap()).isEmpty();
  }

  @Test
  void the_short_list_holds_one_to_fifty() {
    assertThat(FavoritePreferences.isValidListSize(1)).isTrue();
    assertThat(FavoritePreferences.isValidListSize(50)).isTrue();
    assertThat(FavoritePreferences.isValidListSize(0)).isFalse();
    assertThat(FavoritePreferences.isValidListSize(51)).isFalse();
  }
}
