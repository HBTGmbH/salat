package org.tb.budget.viewhelper;

import java.math.BigDecimal;
import org.tb.budget.domain.BudgetControllingSection;
import org.tb.common.util.DateUtils;

/**
 * The derivation of a section's available budget, ready for the info box (#917, → ADR-0017).
 *
 * <p>Amounts stay {@code BigDecimal}: the template formats them through the same
 * {@code main.budget.format.amount} pattern as the table below, so the box cannot end up showing
 * euros in a different shape than the numbers it explains. The arithmetic happens here, not in the
 * template.
 */
public record BudgetHistoryViewHelper(
    boolean visible,
    String planPeriod,
    BigDecimal totalBudgetEuro,
    BigDecimal consumedBeforeEuro,
    BigDecimal availableAtWindowStartEuro,
    BigDecimal consumedInWindowEuro,
    BigDecimal availableAtWindowEndEuro) {

    /** The same day format the rest of the budget section uses. */
    private static final String DAY_FORMAT = "dd.MM.yyyy";

    private static final BudgetHistoryViewHelper HIDDEN = new BudgetHistoryViewHelper(
        false, null, null, null, null, null, null);

    public static BudgetHistoryViewHelper from(BudgetControllingSection section) {
        var history = section.history();
        if (history == null || !history.isWorthShowing()) {
            return HIDDEN;
        }
        var availableAtStart = history.availableAtWindowStartEuro();
        // What the window itself used up is the revenue the section reports.
        var consumedInWindow = section.total().revenueEuro() == null
            ? BigDecimal.ZERO
            : section.total().revenueEuro();
        return new BudgetHistoryViewHelper(
            true,
            DateUtils.format(history.planFrom(), DAY_FORMAT)
                + " – " + DateUtils.format(history.planUntil(), DAY_FORMAT),
            history.cumulativeEuro(),
            history.consumedBeforeEuro(),
            availableAtStart,
            consumedInWindow,
            availableAtStart.subtract(consumedInWindow));
    }

    /** Whether the plan is already spent — the box says so rather than only showing a minus sign. */
    public boolean isExhausted() {
        return visible && availableAtWindowEndEuro.signum() < 0;
    }

}
