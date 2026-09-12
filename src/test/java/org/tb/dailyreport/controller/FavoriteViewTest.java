package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * What the delete confirmation of a favourite names (#835). Two favourites on the same suborder
 * differ only in their comment, so that is what the dialog has to show.
 */
class FavoriteViewTest {

  @Test
  void the_comment_names_the_favourite() {
    assertThat(favourite("HBT-Zeit/01 - HBT gestalten", "Daily").deleteConfirmName())
        .isEqualTo("Daily");
  }

  @Test
  void without_a_comment_the_order_names_it() {
    assertThat(favourite("HBT-Zeit/01 - HBT gestalten", null).deleteConfirmName())
        .isEqualTo("HBT-Zeit/01 - HBT gestalten");
    assertThat(favourite("HBT-Zeit/01 - HBT gestalten", "   ").deleteConfirmName())
        .isEqualTo("HBT-Zeit/01 - HBT gestalten");
  }

  /** confirm() renders plain text in one dialog, so a multi-line comment has to collapse. */
  @Test
  void line_breaks_are_folded_into_single_spaces() {
    assertThat(favourite("order", "Daily\n\nund Code Review").deleteConfirmName())
        .isEqualTo("Daily und Code Review");
  }

  @Test
  void a_long_comment_is_cut_off() {
    var name = favourite("order", "X".repeat(200)).deleteConfirmName();

    assertThat(name).hasSize(60).endsWith("…");
  }

  private static FavoriteView favourite(String label, String comment) {
    return new FavoriteView(1L, label, comment, Duration.ofMinutes(75));
  }

}
