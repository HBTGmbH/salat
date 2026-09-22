package org.tb.budget.domain;

import java.time.LocalDate;
import java.util.Collection;
import org.tb.common.util.SqlLikePattern;

/**
 * Which budget plans a customer rate or a flat rate may be bound to (#1065).
 *
 * <p>A binding is offered only where it can ever resolve. The same thinking as
 * {@code OrderPricingService.checkSuborderPatternMatches}: a combination that provably applies to
 * nothing is refused when it is written, not merely left out of a select — a record that earns
 * nothing looks exactly like one that earns something until a total says otherwise.
 *
 * <p>Three conditions, and the service tells the first two apart so the message can say which one
 * was violated:
 * <ol>
 *   <li>the plan belongs to the same customer order,</li>
 *   <li>the scopes intersect — some suborder lies both in what the record covers and in what the
 *       plan covers,</li>
 *   <li>the validities overlap, or the rate applies on no single day of the plan.</li>
 * </ol>
 *
 * <p>Whether the plan is <em>active</em> is deliberately not part of this. It decides what a select
 * offers, not what may be stored: a plan that is deactivated after the fact must not make the rate
 * it hangs off uneditable, and the rate then behaves like the bookings of that plan — reported
 * without a budget, not repriced.
 */
public final class OrderBudgetBinding {

    private OrderBudgetBinding() {
    }

    /**
     * Whether the plan can meet a rate whose suborder is a {@code LIKE} pattern.
     *
     * <p>Two cases need no suborder at all, and skipping them is not an optimisation but the only
     * correct answer: an order-wide rate applies everywhere in the order and therefore meets every
     * plan of it, and an order-wide plan covers everything of the order and therefore meets every
     * rate of it. Asking the suborders in those cases would reject the pairing on an order that has
     * no suborders yet, and would also let the hidden ones — which this list leaves out, as every
     * other suborder list does — decide a commercial condition.
     *
     * @param suborderSigns the complete order signs of the order's suborders
     */
    public static boolean scopeMeetsPattern(OrderBudget plan, String customerorderSign,
                                            String suborderPattern, Collection<String> suborderSigns) {
        if (!sameOrder(plan, customerorderSign)) {
            return false;
        }
        if (isBlank(suborderPattern) || BudgetScope.isOrderWide(plan.getSuborderSign())) {
            return true;
        }
        var pattern = SqlLikePattern.startingWith(suborderPattern);
        return suborderSigns.stream().anyMatch(sign ->
            pattern.matches(sign + "/") && BudgetScope.covers(plan, customerorderSign, sign));
    }

    /**
     * Whether the plan can meet a record naming one concrete suborder — a flat rate, which spreads
     * over nothing and therefore needs no pattern (→ {@link OrderFlatRate}).
     */
    public static boolean scopeMeetsSuborder(OrderBudget plan, String customerorderSign,
                                             String suborderSign) {
        return sameOrder(plan, customerorderSign)
            && BudgetScope.covers(plan, customerorderSign, suborderSign);
    }

    /**
     * Whether plan and record share at least one day. An open rate end arrives here as the sentinel
     * {@code 31.12.2999}, so there is no case of its own for it.
     */
    public static boolean periodsOverlap(OrderBudget plan, LocalDate from, LocalDate until) {
        return !plan.getValidFrom().isAfter(until) && !plan.getValidUntil().isBefore(from);
    }

    private static boolean sameOrder(OrderBudget plan, String customerorderSign) {
        return plan.getCustomerorderSign().equals(customerorderSign);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
