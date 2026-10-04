package de.hbt.salat.dailyreport.domain;

import java.util.List;

/**
 * What an earlier booking on the same suborder offers a new one for reuse: its comment and the
 * ticket references it was booked against (#1029, #1326). The references belong to the comment — a
 * comment written for a ticket usually names exactly that ticket — so they travel together rather
 * than the comment alone.
 *
 * <p>Both parts identify the entry: the same comment booked against two tickets is two entries, and
 * only the references tell them apart.
 */
public record RecentBooking(String comment, List<String> ticketReferences) {

  public RecentBooking {
    ticketReferences = ticketReferences == null ? List.of() : List.copyOf(ticketReferences);
  }
}
