package de.hbt.salat.jira.command;

import java.time.LocalDate;
import java.util.Map;

/**
 * What was booked on one ticket on one day, per person (#1007, #1408).
 *
 * <p>Plain values only, and deliberately so: this crosses a module boundary, and an entity or an
 * id of a foreign module as a component would hand the association graph out with it (→ ADR-0021).
 * A person appears by the sign of the employee and nothing else — no name, no task description.
 * The worklog comment names exactly these signs with their shares; everything else a booking says
 * about who and what stays in SALAT.
 *
 * @param ticketReference a ticket reference of the bookings, as {@code Timereport} stores it
 * @param minutesBySign   per sign the share of each booking that falls to this reference (#1326),
 *                        summed over the bookings of that person. A share of zero is kept — it
 *                        is the comment that leaves it out, and the comment that sorts by sign.
 */
public record TicketDaySum(LocalDate workDate, String ticketReference, Map<String, Long> minutesBySign) {

  public TicketDaySum {
    minutesBySign = Map.copyOf(minutesBySign);
  }
}
