package org.tb.budget.domain;

import org.tb.order.domain.Suborder;

/**
 * What a budget plan covers. A plan is either order-wide or lives on a first level suborder (#905),
 * while bookings happen anywhere below that level. A booking's scope therefore has to be compared
 * against its first level ancestor, not against its own suborder.
 *
 * <p><b>Divergence from the derived coverage.</b> {@code BudgetControllingService.firstLevelSignOf}
 * carries the same name and the same documented intent, but resolves
 * {@code withParents().get(0)} — and {@code withParents()} returns the suborder <em>itself</em>
 * first, so it yields the booking's own complete order sign instead of its first level ancestor.
 * A plan on {@code CO/01} therefore does not cover a booking on {@code CO/01/02} there, although
 * the comment at its call site says it should. That is a defect of the derived coverage, tracked
 * separately; this class resolves the ancestor as intended and is not bug-compatible with it. The
 * derivation disappears with #913, which is what makes the two definitions safe to differ in the
 * meantime.
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
