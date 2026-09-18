package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tb.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;
import static org.tb.common.exception.ErrorCode.TR_TICKET_REFERENCE_INVALID_LENGTH;
import static org.tb.dailyreport.controller.TimereportController.favoriteFrom;

import org.junit.jupiter.api.Test;
import org.tb.common.exception.InvalidDataException;

/**
 * A booking saved as a favourite takes its ticket reference along (#1029). Before that the
 * favourite was built from comment and duration alone, so the reference was lost at the moment the
 * favourite was made.
 */
class FavoriteTicketReferenceTest {

  @Test
  void a_booking_saved_as_a_favourite_takes_its_reference_along() {
    assertThat(favoriteFrom(7L, 1, 30, form("PROJ-123")).getTicketReference()).isEqualTo("PROJ-123");
  }

  @Test
  void a_booking_without_a_reference_makes_a_favourite_without_one() {
    // null, not "": applying the favourite must not write an empty string into the booking
    assertThat(favoriteFrom(7L, 1, 30, form("")).getTicketReference()).isNull();
    assertThat(favoriteFrom(7L, 1, 30, form(null)).getTicketReference()).isNull();
    assertThat(favoriteFrom(7L, 1, 30, form("   ")).getTicketReference()).isNull();
  }

  /**
   * The favourite goes through the same normalisation as the booking. Written a second time next to
   * the form, the two rules would drift apart.
   */
  @Test
  void the_reference_is_stored_under_the_same_rule_as_on_the_booking() {
    assertThat(favoriteFrom(7L, 1, 30, form("  PROJ-123 ")).getTicketReference())
        .isEqualTo("PROJ-123");

    var tooLong = form("X".repeat(TICKET_REFERENCE_MAX_LENGTH + 1));
    assertThatThrownBy(() -> favoriteFrom(7L, 1, 30, tooLong))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_TICKET_REFERENCE_INVALID_LENGTH.getCode());
  }

  @Test
  void the_rest_of_the_favourite_is_unchanged() {
    var favourite = favoriteFrom(7L, 1, 30, form("PROJ-123"));

    assertThat(favourite.getEmployeeorderId()).isEqualTo(7L);
    assertThat(favourite.getHours()).isEqualTo(1);
    assertThat(favourite.getMinutes()).isEqualTo(30);
    assertThat(favourite.getComment()).isEqualTo("Daily");
  }

  private static TimereportForm form(String ticketReference) {
    var form = new TimereportForm();
    form.setComment("Daily");
    form.setTicketReference(ticketReference);
    return form;
  }

}
