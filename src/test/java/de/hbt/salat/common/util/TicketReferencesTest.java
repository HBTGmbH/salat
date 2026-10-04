package de.hbt.salat.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static de.hbt.salat.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCE_DUPLICATE;
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCE_INVALID_LENGTH;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.exception.InvalidDataException;

/** The rule every ticket reference is stored under, and the keys proposed from a comment (#982, #1326). */
class TicketReferencesTest {

  @Test
  void a_key_is_stored_in_capitals() {
    assertThat(TicketReferences.normalize("PROJ-123")).isEqualTo("PROJ-123");
    assertThat(TicketReferences.normalize("proj-123")).isEqualTo("PROJ-123");
    assertThat(TicketReferences.normalize("Proj_2-7")).isEqualTo("PROJ_2-7");
  }

  @Test
  void free_text_stays_as_typed() {
    // a word without a number is no key, and a single letter before the hyphen is too short for one
    assertThat(TicketReferences.normalize("Sprintwechsel")).isEqualTo("Sprintwechsel");
    assertThat(TicketReferences.normalize("otp dev meeting")).isEqualTo("otp dev meeting");
    assertThat(TicketReferences.normalize("a-5")).isEqualTo("a-5");
  }

  @Test
  void surrounding_whitespace_is_dropped() {
    assertThat(TicketReferences.normalize("  proj-123 ")).isEqualTo("PROJ-123");
  }

  @Test
  void an_empty_field_means_no_reference() {
    // an emptied form field has to clear the stored reference, not store a blank one
    assertThat(TicketReferences.normalize((String) null)).isNull();
    assertThat(TicketReferences.normalize("")).isNull();
    assertThat(TicketReferences.normalize("   ")).isNull();
    assertThat(TicketReferences.normalize(Arrays.asList("", null, "  "))).isEmpty();
  }

  @Test
  void a_reference_longer_than_the_column_is_rejected() {
    String tooLong = "X".repeat(TICKET_REFERENCE_MAX_LENGTH + 1);

    assertThatThrownBy(() -> TicketReferences.normalize(tooLong))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_TICKET_REFERENCE_INVALID_LENGTH.getCode());
  }

  @Test
  void references_keep_their_order() {
    assertThat(TicketReferences.normalize(List.of("bb-2", "AA-1", "Retro"))).containsExactly("BB-2", "AA-1", "Retro");
  }

  /** A second reference that differs only in case or blanks is refused, not silently dropped. */
  @Test
  void a_reference_twice_is_refused() {
    assertThatThrownBy(() -> TicketReferences.normalize(List.of("PROJ-1", " proj-1")))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_TICKET_REFERENCE_DUPLICATE.getCode());
    assertThatThrownBy(() -> TicketReferences.normalize(List.of("Retro", "retro")))
        .isInstanceOf(InvalidDataException.class);
  }

  @Test
  void keys_of_a_comment_in_order_of_appearance_in_capitals_and_once() {
    assertThat(TicketReferences.keysIn("Analyse ABC-117 und abc-120, Rückfrage zu OPS-7; ABC-117 erledigt"))
        .containsExactly("ABC-117", "ABC-120", "OPS-7");
  }

  @Test
  void a_key_counts_only_on_its_own() {
    assertThat(TicketReferences.keysIn("x-ABC-3 ABC-4-Fix ABC-5x a-5 Abc-2 (ABC-6) ABC-7.")).containsExactly("ABC-2", "ABC-6", "ABC-7");
  }

  @Test
  void every_word_of_the_shape_counts() {
    // the person decides; the proposal ticks nothing in advance
    assertThat(TicketReferences.keysIn("ISO-9001 gelesen, covid-19")).containsExactly("ISO-9001", "COVID-19");
  }

  @Test
  void nothing_in_an_empty_comment() {
    assertThat(TicketReferences.keysIn(null)).isEmpty();
    assertThat(TicketReferences.keysIn("  ")).isEmpty();
  }

  @Test
  void a_key_already_referenced_is_not_proposed_whatever_its_case() {
    assertThat(TicketReferences.keysNotReferenced("ABC-1, abc-2 und ABC-3", List.of("abc-1", " ABC-2 ")))
        .containsExactly("ABC-3");
  }
}
