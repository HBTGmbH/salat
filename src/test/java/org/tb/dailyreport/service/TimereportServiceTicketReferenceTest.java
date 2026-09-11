package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tb.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;
import static org.tb.common.exception.ErrorCode.TR_TICKET_REFERENCE_INVALID_LENGTH;
import static org.tb.dailyreport.service.TimereportService.normalizeTicketReference;

import org.junit.jupiter.api.Test;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.Timereport;

/**
 * The optional free text reference to an external ticket (#982). It is stored as entered - the
 * suggestions offered while booking are a convenience, not a constraint.
 */
class TimereportServiceTicketReferenceTest {

  @Test
  void a_reference_is_stored_as_entered() {
    assertThat(normalizeTicketReference("PROJ-123")).isEqualTo("PROJ-123");
  }

  @Test
  void surrounding_whitespace_is_dropped() {
    assertThat(normalizeTicketReference("  PROJ-123 ")).isEqualTo("PROJ-123");
  }

  @Test
  void an_empty_field_means_no_reference() {
    // an emptied form field has to clear the stored reference, not store a blank one
    assertThat(normalizeTicketReference(null)).isNull();
    assertThat(normalizeTicketReference("")).isNull();
    assertThat(normalizeTicketReference("   ")).isNull();
  }

  @Test
  void a_reference_longer_than_the_column_is_rejected() {
    String tooLong = "X".repeat(TICKET_REFERENCE_MAX_LENGTH + 1);

    assertThatThrownBy(() -> normalizeTicketReference(tooLong))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(TR_TICKET_REFERENCE_INVALID_LENGTH.getCode());
  }

  /**
   * Serial bookings are created from a twin of the first one. Were the reference not copied, every
   * day but the first would lose it.
   */
  @Test
  void a_serial_booking_carries_the_reference_to_every_day() {
    var timereport = new Timereport();
    timereport.setTicketReference("PROJ-123");

    assertThat(timereport.getTwin().getTicketReference()).isEqualTo("PROJ-123");
  }

}
