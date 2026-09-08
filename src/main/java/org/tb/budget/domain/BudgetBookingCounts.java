package org.tb.budget.domain;

import java.time.Duration;

/**
 * How many bookings fell into one outcome, and how many hours they carry. Used wherever bookings are
 * counted per outcome: the backfill protocol (#910) and the bulk assignment preview (#911).
 *
 * <p>The hours are reported next to the count because the count alone does not say how much is at
 * stake: a hundred quarter-hour bookings and one full week are the same number and a very different
 * problem.
 */
public record BudgetBookingCounts(int bookings, Duration hours) {

    public static final BudgetBookingCounts NONE = new BudgetBookingCounts(0, Duration.ZERO);

    /** This outcome plus one more booking of the given length. */
    public BudgetBookingCounts plus(Duration duration) {
        return new BudgetBookingCounts(bookings + 1, hours.plus(duration == null ? Duration.ZERO : duration));
    }

    /** The same outcome from somewhere else, added up for a total. */
    public BudgetBookingCounts plus(BudgetBookingCounts other) {
        return new BudgetBookingCounts(bookings + other.bookings, hours.plus(other.hours));
    }

    public boolean isEmpty() {
        return bookings == 0;
    }

}
