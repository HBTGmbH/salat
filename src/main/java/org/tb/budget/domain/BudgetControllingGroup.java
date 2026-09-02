package org.tb.budget.domain;

import java.util.List;

/**
 * Rows belonging to one budget plan within a section, plus their subtotal.
 *
 * <p>Bookings live on suborders of any depth while plans only live on the first level, so the rows
 * of a group are the whole subtree below the suborder the plan refers to. The subtotal is where that
 * plan's budget and utilization appear — the individual rows carry no budget of their own.
 *
 * <p>For an order-level or unplanned section there is a single group without a subtotal: the
 * section total already plays that role.
 */
public record BudgetControllingGroup(
    String sign,
    String label,
    List<BudgetControllingRow> rows,
    BudgetControllingRow subtotal
) {
    public boolean hasSubtotal() {
        return subtotal != null;
    }
}
