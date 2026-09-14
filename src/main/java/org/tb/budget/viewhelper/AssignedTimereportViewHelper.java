package org.tb.budget.viewhelper;

import java.util.List;
import org.tb.budget.domain.AssignedBooking;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;

/**
 * One booking as the budget detail view lists it (#912, → ADR-0017): the columns a person needs to
 * decide whether this booking belongs to this plan.
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
    String taskDescription) {

    /** The same day format the rest of the budget section uses. */
    private static final String DAY_FORMAT = "dd.MM.yyyy";

    public static List<AssignedTimereportViewHelper> from(List<AssignedBooking> bookings) {
        return bookings.stream().map(AssignedTimereportViewHelper::from).toList();
    }

    public static AssignedTimereportViewHelper from(AssignedBooking booking) {
        return new AssignedTimereportViewHelper(
            booking.id(),
            DateUtils.format(booking.day(), DAY_FORMAT),
            booking.suborderSign(),
            booking.employeeSign(),
            DurationUtils.format(booking.duration()),
            booking.taskDescription());
    }

}
