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
    double utilizationPercent,
    /**
     * How far the plan has come by its own progress mode — elapsed working time or the last agreed
     * scope entry. {@code null} for a plan that has no progress mode, and for one that runs
     * open-ended: there is no share of a running time without an end.
     */
    Double progressPercent,
    ProgressStatus progressStatus
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

    /** A progress is known for this plan, so its status says something. */
    public boolean hasProgress() {
        return progressPercent != null && progressStatus != null
            && progressStatus != ProgressStatus.UNKNOWN;
    }

    /**
     * The plan has spent noticeably more of its budget than it has come along — the warning the
     * dashboard shows next to the utilization. Independent of the alert threshold and of the budget
     * being exceeded: a plan can be behind its plan long before either of those applies.
     */
    public boolean isBehindPlan() {
        return hasProgress() && progressStatus == ProgressStatus.BEHIND;
    }
}
