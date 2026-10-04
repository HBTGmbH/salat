package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
import java.util.List;

/**
 * A booking of an earlier day, reduced to what a booking of today can take over from it (#1017):
 * the employee order it was made on, its comment, the tickets it was booked against and how long it
 * took. Whoever works on the same task for several days would otherwise have to leaf back through
 * the days to read those four values off the earlier booking.
 *
 * <p>The first three identify the entry, the duration does not: the same task booked on two days
 * with different durations is one offer, not two, and the most recent duration is the one it
 * carries. Only the employee order id travels, not the order itself — the caller that needs a label
 * fetches it (→ ADR-0021).
 */
public record PreviousBooking(long employeeorderId, String comment, List<String> ticketReferences,
                              Duration duration) {

  public PreviousBooking {
    ticketReferences = ticketReferences == null ? List.of() : List.copyOf(ticketReferences);
  }
}
