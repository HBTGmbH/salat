package org.tb.budget.domain;

import java.util.List;
import java.util.function.Function;

/**
 * The protocol of one backfill run (#910), one entry per customer order that was looked at, plus
 * the run totals.
 */
public record BudgetBackfillResult(List<BudgetBackfillOrderResult> orders) {

    public BudgetBackfillResult {
        orders = List.copyOf(orders);
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public BudgetBackfillCounts totalAssigned() {
        return total(BudgetBackfillOrderResult::assigned);
    }

    public BudgetBackfillCounts totalAmbiguous() {
        return total(BudgetBackfillOrderResult::ambiguous);
    }

    public BudgetBackfillCounts totalWithoutPlan() {
        return total(BudgetBackfillOrderResult::withoutPlan);
    }

    public BudgetBackfillCounts totalAlreadyAssigned() {
        return total(BudgetBackfillOrderResult::alreadyAssigned);
    }

    private BudgetBackfillCounts total(Function<BudgetBackfillOrderResult, BudgetBackfillCounts> part) {
        return orders.stream().map(part).reduce(BudgetBackfillCounts.NONE, BudgetBackfillCounts::plus);
    }

}
