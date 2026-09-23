package org.tb.dailyreport.controller;

import java.time.Duration;

/**
 * A booking of an earlier day as the dropdown "Vorherige übernehmen" offers it (#1017). It carries
 * the same four values a favourite does plus the employee order it is booked on — unlike a
 * favourite there is no stored record to point at, so everything the new booking needs travels
 * with the offer itself.
 *
 * <p>{@code label} is the order and suborder the booking was made on. It is the line that makes
 * the offer readable at all: the list spans every suborder, so the comment alone does not say what
 * is being booked.
 */
record PreviousBookingView(long employeeorderId, String label, String comment,
    String ticketReference, Duration duration) {

}
