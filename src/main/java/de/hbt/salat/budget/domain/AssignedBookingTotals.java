package de.hbt.salat.budget.domain;

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

    /**
     * The shape the query builds: the time surcharge credit is summed separately and counts as part of
     * the hours (#1470).
     */
    public AssignedBookingTotals(Long count, Long minutes, Long surchargeMinutes) {
        this(count, minutes == null && surchargeMinutes == null ? null
            : (minutes == null ? 0 : minutes) + (surchargeMinutes == null ? 0 : surchargeMinutes));
    }

    public long bookings() {
        return count == null ? 0 : count;
    }

    public Duration totalDuration() {
        return Duration.ofMinutes(minutes == null ? 0 : minutes);
    }

}
