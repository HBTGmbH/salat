package org.tb.budget.viewhelper;

import java.util.Comparator;
import java.util.List;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;

/**
 * One booking as the budget detail view lists it (#912, → ADR-0017): the columns a person needs to
 * decide whether this booking belongs to this plan.
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

    /**
     * The youngest booking first, and within a day a fixed order so the list looks the same on every
     * call — by employee sign, then suborder, then id, the last of which is unique and therefore
     * makes the order total.
     */
    private static final Comparator<TimereportDTO> NEWEST_FIRST =
        Comparator.comparing(TimereportDTO::getReferenceday, Comparator.reverseOrder())
            .thenComparing(TimereportDTO::getEmployeeSign, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TimereportDTO::getCompleteOrderSign, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TimereportDTO::getId);

    /**
     * The youngest {@code limit} bookings, youngest first (#997).
     *
     * <p>The sorting happens <em>before</em> the cap, which is the whole point: the bookings arrive
     * from {@code TimereportDAO.getTimereportsByDatesAndCustomerOrderId} ordered by employee sign
     * first, so cutting the list short without reordering it would show every booking of the
     * alphabetically first people and none at all of the rest. Sorting afterwards would merely
     * rearrange that same wrong selection.
     */
    public static List<AssignedTimereportViewHelper> newestFirst(List<TimereportDTO> reports, int limit) {
        return reports.stream().sorted(NEWEST_FIRST).limit(limit).map(AssignedTimereportViewHelper::from).toList();
    }

    public static AssignedTimereportViewHelper from(TimereportDTO report) {
        return new AssignedTimereportViewHelper(
            report.getId(),
            DateUtils.format(report.getReferenceday(), DAY_FORMAT),
            report.getCompleteOrderSign(),
            report.getEmployeeSign(),
            DurationUtils.format(report.getDuration()),
            report.getTaskdescription());
    }

}
