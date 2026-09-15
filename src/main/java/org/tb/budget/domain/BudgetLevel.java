package org.tb.budget.domain;

/**
 * How a customer order is budgeted at a point in time, and on which level (#1004): the mode plus the
 * suborder level the plans in force sit on — 0 for an order-wide plan, 1 for a direct suborder of
 * the order, 2 for its children and so on.
 *
 * <p>The form shows it, because the level is what the next plan has to match. "Per suborder" alone
 * stopped being enough the moment a plan could live on any level.
 */
public record BudgetLevel(BudgetMode mode, int level) {

    /** No active plan is in force — the order is not budgeted at this point in time. */
    public static final BudgetLevel NONE = new BudgetLevel(BudgetMode.NONE, 0);

    /** The level a plan with this scope puts the order on. */
    public static BudgetLevel of(String suborderSign) {
        var level = BudgetScope.levelOf(suborderSign);
        return new BudgetLevel(level == 0 ? BudgetMode.ORDER_WIDE : BudgetMode.PER_SUBORDER, level);
    }

    /** Whether the level is one of the suborder levels — the only case where naming it says anything. */
    public boolean perSuborder() {
        return mode == BudgetMode.PER_SUBORDER;
    }

}
