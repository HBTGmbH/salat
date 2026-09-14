package org.tb.budget.domain;

import java.time.Duration;
import java.util.List;

/**
 * What the budget detail page needs about the bookings of a plan (#997): the youngest ones it
 * renders, and the figures of the <em>whole</em> period above them.
 *
 * <p>The two are deliberately separate. The list is capped, the figures are not — deriving them
 * from the capped list would make a plan with more bookings than the cap report too few hours, which
 * is exactly the kind of quiet wrongness the cap's own hint warns about.
 */
public record AssignedBookings(List<AssignedBooking> newest, long count, Duration totalDuration) {

    public static final AssignedBookings NONE = new AssignedBookings(List.of(), 0, Duration.ZERO);

    /** Whether the period holds more bookings than {@code newest} shows. */
    public boolean truncated() {
        return count > newest.size();
    }

}
