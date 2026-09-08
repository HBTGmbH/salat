package org.tb.budget.viewhelper;

import java.util.List;
import org.tb.budget.domain.BudgetBookingCounts;
import org.tb.budget.domain.BulkAssignmentPreview;
import org.tb.common.util.DurationUtils;

/**
 * The bulk assignment preview as the panel shows it (#911, → ADR-0017): one line per outcome plus
 * the line that says what the button will actually do.
 *
 * <p>Lines carry their i18n key rather than a translated label, so the template resolves them and
 * the view helper stays free of the message source.
 */
public record BulkAssignmentPreviewViewHelper(List<Line> lines, Line affected, boolean empty) {

    /** One outcome of the selection. */
    public record Line(String labelKey, int bookings, String hours) {}

    public static BulkAssignmentPreviewViewHelper from(BulkAssignmentPreview preview, boolean includeAssigned) {
        var lines = List.of(
            line("main.bulkassignment.preview.unassigned", preview.unassigned()),
            line("main.bulkassignment.preview.assignedelsewhere", preview.assignedElsewhere()),
            line("main.bulkassignment.preview.alreadyontarget", preview.alreadyOnTarget()),
            line("main.bulkassignment.preview.notassignable", preview.notAssignable()));
        return new BulkAssignmentPreviewViewHelper(lines,
            line("main.bulkassignment.preview.affected", preview.affected(includeAssigned)),
            preview.isEmpty());
    }

    private static Line line(String labelKey, BudgetBookingCounts counts) {
        return new Line(labelKey, counts.bookings(),
            counts.isEmpty() ? "—" : DurationUtils.format(counts.hours()));
    }

}
