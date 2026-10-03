package de.hbt.salat.budget.domain;

import static java.lang.Boolean.TRUE;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

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
     * <p>A named plan counts only while it is among the plans passed in, and it counts whether or
     * not it is active: the controlling passes every plan of the order, so an amount follows a
     * deactivated plan into its section exactly as the bookings assigned to it do (#1217). The
     * dashboard passes the active plans only, and there the amount holds nothing. Falling back to
     * the derivation instead would move the amount to a plan somebody else picked.
     *
     * <p>The derivation itself only ever picks an active plan. Letting a deactivated one qualify
     * would make amounts ambiguous that are unambiguous today.
     */
    /**
     * @param positionOf where a flat rate sits in the order tree (#1205); empty for one whose suborder
     *                   no longer exists, which then counts against no derived plan
     */
    public static Optional<OrderBudget> uniquePlanFor(FlatRateDueAmount dueAmount,
                                                      Collection<OrderBudget> plans,
                                                      Function<OrderFlatRate, Optional<OrderPosition>> positionOf) {
        var named = dueAmount.flatRate().getOrderBudgetId();
        if (named != null) {
            return plans.stream().filter(plan -> named.equals(plan.getId())).findFirst();
        }
        var position = positionOf.apply(dueAmount.flatRate()).orElse(null);
        var covering = plans.stream().filter(plan -> covers(plan, dueAmount, position)).toList();
        return covering.size() == 1 ? Optional.of(covering.get(0)) : Optional.empty();
    }

    private static boolean covers(OrderBudget plan, FlatRateDueAmount dueAmount, OrderPosition position) {
        return TRUE.equals(plan.getActive())
            && !dueAmount.due().isBefore(plan.getValidFrom())
            && !dueAmount.due().isAfter(plan.getValidUntil())
            && BudgetScope.covers(plan, position);
    }

}
