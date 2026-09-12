package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The comment as the delete confirmation of a favourite shows it (#835). Two favourites on the
 * same suborder differ only in their comment, so that is what the dialog leads with - the order
 * stands underneath it and comes from {@code label} unchanged.
 */
class FavoriteViewTest {

  @Test
  void the_comment_is_shown_as_entered() {
    assertThat(favourite("HBT-Zeit/01 - HBT gestalten", "Daily").shortComment())
        .isEqualTo("Daily");
  }

  @Test
  void without_a_comment_there_is_nothing_to_show() {
    assertThat(favourite("HBT-Zeit/01 - HBT gestalten", null).shortComment()).isEmpty();
    assertThat(favourite("HBT-Zeit/01 - HBT gestalten", "   ").shortComment()).isEmpty();
  }

  /** The dialog gives the comment one line, so a multi-line comment has to collapse. */
  @Test
  void line_breaks_are_folded_into_single_spaces() {
    assertThat(favourite("order", "Daily\n\nund Code Review").shortComment())
        .isEqualTo("Daily und Code Review");
  }

  @Test
  void a_long_comment_is_cut_off() {
    var shown = favourite("order", "X".repeat(200)).shortComment();

    assertThat(shown).hasSize(60).endsWith("…");
  }

  private static FavoriteView favourite(String label, String comment) {
    return new FavoriteView(1L, label, comment, Duration.ofMinutes(75));
  }

}
