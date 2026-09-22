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
 *
 * <p>A flat rate may name its plan outright (#1065), and that nails the ambiguous case down: the
 * named plan is taken and nothing is derived. Period and scope are not checked again — the saving
 * did that, and a due date outside the plan is therefore no special case here.
 */
public final class FlatRateAllocation {

    private FlatRateAllocation() {
    }

    /**
     * The single plan that may hold this amount, or empty when none or several qualify.
     *
     * <p>A named plan counts only while it is among the plans passed in — the active ones. A
     * deactivated plan is not evaluated, so its amounts are reported as being without a budget,
     * exactly as the bookings of a deactivated plan are (→ AGENTS.md, "Budget Assignments Follow a
     * Changed Plan"). Falling back to the derivation instead would move the amount to a plan
     * somebody else picked.
     */
    public static Optional<OrderBudget> uniquePlanFor(FlatRateDueAmount dueAmount,
                                                      Collection<OrderBudget> plans) {
        var named = dueAmount.flatRate().getOrderBudgetId();
        if (named != null) {
            return plans.stream().filter(plan -> named.equals(plan.getId())).findFirst();
        }
        var covering = plans.stream().filter(plan -> covers(plan, dueAmount)).toList();
        return covering.size() == 1 ? Optional.of(covering.get(0)) : Optional.empty();
    }

    private static boolean covers(OrderBudget plan, FlatRateDueAmount dueAmount) {
        var flatRate = dueAmount.flatRate();
        return TRUE.equals(plan.getActive())
            && !dueAmount.due().isBefore(plan.getValidFrom())
            && !dueAmount.due().isAfter(plan.getValidUntil())
            && BudgetScope.covers(plan, flatRate.getCustomerorderSign(), flatRate.getSuborderSign());
    }

}
