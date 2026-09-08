package org.tb.budget.viewhelper;

import java.util.List;
import org.tb.budget.domain.BudgetBackfillCounts;
import org.tb.budget.domain.BudgetBackfillOrderResult;
import org.tb.budget.domain.BudgetBackfillResult;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;

/**
 * One line of the backfill protocol (#910), already formatted for the table — per customer order or
 * as the run total (→ ADR-0017).
 *
 * <p>The same shape serves both, so the footer row needs no second template block: for the total
 * the label is supplied by the caller and there is no period, because the examined period differs
 * per order and summing it would claim a span that was never looked at as a whole.
 */
public record BudgetBackfillRowViewHelper(
    String label,
    String description,
    String period,
    int assignedBookings,
    String assignedHours,
    int ambiguousBookings,
    String ambiguousHours,
    int withoutPlanBookings,
    String withoutPlanHours,
    int alreadyAssignedBookings,
    String alreadyAssignedHours) {

    public static List<BudgetBackfillRowViewHelper> from(BudgetBackfillResult result) {
        return result.orders().stream().map(BudgetBackfillRowViewHelper::from).toList();
    }

    /** The same day format the rest of the budget section uses (see {@code controlling.html}). */
    private static final String DAY_FORMAT = "dd.MM.yyyy";

    public static BudgetBackfillRowViewHelper from(BudgetBackfillOrderResult order) {
        return new BudgetBackfillRowViewHelper(
            order.customerorderSign(),
            order.customerorderDescription(),
            DateUtils.format(order.examinedFrom(), DAY_FORMAT)
                + " – " + DateUtils.format(order.examinedUntil(), DAY_FORMAT),
            order.assigned().bookings(), hours(order.assigned()),
            order.ambiguous().bookings(), hours(order.ambiguous()),
            order.withoutPlan().bookings(), hours(order.withoutPlan()),
            order.alreadyAssigned().bookings(), hours(order.alreadyAssigned()));
    }

    /** The run total. The label is passed in because only the caller can translate it. */
    public static BudgetBackfillRowViewHelper totals(BudgetBackfillResult result, String label) {
        return new BudgetBackfillRowViewHelper(
            label, null, null,
            result.totalAssigned().bookings(), hours(result.totalAssigned()),
            result.totalAmbiguous().bookings(), hours(result.totalAmbiguous()),
            result.totalWithoutPlan().bookings(), hours(result.totalWithoutPlan()),
            result.totalAlreadyAssigned().bookings(), hours(result.totalAlreadyAssigned()));
    }

    /** A dash rather than {@code 0:00} — an outcome that did not occur should not read as a number. */
    private static String hours(BudgetBackfillCounts counts) {
        return counts.isEmpty() ? "—" : DurationUtils.format(counts.hours());
    }

}
