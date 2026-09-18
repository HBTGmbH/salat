package org.tb.dailyreport.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.tb.dailyreport.domain.RecentBooking;
import org.tb.dailyreport.domain.Timereport;

/**
 * The entries the booking form offers for reuse: the comment of an earlier booking on the same
 * suborder together with the ticket reference it was booked against (#1029). The reference used to
 * be dropped here, although on the same suborder it is as a rule exactly the fitting one.
 */
@ExtendWith(MockitoExtension.class)
class RecentBookingsTest {

  @InjectMocks
  private TimereportDAO classUnderTest;
  @Mock
  private TimereportRepository timereportRepository;

  @Test
  void a_comment_brings_the_reference_of_the_booking_it_comes_from() {
    givenBookings(booking("Daily", "PROJ-123"));

    assertThat(recentBookings()).containsExactly(new RecentBooking("Daily", "PROJ-123"));
  }

  @Test
  void a_booking_without_a_reference_offers_the_comment_alone() {
    givenBookings(booking("Daily", null));

    assertThat(recentBookings()).containsExactly(new RecentBooking("Daily", null));
  }

  /**
   * The pair identifies an entry, not the comment: the same comment booked against two tickets is
   * two offers, and only the reference tells them apart.
   */
  @Test
  void the_same_comment_on_two_tickets_is_offered_twice() {
    givenBookings(booking("Daily", "PROJ-123"), booking("Daily", "PROJ-456"));

    assertThat(recentBookings()).containsExactly(
        new RecentBooking("Daily", "PROJ-123"),
        new RecentBooking("Daily", "PROJ-456"));
  }

  @Test
  void the_same_comment_on_the_same_ticket_is_offered_once() {
    givenBookings(booking("Daily", "PROJ-123"), booking(" Daily ", "PROJ-123"));

    assertThat(recentBookings()).containsExactly(new RecentBooking("Daily", "PROJ-123"));
  }

  @Test
  void a_booking_without_a_comment_is_no_offer() {
    givenBookings(booking(null, "PROJ-123"), booking("   ", "PROJ-456"), booking("Daily", null));

    assertThat(recentBookings()).containsExactly(new RecentBooking("Daily", null));
  }

  @Test
  void at_most_five_entries_are_offered() {
    givenBookings(booking("a", null), booking("b", null), booking("c", null),
        booking("d", null), booking("e", null), booking("f", null));

    assertThat(recentBookings()).hasSize(5);
  }

  private List<RecentBooking> recentBookings() {
    return classUnderTest.getRecentBookingsByEmployeeContractIdAndSuborderId(1L, 2L);
  }

  private void givenBookings(Timereport... timereports) {
    when(timereportRepository.findAll(any(Specification.class))).thenReturn(Arrays.asList(timereports));
  }

  private static Timereport booking(String comment, String ticketReference) {
    var timereport = new Timereport();
    timereport.setTaskdescription(comment);
    timereport.setTicketReference(ticketReference);
    return timereport;
  }

}
