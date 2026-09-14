package org.tb.budget.viewhelper;

import java.util.List;
import org.tb.budget.domain.AppliedRate;
import org.tb.budget.domain.AppliedRates;
import org.tb.budget.domain.AssignedBooking;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;

/**
 * One booking as the budget detail view lists it (#912, → ADR-0017): the columns a person needs to
 * decide whether this booking belongs to this plan, and since #964 the two rates that apply to it.
 *
 * <p>The rates are handed in, not fetched: they come out of the same pass that builds the
 * "Mitarbeitende" card, so a row and the card cannot name different rates for the same work. No
 * amounts here — what the work costs and earns is the controlling's business; this column says
 * which rate applies, and where none does.
 *
 * <p>Formatting only. The order of the rows is the one the query delivered — youngest first (#997)
 * — and is deliberately not re-established here: a second sort would be a second opinion about an
 * order the database already holds.
 */
public record AssignedTimereportViewHelper(
    long id,
    String day,
    String suborderSign,
    String employeeSign,
    String duration,
    String taskDescription,
    AppliedRate rate) {

    /** The same day format the rest of the budget section uses. */
    private static final String DAY_FORMAT = "dd.MM.yyyy";

    public static List<AssignedTimereportViewHelper> from(List<AssignedBooking> bookings,
                                                          AppliedRates rates) {
        return bookings.stream().map(booking -> from(booking, rates.of(booking.id()))).toList();
    }

    public static AssignedTimereportViewHelper from(AssignedBooking booking, AppliedRate rate) {
        return new AssignedTimereportViewHelper(
            booking.id(),
            DateUtils.format(booking.day(), DAY_FORMAT),
            booking.suborderSign(),
            booking.employeeSign(),
            DurationUtils.format(booking.duration()),
            booking.taskDescription(),
            rate);
    }

}
