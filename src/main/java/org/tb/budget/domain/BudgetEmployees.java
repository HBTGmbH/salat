package org.tb.budget.domain;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * The "Mitarbeitende" card of a budget plan (#964): one row per person who booked in the period,
 * the person with the largest share first, plus the hours the plan has no rate for.
 *
 * <p>The three sums are what makes the card worth reading at a glance — how much of the plan enters
 * the margin with 0 EUR cost, how much with 0 EUR revenue, and how much is not billed on purpose.
 * They are summed once here rather than in the template, which would ask for them repeatedly.
 *
 * <p>{@code costsIncluded} is false for everybody but managers. The card then has no cost side at
 * all — not an empty one: {@code durationWithoutCost} stays zero, because a page that does not
 * report costs must not read as a page on which no cost rate applies.
 */
public record BudgetEmployees(List<BudgetEmployee> rows, boolean costsIncluded,
                              Duration durationWithoutCost, Duration durationWithoutPrice,
                              Duration durationNotInvoiceable) {

    public static BudgetEmployees of(List<BudgetEmployee> rows, boolean costsIncluded) {
        return new BudgetEmployees(rows, costsIncluded,
            sum(rows, BudgetEmployee::durationWithoutCost),
            sum(rows, BudgetEmployee::durationWithoutPrice),
            sum(rows, BudgetEmployee::durationNotInvoiceable));
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** Whether anything is worth naming below the table at all. */
    public boolean hasFindings() {
        return !durationWithoutCost.isZero() || !durationWithoutPrice.isZero()
            || !durationNotInvoiceable.isZero();
    }

    private static Duration sum(List<BudgetEmployee> rows, Function<BudgetEmployee, Duration> of) {
        return rows.stream().map(of).reduce(Duration.ZERO, Duration::plus);
    }

}
