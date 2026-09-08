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

    public BudgetBookingCounts totalAssigned() {
        return total(BudgetBackfillOrderResult::assigned);
    }

    public BudgetBookingCounts totalAmbiguous() {
        return total(BudgetBackfillOrderResult::ambiguous);
    }

    public BudgetBookingCounts totalWithoutPlan() {
        return total(BudgetBackfillOrderResult::withoutPlan);
    }

    public BudgetBookingCounts totalAlreadyAssigned() {
        return total(BudgetBackfillOrderResult::alreadyAssigned);
    }

    private BudgetBookingCounts total(Function<BudgetBackfillOrderResult, BudgetBookingCounts> part) {
        return orders.stream().map(part).reduce(BudgetBookingCounts.NONE, BudgetBookingCounts::plus);
    }

}
