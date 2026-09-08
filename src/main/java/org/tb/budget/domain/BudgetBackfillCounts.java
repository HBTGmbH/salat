package org.tb.budget.domain;

import java.time.Duration;

/**
 * How many bookings fell into one outcome of a backfill run, and how many hours they carry.
 *
 * <p>The hours are reported next to the count because the count alone does not say how much is at
 * stake: a hundred quarter-hour bookings and one full week are the same number and a very different
 * problem.
 */
public record BudgetBackfillCounts(int bookings, Duration hours) {

    public static final BudgetBackfillCounts NONE = new BudgetBackfillCounts(0, Duration.ZERO);

    /** This outcome plus one more booking of the given length. */
    public BudgetBackfillCounts plus(Duration duration) {
        return new BudgetBackfillCounts(bookings + 1, hours.plus(duration == null ? Duration.ZERO : duration));
    }

    /** The same outcome of another order, added up for the run total. */
    public BudgetBackfillCounts plus(BudgetBackfillCounts other) {
        return new BudgetBackfillCounts(bookings + other.bookings, hours.plus(other.hours));
    }

    public boolean isEmpty() {
        return bookings == 0;
    }

}
