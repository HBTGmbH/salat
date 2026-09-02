package org.tb.budget.domain;

import java.util.List;
import org.tb.common.LocalDateRange;

/**
 * One evaluation of the controlling view. Each section covers exactly one budget period — or, for
 * {@link SectionKind#UNPLANNED}, the time no plan covers — so within a section the period <em>is</em>
 * the coverage. That is what makes a separate "covered revenue" unnecessary: everything a section
 * reports happened inside it.
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

    /** Unplanned rows each cover their own gaps, so there the period belongs in a column. */
    public boolean hasPeriodColumn() {
        return kind == SectionKind.UNPLANNED;
    }

    public boolean hasSubtotals() {
        return groups.stream().anyMatch(BudgetControllingGroup::hasSubtotal);
    }
}
