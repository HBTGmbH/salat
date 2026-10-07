package de.hbt.salat.budget.domain;

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
    /**
     * The plan this group stands for, so the header can link to it. {@code null} on the group of an
     * {@link SectionKind#UNPLANNED} section — those bookings answer to no plan.
     */
    Long budgetId,
    List<BudgetControllingRow> rows,
    BudgetControllingRow subtotal,
    /**
     * How far the plan of this group has come, and where that puts it against its budget (#989).
     * Both belong to the plan rather than to any of its rows, which is why they live here and not on
     * a row: a group is exactly one plan, whether the section reports it through a subtotal or
     * through the section total.
     */
    Double progressPercent,
    ProgressStatus progressStatus,
    /**
     * The calculation and the hourly rates of a fixed-price plan (#1405); {@code null} for every
     * other plan and for the group of an {@link SectionKind#UNPLANNED} section.
     */
    FixedPriceEvaluation fixedPrice
) {

    /** A group without a fixed price — every plan that is not one, and the unplanned section. */
    public BudgetControllingGroup(String sign, String label, Long budgetId, List<BudgetControllingRow> rows,
                                  BudgetControllingRow subtotal, Double progressPercent,
                                  ProgressStatus progressStatus) {
        this(sign, label, budgetId, rows, subtotal, progressPercent, progressStatus, null);
    }

    public boolean hasFixedPrice() {
        return fixedPrice != null;
    }

    public boolean hasSubtotal() {
        return subtotal != null;
    }

    /** Whether there is a plan to link to — an unplanned section has none. */
    public boolean hasBudgetPlan() {
        return budgetId != null;
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
