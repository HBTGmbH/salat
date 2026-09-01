package org.tb.budget.domain;

import java.util.List;

public record BudgetControllingResult(
    BudgetControllingRow total,
    List<BudgetControllingRow> suborderRows,
    boolean forecastAvailable
) {
    public boolean hasProgressData() {
        return total.hasProgress() || suborderRows.stream().anyMatch(BudgetControllingRow::hasProgress);
    }

    /**
     * Whether any suborder carries planned hours. Without them the planned-hours column and the
     * consumption derived from it would be nothing but dashes, so both are left out entirely.
     */
    public boolean hasPlannedData() {
        return suborderRows.stream().anyMatch(BudgetControllingRow::hasPlanned);
    }
}
