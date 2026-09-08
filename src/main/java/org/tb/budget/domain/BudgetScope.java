package org.tb.budget.domain;

import org.tb.order.domain.Suborder;

/**
 * What a budget plan covers. A plan is either order-wide or lives on a first level suborder (#905),
 * while bookings happen anywhere below that level. A booking's scope therefore has to be compared
 * against its first level ancestor, not against its own suborder.
 *
 * <p>Both the stored assignment and the derived coverage in {@code BudgetControllingService} resolve
 * the scope through this class, so the two cannot drift apart while the derivation still exists
 * (it disappears with #913). Note the direction {@code withParents()} runs in: it returns the
 * suborder <em>itself</em> first and the ancestor last. Reading {@code get(0)} instead of
 * {@code getLast()} yields the booking's own sign, which is exactly the defect #931 fixed.
 */
public final class BudgetScope {

    private BudgetScope() {
    }

    /** {@code null} and blank both mean "the whole customer order", as everywhere else. */
    public static boolean isOrderWide(String suborderSign) {
        return suborderSign == null || suborderSign.isBlank();
    }

    /**
     * The complete order sign of the suborder's first level ancestor, or its own if it already is
     * one. {@code withParents()} runs from the suborder up to the root, so the ancestor is its last
     * element.
     */
    public static String firstLevelSignOf(Suborder suborder) {
        return suborder.withParents().getLast().getCompleteOrderSign();
    }

    /**
     * Whether the plan covers a booking on the given customer order whose first level ancestor
     * carries {@code firstLevelSign}. For an order-wide plan the sign is irrelevant and may be
     * {@code null} — the caller then does not have to resolve the suborder at all.
     */
    public static boolean covers(OrderBudget plan, String customerorderSign, String firstLevelSign) {
        if (!plan.getCustomerorderSign().equals(customerorderSign)) {
            return false;
        }
        return isOrderWide(plan.getSuborderSign()) || plan.getSuborderSign().equals(firstLevelSign);
    }

}
