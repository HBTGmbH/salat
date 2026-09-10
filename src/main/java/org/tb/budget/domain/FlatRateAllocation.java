package org.tb.budget.domain;

import static java.lang.Boolean.TRUE;

import java.util.Collection;
import java.util.Optional;

/**
 * Which budget plan a flat rate amount counts against (#972).
 *
 * <p>The rule is the one {@code BudgetResolver} applies to a booking: an active plan of the same
 * customer order whose validity contains the day and whose scope covers it. A flat rate needs no
 * assignment of its own — unlike a booking it is entered by the same people who plan the budget,
 * and it names its scope and its due date itself.
 *
 * <p>Where several plans qualify, none is chosen. Guessing would count the amount against a plan
 * nobody picked, and counting it against both would report it twice — so it lands in the section
 * without a budget, exactly where a booking with an ambiguous resolution lands.
 */
public final class FlatRateAllocation {

    private FlatRateAllocation() {
    }

    /** The single plan that may hold this amount, or empty when none or several qualify. */
    public static Optional<OrderBudget> uniquePlanFor(FlatRateDueAmount dueAmount,
                                                      Collection<OrderBudget> plans) {
        var covering = plans.stream().filter(plan -> covers(plan, dueAmount)).toList();
        return covering.size() == 1 ? Optional.of(covering.get(0)) : Optional.empty();
    }

    private static boolean covers(OrderBudget plan, FlatRateDueAmount dueAmount) {
        var flatRate = dueAmount.flatRate();
        return TRUE.equals(plan.getActive())
            && !dueAmount.due().isBefore(plan.getValidFrom())
            && !dueAmount.due().isAfter(plan.getValidUntil())
            && BudgetScope.covers(plan, flatRate.getCustomerorderSign(), flatRate.firstLevelSign());
    }

}
