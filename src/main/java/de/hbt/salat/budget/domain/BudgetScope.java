package de.hbt.salat.budget.domain;

/**
 * What a budget plan covers. A plan is either order-wide or lives on a suborder of any depth
 * (#1004), and it then covers that suborder <em>and everything below it</em> — bookings, flat rates
 * and planned hours alike.
 *
 * <p>The comparison runs on ids since #1205: the plan names its customer order and suborder by id,
 * and what it is compared with comes as an {@link OrderPosition} — the customer order and the path of
 * suborder ids down to the one meant, read from the current tree. "In the subtree" is "the plan's
 * suborder is on that path". Before, it was a prefix comparison on the complete order sign; that
 * said the same as long as no sign changed and no suborder moved, and nothing at all afterwards.
 *
 * <p>The stored assignment ({@code BudgetResolver}), the controlling
 * ({@code BudgetControllingService}) and the flat rates ({@link FlatRateAllocation}) all resolve
 * coverage through this class. Spelling it out a second time somewhere is what produced #931.
 *
 * <p>A plan the migration could not resolve ({@code OrderBudget#isUnresolved()}) covers nothing. In
 * particular a plan whose suborder did not resolve does not cover the whole order — that would count
 * the bookings of every other suborder against it.
 *
 * <p><strong>The level is a validation rule, not a coverage rule.</strong> All active plans of a
 * customer order that are valid at the same time have to sit on the same level (→
 * {@code OrderBudgetService}); {@link OrderPosition#level()} is what that check reads. What double
 * counting technically requires is only "no plan lies in the subtree of another", which would allow
 * {@code AB1234/01} next to {@code AB1234/02/B} because the two are disjoint. That weaker rule costs
 * the same code and is deliberately not the one in force: equal levels are what guarantees a
 * controlling section a flat, mutually comparable set of rows, and the rule fits in one sentence.
 */
public final class BudgetScope {

    private BudgetScope() {
    }

    /**
     * Whether a record names no suborder and therefore applies to the whole customer order. A record
     * with a suborder id names one. Without an id the stored sign decides, because that is what tells
     * "no suborder" apart from "a suborder the migration could not resolve" — the only read of the
     * sign column left, which otherwise just mirrors the suborder for reports (#1212). {@code null}
     * and blank both mean "the whole customer order", as everywhere else.
     */
    public static boolean isOrderWide(Long suborderId, String suborderSign) {
        return suborderId == null && (suborderSign == null || suborderSign.isBlank());
    }

    /** Whether the plan covers something booked or agreed at that position of the order tree. */
    public static boolean covers(OrderBudget plan, OrderPosition position) {
        if (plan.isUnresolved() || position == null) {
            return false;
        }
        if (plan.getCustomerorderId() != position.customerorderId()) {
            return false;
        }
        return plan.isOrderWide() || position.liesWithin(plan.getSuborderId());
    }

}
