package org.tb.budget.viewhelper;

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

    public static List<AssignedTimereportViewHelper> from(List<TimereportDTO> reports) {
        return reports.stream().map(AssignedTimereportViewHelper::from).toList();
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
