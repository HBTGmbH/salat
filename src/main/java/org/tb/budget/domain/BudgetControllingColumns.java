package org.tb.budget.domain;

import java.util.Collection;

/**
 * Which of the optional columns a controlling table shows.
 *
 * <p>The rule has always been the same: a column that would be nothing but dashes costs width in a
 * table that already has a dozen of them, so it only appears where it has something to say. What is
 * new is that three views now decide it — the sections of one order, the total over them (#779) and
 * the segment listing — and they have to agree, otherwise the same figures would sit under different
 * headers depending on where they are read.
 *
 * <p>Which rows a flag is derived from stays with the view that owns them
 * (→ {@link BudgetControllingSection#columns()}); this record only carries the answer.
 */
public record BudgetControllingColumns(
    boolean revenueBeforeWindow,
    boolean planned,
    boolean flatRate,
    boolean budget,
    boolean overrun,
    boolean grossProfitMargin
) {

    /** Nothing optional — the starting point for folding several sets into one. */
    public static final BudgetControllingColumns NONE =
        new BudgetControllingColumns(false, false, false, false, false, false);

    /**
     * The columns of a flat table, read off the lines it shows. Used where every line is of the same
     * kind — the segment listing, whose lines are all order totals. A section decides differently
     * (→ {@link BudgetControllingSection#columns()}): there the planned column describes the
     * suborder breakdown and deliberately ignores the total.
     */
    public static BudgetControllingColumns of(Collection<BudgetControllingRow> rows) {
        return new BudgetControllingColumns(
            rows.stream().anyMatch(BudgetControllingRow::hasRevenueBeforeWindow),
            rows.stream().anyMatch(BudgetControllingRow::hasPlanned),
            rows.stream().anyMatch(BudgetControllingRow::hasFlatRateRevenue),
            rows.stream().anyMatch(BudgetControllingRow::hasBudget),
            rows.stream().anyMatch(BudgetControllingRow::hasOverrun),
            rows.stream().anyMatch(BudgetControllingRow::hasGrossProfitMargin));
    }

    /**
     * The same columns without everything that answers to a budget plan.
     *
     * <p>A budget belongs to one plan with one period and one scope. Added up over the plans of an
     * order — or over the orders of a segment, unbudgeted ones among them — the sum stands for
     * nothing anybody agreed to, and a utilization or an overrun computed from it is arithmetic
     * without a subject. Aggregates therefore report what was worked, earned and cost, and leave the
     * budget where it is decided: in the section of its plan.
     *
     * <p>The revenue earned before the window goes with them. It is not a figure of its own right —
     * it exists so the budget columns can read against the whole plan while everything else stays
     * inside the period. Without those columns it is a number without a question.
     */
    public BudgetControllingColumns withoutBudget() {
        return new BudgetControllingColumns(false, planned, flatRate, false, false,
            grossProfitMargin);
    }

    /**
     * The same columns without anything a plan answers for: the budget columns and, on top of them,
     * the planned hours and their consumption.
     *
     * <p>For the segment listing (#779), which reports orders and not plans. Sollstunden come from
     * the suborders of an order and a consumption read against them says how far that one order has
     * come — next to orders that carry no plan at all, and summed over a segment, the figure invites
     * a comparison that does not exist. What the segment is read for is what was worked, earned,
     * cost and earned on top.
     */
    public BudgetControllingColumns withoutPlan() {
        return withoutBudget().withoutPlannedHours();
    }

    private BudgetControllingColumns withoutPlannedHours() {
        return new BudgetControllingColumns(revenueBeforeWindow, false, flatRate, budget, overrun,
            grossProfitMargin);
    }

    /**
     * The columns of a table made of several parts: a column appears when any part shows it. A total
     * over the sections of an order carries the figures of all of them, so it has to offer every
     * column any one of them offers.
     */
    public BudgetControllingColumns merge(BudgetControllingColumns other) {
        if (other == null) {
            return this;
        }
        return new BudgetControllingColumns(
            revenueBeforeWindow || other.revenueBeforeWindow(),
            planned || other.planned(),
            flatRate || other.flatRate(),
            budget || other.budget(),
            overrun || other.overrun(),
            grossProfitMargin || other.grossProfitMargin());
    }
}
