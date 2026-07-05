package org.tb.budget.domain;

import java.util.List;

public record BudgetControllingResult(
    BudgetControllingRow total,
    List<BudgetControllingRow> suborderRows
) {}
