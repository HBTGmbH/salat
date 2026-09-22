package org.tb.jira.command;

import java.time.LocalDate;

/**
 * What was booked on one ticket on one day, over all people (#1007).
 *
 * <p>Plain values only, and deliberately so: this crosses a module boundary, and an entity or an
 * id of a foreign module as a component would hand the association graph out with it (→ ADR-0021).
 * It also carries no person and no task description — a worklog in JIRA says how much, never who
 * and never what.
 *
 * @param ticketReference the free text reference of the bookings, as {@code Timereport} stores it
 */
public record TicketDaySum(LocalDate workDate, String ticketReference, long minutes) {

}
