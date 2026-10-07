package de.hbt.salat.favorites.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** The dialog sends its arrangement as tokens in the order of the page (#1414). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class FavoriteLayoutTest {

  @Test
  void every_favourite_belongs_to_the_section_before_it() {
    var layout = FavoriteLayout.parse(List.of("group:", "favorite:3", "group:7", "favorite:1", "favorite:2", "group:5"));

    assertThat(layout.sections()).containsExactly(
        new FavoriteLayout.Section(null, List.of(3L)),
        new FavoriteLayout.Section(7L, List.of(1L, 2L)),
        new FavoriteLayout.Section(5L, List.of()));
  }

  @Test
  void nothing_sent_is_an_empty_layout() {
    assertThat(FavoriteLayout.parse(null).sections()).isEmpty();
  }

  @Test
  void a_favourite_before_any_section_or_an_unknown_token_is_refused() {
    assertThatThrownBy(() -> FavoriteLayout.parse(List.of("favorite:1"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FavoriteLayout.parse(List.of("group:", "x"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FavoriteLayout.parse(List.of("group:abc"))).isInstanceOf(IllegalArgumentException.class);
  }
}
