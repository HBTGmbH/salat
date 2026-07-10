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
}
