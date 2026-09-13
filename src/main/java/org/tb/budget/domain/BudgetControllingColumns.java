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
    boolean bookedBeforeWindow,
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
            rows.stream().anyMatch(BudgetControllingRow::hasBookedBeforeWindow),
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
     */
    public BudgetControllingColumns withoutBudget() {
        return new BudgetControllingColumns(bookedBeforeWindow, planned, flatRate, false, false,
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
            bookedBeforeWindow || other.bookedBeforeWindow(),
            planned || other.planned(),
            flatRate || other.flatRate(),
            budget || other.budget(),
            overrun || other.overrun(),
            grossProfitMargin || other.grossProfitMargin());
    }
}
