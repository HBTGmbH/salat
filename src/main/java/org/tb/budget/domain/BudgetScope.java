package org.tb.budget.domain;

/**
 * What a budget plan covers. A plan is either order-wide or lives on a suborder of any depth
 * (#1004), and it then covers that suborder <em>and everything below it</em> — bookings, flat rates
 * and planned hours alike.
 *
 * <p>The comparison is a prefix comparison on the complete order sign, with no notion of a level in
 * it: {@code AB1234/01} covers {@code AB1234/01/A}, and the appended slash is what keeps it from
 * covering {@code AB1234/010}. For the plans on the first suborder level that used to be the only
 * allowed ones this says exactly what the old rule said — "the booking's first level ancestor is the
 * plan's suborder" and "the booking lies in the plan's subtree" coincide for level 1 — so the
 * coverage of every existing plan is unchanged.
 *
 * <p>The stored assignment ({@code BudgetResolver}), the controlling
 * ({@code BudgetControllingService}) and the flat rates ({@link FlatRateAllocation}) all resolve
 * coverage through this class. Spelling it out a second time somewhere is what produced #931.
 *
 * <p><strong>The level is a validation rule, not a coverage rule.</strong> All active plans of a
 * customer order that are valid at the same time have to sit on the same level (→
 * {@code OrderBudgetService}); {@link #levelOf(String)} is what that check reads. What double
 * counting technically requires is only "no plan lies in the subtree of another", which would allow
 * {@code AB1234/01} next to {@code AB1234/02/B} because the two are disjoint. That weaker rule costs
 * the same code and is deliberately not the one in force: equal levels are what guarantees a
 * controlling section a flat, mutually comparable set of rows, and the rule fits in one sentence.
 */
public final class BudgetScope {

    private BudgetScope() {
    }

    /** {@code null} and blank both mean "the whole customer order", as everywhere else. */
    public static boolean isOrderWide(String suborderSign) {
        return suborderSign == null || suborderSign.isBlank();
    }

    /**
     * Whether the plan covers something booked or agreed on the given customer order and suborder.
     * The suborder is named by its complete order sign ({@code Suborder#getCompleteOrderSign()}); for
     * an order-wide plan it is irrelevant and may be {@code null} — the caller then does not have to
     * resolve the suborder at all.
     */
    public static boolean covers(OrderBudget plan, String customerorderSign, String suborderSign) {
        if (!plan.getCustomerorderSign().equals(customerorderSign)) {
            return false;
        }
        return coversSign(plan.getSuborderSign(), suborderSign);
    }

    /**
     * The subtree comparison itself: the plan's suborder, or anything below it. The trailing slash
     * is not cosmetic, it is the boundary — without it {@code AB1234/010} would fall under
     * {@code AB1234/01}. The same pattern decides the selection of the bulk assignment
     * ({@code BulkAssignmentData.coversSuborder}).
     */
    private static boolean coversSign(String planSuborderSign, String suborderSign) {
        if (isOrderWide(planSuborderSign)) {
            return true;
        }
        return suborderSign != null
            && (suborderSign.equals(planSuborderSign)
                || suborderSign.startsWith(planSuborderSign + "/"));
    }

    /**
     * Which level a scope sits on: 0 for an order-wide scope, 1 for a direct suborder of the customer
     * order, 2 for its children and so on — the number of slashes in the complete order sign.
     *
     * <p>Levels are only ever compared within one customer order, so a customer order sign that
     * contained a slash itself would shift both sides by the same amount and leave the comparison
     * intact.
     */
    public static int levelOf(String suborderSign) {
        if (isOrderWide(suborderSign)) {
            return 0;
        }
        return (int) suborderSign.chars().filter(c -> c == '/').count();
    }

}
