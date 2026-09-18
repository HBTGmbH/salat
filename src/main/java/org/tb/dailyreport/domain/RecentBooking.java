package org.tb.dailyreport.domain;

/**
 * What an earlier booking on the same suborder offers a new one for reuse: its comment and the
 * ticket reference it was booked against (#1029). The reference belongs to the comment — a comment
 * written for a ticket usually names exactly that ticket — so the two travel together rather than
 * the comment alone.
 *
 * <p>Both parts identify the entry: the same comment booked against two tickets is two entries, and
 * only the reference tells them apart.
 */
public record RecentBooking(String comment, String ticketReference) {

}
