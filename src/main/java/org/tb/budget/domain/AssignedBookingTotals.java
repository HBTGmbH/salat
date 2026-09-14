package org.tb.budget.domain;

import java.time.Duration;

/**
 * The figures above the booking list of a budget plan (#997), counted and summed in the database
 * over the whole period rather than over the capped list.
 *
 * <p>Both fields are boxed because that is what the aggregate returns: {@code count} is a
 * {@code Long}, and {@code sum} is {@code null} — not zero — when the period holds no booking at
 * all. Normalising that here keeps the empty case out of every caller.
 */
public record AssignedBookingTotals(Long count, Long minutes) {

    public long bookings() {
        return count == null ? 0 : count;
    }

    public Duration totalDuration() {
        return Duration.ofMinutes(minutes == null ? 0 : minutes);
    }

}
