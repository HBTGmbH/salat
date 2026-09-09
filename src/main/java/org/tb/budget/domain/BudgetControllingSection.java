package org.tb.budget.domain;

import java.util.List;
import org.tb.common.LocalDateRange;

/**
 * One evaluation of the controlling view: the plans of one period and mode, or — for
 * {@link SectionKind#UNPLANNED} — the bookings that belong to no plan at all (#913).
 *
 * <p>The hours a section reports are the ones booked inside the evaluated window; the amounts are
 * the full figures up to its end, so the budget and its utilization read against the whole plan
 * rather than against a remainder (#917). How much was booked before the window is a column of its
 * own.
 */
public record BudgetControllingSection(
    SectionKind kind,
    /** The period of the plans in this section; {@code null} for UNPLANNED, where rows differ. */
    LocalDateRange period,
    List<String> budgetNames,
    List<BudgetControllingGroup> groups,
    BudgetControllingRow total
) {
    /** A section worth showing at all — same rule as for a row (#901). */
    public boolean hasContent() {
        return total.hasContent();
    }

    public List<BudgetControllingRow> rows() {
        return groups.stream().flatMap(g -> g.rows().stream()).toList();
    }

    public boolean hasPlannedData() {
        return rows().stream().anyMatch(BudgetControllingRow::hasPlanned);
    }

    public boolean hasProgressData() {
        return total.hasProgress() || rows().stream().anyMatch(BudgetControllingRow::hasProgress);
    }

    /** Only planned sections have a budget, and only there is a utilization meaningful. */
    public boolean hasBudgetColumn() {
        return kind != SectionKind.UNPLANNED;
    }

    public boolean hasSubtotals() {
        return groups.stream().anyMatch(BudgetControllingGroup::hasSubtotal);
    }
}
