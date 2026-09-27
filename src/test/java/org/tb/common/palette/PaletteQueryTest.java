package org.tb.common.palette;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * What the palette makes of the typed text before any provider sees it (#1157): the words that reach
 * the database as LIKE patterns, and the tiers by which the providers rank what came back.
 *
 * <p>The patterns are escaped with {@code !}, not with the backslash, so that a percent sign or an
 * underscore in a sign is looked for as such and not read as a wildcard. The tiers ignore case and
 * diacritics, as the palette's own ranking in {@code salat.js} does — {@code mobel} has to find
 * {@code Möbel} as high up as {@code möbel} does.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class PaletteQueryTest {

  // --- text and words -----------------------------------------------------------------------------

  @Test
  void collapses_whitespace_and_lowers_the_case() {
    assertThat(PaletteQuery.of("  MUSTER \t 01  ").text()).isEqualTo("muster 01");
  }

  @Test
  void reads_a_missing_text_as_an_empty_query() {
    var query = PaletteQuery.of(null);

    assertThat(query.text()).isEmpty();
    assertThat(query.isSearchable()).isFalse();
    assertThat(query.likeWord(0)).isNull();
  }

  @Test
  void is_searchable_from_two_characters_on() {
    assertThat(PaletteQuery.of("m").isSearchable()).isFalse();
    assertThat(PaletteQuery.of("mu").isSearchable()).isTrue();
  }

  @Test
  void does_not_count_surrounding_whitespace_as_characters() {
    assertThat(PaletteQuery.of("  m  ").isSearchable()).isFalse();
  }

  @Test
  void turns_every_word_into_a_like_pattern_of_its_own() {
    var query = PaletteQuery.of("Muster Wartung");

    assertThat(query.likeWord(0)).isEqualTo("%muster%");
    assertThat(query.likeWord(1)).isEqualTo("%wartung%");
  }

  /** The queries read a {@code null} word as "no condition". */
  @Test
  void answers_null_for_a_word_the_query_does_not_have() {
    assertThat(PaletteQuery.of("Muster Wartung").likeWord(2)).isNull();
    assertThat(PaletteQuery.of("").likeWord(0)).isNull();
  }

  /** The queries have a fixed parameter for each of the three words; a fourth one is dropped. */
  @Test
  void keeps_at_most_three_words() {
    var query = PaletteQuery.of("eins zwei drei vier");

    assertThat(query.likeWord(PaletteQuery.MAX_WORDS - 1)).isEqualTo("%drei%");
    assertThat(query.likeWord(PaletteQuery.MAX_WORDS)).isNull();
  }

  /**
   * An emoji takes four bytes in UTF-8, and MySQL refuses to compare it with a column in utf8mb3:
   * the query would end in an error instead of in no hits. Such characters stand in no sign and no
   * name anyway.
   */
  @Test
  void drops_characters_outside_the_basic_multilingual_plane() {
    var query = PaletteQuery.of("muster \uD83D\uDE00 wartung\uD83D\uDE00");

    assertThat(query.text()).isEqualTo("muster wartung");
    assertThat(query.likeWord(1)).isEqualTo("%wartung%");
    assertThat(query.likeWord(2)).isNull();
  }

  @Test
  void is_not_searchable_with_nothing_but_such_characters() {
    assertThat(PaletteQuery.of("\uD83D\uDE00\uD83D\uDE00").isSearchable()).isFalse();
  }

  // --- escaping -----------------------------------------------------------------------------------

  @Test
  void escapes_percent_sign_and_underscore_with_the_exclamation_mark() {
    assertThat(PaletteQuery.of("50%_x").likeWord(0)).isEqualTo("%50!%!_x%");
  }

  @Test
  void escapes_the_escape_character_itself() {
    assertThat(PaletteQuery.LIKE_ESCAPE).isEqualTo('!');
    assertThat(PaletteQuery.of("MUSTER!01").likeWord(0)).isEqualTo("%muster!!01%");
  }

  /** With {@code escape '!'} the backslash is an ordinary character of the pattern. */
  @Test
  void leaves_the_backslash_alone() {
    assertThat(PaletteQuery.of("a\\b").likeWord(0)).isEqualTo("%a\\b%");
  }

  // --- match tiers --------------------------------------------------------------------------------

  @Test
  void ranks_a_title_beginning_with_the_query_highest() {
    assertThat(PaletteQuery.of("muster").match("MUSTER-01", "Wartungsvertrag")).isEqualTo(4);
    assertThat(PaletteQuery.of("muster-0").match("MUSTER-01")).isEqualTo(4);
  }

  @Test
  void ranks_a_title_beginning_with_all_words_of_the_query_highest() {
    assertThat(PaletteQuery.of("person p").match("Person P", "ppp")).isEqualTo(4);
  }

  @Test
  void ranks_a_word_start_behind_a_hyphen_as_a_word_start() {
    assertThat(PaletteQuery.of("01").match("MUSTER-01")).isEqualTo(3);
  }

  @Test
  void ranks_a_word_start_behind_a_slash_as_a_word_start() {
    assertThat(PaletteQuery.of("02").match("MUSTER/02")).isEqualTo(3);
  }

  @Test
  void ranks_a_field_beginning_with_the_query_below_the_title() {
    assertThat(PaletteQuery.of("wartung").match("MUSTER-01", "Wartungsvertrag", "Musterkunde"))
        .isEqualTo(3);
  }

  /** The first occurrence stands inside a word, the second one after the slash. */
  @Test
  void looks_past_a_hit_inside_a_word_for_a_word_start() {
    assertThat(PaletteQuery.of("ung").match("MUSTER-01", "Wartung/Ungeplant")).isEqualTo(3);
  }

  @Test
  void ranks_a_hit_inside_a_word_below_a_word_start() {
    assertThat(PaletteQuery.of("uster").match("MUSTER-01", "Wartungsvertrag")).isEqualTo(2);
  }

  @Test
  void does_not_take_a_digit_for_a_word_boundary() {
    assertThat(PaletteQuery.of("01").match("MUSTER101")).isEqualTo(2);
  }

  @Test
  void ranks_words_standing_in_different_fields_in_the_middle() {
    assertThat(PaletteQuery.of("wartung muster").match("MUSTER-01", "Wartungsvertrag")).isEqualTo(2);
  }

  /** The database found it by a field the provider did not pass, or the fields differ from the query. */
  @Test
  void ranks_a_hit_lowest_where_a_word_stands_in_none_of_the_fields() {
    assertThat(PaletteQuery.of("muster pflege").match("MUSTER-01", "Wartungsvertrag")).isEqualTo(1);
  }

  /** The database did not filter by the fourth word, so the ranking does not ask for it either. */
  @Test
  void ranks_by_no_more_than_three_words() {
    assertThat(PaletteQuery.of("muster wartung kunde pflege")
        .match("MUSTER-01", "Wartungsvertrag", "Musterkunde")).isEqualTo(2);
  }

  /** Subtitles and customer names are optional. */
  @Test
  void skips_fields_that_are_null() {
    assertThat(PaletteQuery.of("wartung").match("MUSTER-01", null, "Wartungsvertrag")).isEqualTo(3);
    assertThat(PaletteQuery.of("wartung").match("MUSTER-01", (String) null)).isEqualTo(1);
  }

  // --- the key ------------------------------------------------------------------------------------

  /**
   * A person is shown by name, and a short sign is the beginning of many names. The query that is
   * the whole sign ranks its owner above all of them.
   */
  @Test
  void ranks_the_whole_key_above_a_title_beginning_with_the_query() {
    var query = PaletteQuery.of("KR");

    assertThat(query.matchWithKey("kr", "Anna Zander")).isEqualTo(5);
    assertThat(query.matchWithKey("xa", "Kristin Muster")).isEqualTo(4);
  }

  @Test
  void ranks_a_key_the_query_only_begins_as_any_other_field() {
    assertThat(PaletteQuery.of("kr").matchWithKey("krs", "Anna Zander")).isEqualTo(3);
    assertThat(PaletteQuery.of("zand").matchWithKey("krs", "Anna Zander", "Zander")).isEqualTo(3);
  }

  // --- case and diacritics ------------------------------------------------------------------------

  @Test
  void ignores_the_case_on_both_sides() {
    assertThat(PaletteQuery.of("MuStEr").match("muster-01")).isEqualTo(4);
    assertThat(PaletteQuery.of("muster").match("MUSTER-01")).isEqualTo(4);
  }

  @Test
  void ignores_diacritics_in_the_title() {
    assertThat(PaletteQuery.of("mobel").match("Möbelmuster")).isEqualTo(4);
  }

  @Test
  void ignores_diacritics_in_the_query() {
    assertThat(PaletteQuery.of("MÖBEL").match("Mobelmuster")).isEqualTo(4);
  }

  /** The same letter, written as a base letter with a combining mark. */
  @Test
  void ignores_diacritics_written_as_combining_marks() {
    assertThat(PaletteQuery.of("mobel").match("Mo\u0308belmuster")).isEqualTo(4);
  }

  @Test
  void ignores_diacritics_at_word_starts_and_inside_words() {
    assertThat(PaletteQuery.of("uber").match("MUSTER-01", "Wartung/Übergabe")).isEqualTo(3);
    assertThat(PaletteQuery.of("bergabe").match("MUSTER-01", "Übergabe")).isEqualTo(2);
  }
}
