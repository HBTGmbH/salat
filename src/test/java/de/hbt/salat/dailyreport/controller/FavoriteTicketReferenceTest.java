package de.hbt.salat.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static de.hbt.salat.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCE_INVALID_LENGTH;
import static de.hbt.salat.dailyreport.controller.TimereportController.favoriteFrom;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.exception.InvalidDataException;

/**
 * A booking saved as a favourite takes its ticket references along (#1029, #1326). Before that the
 * favourite was built from comment and duration alone, so the reference was lost at the moment the
 * favourite was made.
 */
class FavoriteTicketReferenceTest {

  @Test
  void a_booking_saved_as_a_favourite_takes_its_references_along() {
    assertThat(favoriteFrom(7L, 1, 30, form("PROJ-123", "PROJ-130")).ticketReferences())
        .containsExactly("PROJ-123", "PROJ-130");
  }

  @Test
  void a_booking_without_a_reference_makes_a_favourite_without_one() {
    // no blank entry: applying the favourite must not write an empty reference into the booking
    assertThat(favoriteFrom(7L, 1, 30, form()).ticketReferences()).isEmpty();
    assertThat(favoriteFrom(7L, 1, 30, form("")).ticketReferences()).isEmpty();
    assertThat(favoriteFrom(7L, 1, 30, form("   ")).ticketReferences()).isEmpty();
  }

  /**
   * The favourite goes through the same normalisation as the booking. Written a second time next to
   * the form, the two rules would drift apart.
   */
  @Test
  void the_reference_is_stored_under_the_same_rule_as_on_the_booking() {
    assertThat(favoriteFrom(7L, 1, 30, form("  proj-123 ")).ticketReferences())
        .containsExactly("PROJ-123");

    var tooLong = form("X".repeat(TICKET_REFERENCE_MAX_LENGTH + 1));
    assertThatThrownBy(() -> favoriteFrom(7L, 1, 30, tooLong))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_TICKET_REFERENCE_INVALID_LENGTH.getCode());
  }

  @Test
  void the_rest_of_the_favourite_is_unchanged() {
    var favourite = favoriteFrom(7L, 1, 30, form("PROJ-123"));

    assertThat(favourite.employeeorderId()).isEqualTo(7L);
    assertThat(favourite.hours()).isEqualTo(1);
    assertThat(favourite.minutes()).isEqualTo(30);
    assertThat(favourite.comment()).isEqualTo("Daily");
  }

  private static TimereportForm form(String... ticketReferences) {
    var form = new TimereportForm();
    form.setComment("Daily");
    form.setTicketReferences(new ArrayList<>(Arrays.asList(ticketReferences)));
    return form;
  }

}
