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
    BudgetControllingRow subtotal,
    /**
     * How far the plan of this group has come, and where that puts it against its budget (#989).
     * Both belong to the plan rather than to any of its rows, which is why they live here and not on
     * a row: a group is exactly one plan, whether the section reports it through a subtotal or
     * through the section total.
     */
    Double progressPercent,
    ProgressStatus progressStatus
) {
    public boolean hasSubtotal() {
        return subtotal != null;
    }

    /** A progress worth showing — a plan without a progress mode has none. */
    public boolean hasProgress() {
        return progressPercent != null;
    }

    /**
     * Whether the progress can be judged against the budget. It cannot without a budget to spend, so
     * such a plan reports how far it has come but no verdict — reported apart from
     * {@link #hasProgress()} rather than suppressing the progress along with the verdict.
     */
    public boolean hasProgressStatus() {
        return hasProgress() && progressStatus != null && progressStatus != ProgressStatus.UNKNOWN;
    }

    public String progressFormatted() {
        return hasProgress() ? String.format("%.1f", progressPercent) + " %" : "—";
    }
}
