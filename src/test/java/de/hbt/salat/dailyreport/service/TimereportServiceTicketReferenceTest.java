package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import de.hbt.salat.dailyreport.domain.Timereport;

/**
 * The ticket references of a booking (#982, #1326). The rule they are stored under is
 * {@code TicketReferences}, see {@code TicketReferencesTest}.
 */
class TimereportServiceTicketReferenceTest {

  /**
   * Serial bookings are created from a twin of the first one. Were the references not copied, every
   * day but the first would lose them.
   */
  @Test
  void a_serial_booking_carries_the_references_to_every_day() {
    var timereport = new Timereport();
    timereport.setTicketReferences(List.of("PROJ-123", "PROJ-130"));

    assertThat(timereport.getTwin().getTicketReferences()).containsExactly("PROJ-123", "PROJ-130");
  }

}
