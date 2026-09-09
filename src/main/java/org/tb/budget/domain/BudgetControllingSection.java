package org.tb.budget.domain;

import java.util.List;
import org.tb.common.LocalDateRange;

/**
 * One evaluation of the controlling view: the plans of one period and mode, or — for
 * {@link SectionKind#UNPLANNED} — the bookings that belong to no plan at all (#913).
 *
 * <p>What a section reports about bookings happened inside the evaluated window. Its budget figure
 * does not: it is what the plans had left when the window opened, so a plan that has been running
 * for months shows the remainder rather than nothing (#916).
 */
public record BudgetControllingSection(
    SectionKind kind,
    /** The period of the plans in this section; {@code null} for UNPLANNED, where rows differ. */
    LocalDateRange period,
    List<String> budgetNames,
    List<BudgetControllingGroup> groups,
    BudgetControllingRow total,
    /** How the available budget came about; {@code null} for UNPLANNED, which has none (#917). */
    BudgetHistory history
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
