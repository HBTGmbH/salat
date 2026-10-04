package de.hbt.salat.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** What counts as naming a renamed order or suborder by its sign (#1206). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SignsRenamedEventTest {

  private final SignsRenamedEvent renamed = new SignsRenamedEvent("ORDER/01", "ORDER/02", 1L, 1L);

  @Test
  void the_old_sign_itself_and_everything_below_it_are_renamed() {
    assertThat(renamed.renamed("ORDER/01")).isEqualTo("ORDER/02");
    assertThat(renamed.renamed("ORDER/01/")).isEqualTo("ORDER/02/");
    assertThat(renamed.renamed("ORDER/01/A%")).isEqualTo("ORDER/02/A%");
  }

  @Test
  void a_sign_that_merely_starts_with_the_same_characters_is_another_suborder() {
    assertThat(renamed.renamed("ORDER/010")).isNull();
    assertThat(renamed.renamed("ORDER/01%")).isNull();
    assertThat(renamed.renamed("ORDER/0%")).isNull();
    assertThat(renamed.renamed(null)).isNull();
  }

  @Test
  void sql_names_the_old_sign_as_a_literal_only() {
    assertThat(renamed.isMentionedIn("where sign = 'ORDER/01'")).isTrue();
    assertThat(renamed.isMentionedIn("where sign like 'ORDER/01/%'")).isTrue();
    assertThat(renamed.isMentionedIn("where sign like 'ORDER/01%'")).isTrue();
    // another suborder, and the sign as part of something else
    assertThat(renamed.isMentionedIn("where sign = 'ORDER/010'")).isFalse();
    assertThat(renamed.isMentionedIn("-- ORDER/01 is old")).isFalse();
    assertThat(renamed.isMentionedIn(null)).isFalse();
  }

  @Test
  void a_suborder_moved_to_another_order_is_told_apart() {
    assertThat(renamed.isMovedToAnotherOrder()).isFalse();
    assertThat(new SignsRenamedEvent("A/01", "B/01", 1L, 2L).isMovedToAnotherOrder()).isTrue();
  }
}
