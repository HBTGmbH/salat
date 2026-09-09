package org.tb.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Where a section's available budget comes from (#917).
 *
 * <p>Since #916 the evaluation measures against the budget left when the window opened. That figure
 * says nothing on its own: 40.000 EUR left in April is a different message depending on whether the
 * plan is worth 50.000 or 500.000. This carries the two inputs it was derived from, so the
 * derivation can be shown instead of asserted.
 *
 * @param cumulativeEuro      granted up to the end of the window, adjustments from before it included
 * @param consumedBeforeEuro  used up before the window opened
 * @param planFrom            earliest start among the section's plans
 * @param planUntil           latest end among them
 * @param startedBeforeWindow whether any of them began before the window
 */
public record BudgetHistory(
    BigDecimal cumulativeEuro,
    BigDecimal consumedBeforeEuro,
    LocalDate planFrom,
    LocalDate planUntil,
    boolean startedBeforeWindow) {

    /** The figure the window is measured against — deliberately not floored at zero (#916). */
    public BigDecimal availableAtWindowStartEuro() {
        return cumulativeEuro.subtract(consumedBeforeEuro);
    }

    /**
     * Whether the derivation is worth showing at all. For a plan that starts inside the window and
     * has consumed nothing beforehand, total and remainder are the same number and the box would
     * only repeat the table.
     */
    public boolean isWorthShowing() {
        return startedBeforeWindow || consumedBeforeEuro.signum() != 0;
    }

}
