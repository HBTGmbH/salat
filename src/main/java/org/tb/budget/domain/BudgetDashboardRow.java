package org.tb.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BudgetDashboardRow(
    long budgetId,
    String budgetName,
    String customerorderSign,
    String customerorderName,
    LocalDate validFrom,
    LocalDate validUntil,
    /**
     * The last day the figures of this row cover — the plan's end, or today when the plan runs on
     * (#972). The row shows a plan's whole validity but reports where it stands now, so the two
     * dates are not the same thing.
     */
    LocalDate evaluatedUntil,
    BigDecimal budgetEuro,
    BigDecimal coveredRevenueEuro,
    Integer alertThresholdPercent,
    double utilizationPercent
) {
    public boolean hasBudget() { return budgetEuro != null && budgetEuro.signum() != 0; }
    public boolean hasAlertThreshold() { return alertThresholdPercent != null; }
    /**
     * The plan has reached its configured alert threshold. Purely a warning, and only meaningful
     * when a threshold is configured at all — the field is optional. Without a budget amount there
     * is nothing to be a percentage of, so such a plan is never above its threshold.
     */
    public boolean isAboveThreshold() {
        return hasBudget() && hasAlertThreshold() && utilizationPercent >= alertThresholdPercent;
    }

    /**
     * The budget is used up: the revenue it has to cover exceeds it. Independent of the alert
     * threshold, so a plan without one is still flagged once it goes over.
     */
    public boolean isOverBudget() { return hasBudget() && utilizationPercent > 100.0; }

    public double progressBarPercent() { return Math.min(utilizationPercent, 100.0); }
}
