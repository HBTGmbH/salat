package de.hbt.salat.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BudgetDashboardRow(
    long budgetId,
    String budgetName,
    long customerorderId,
    /** The sign the order has today, read by id (#1212). */
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
    ProgressStatus progressStatus,
    /**
     * Whether the plan is a fixed price (#1404). Such a plan is judged by the consumption of its
     * calculated hours, not by its euro budget: the instalments fill that up on a calendar of their
     * own, whatever the work has come to.
     */
    boolean fixedPrice,
    /**
     * Booked against calculated hours, in percent, for a fixed-price plan; {@code null} for one
     * without a calculation and for every other plan (→ {@code FixedPriceCalculationService}).
     */
    Double hoursConsumedPercent
) {

    /** A row of a plan that is not a fixed price. */
    public BudgetDashboardRow(long budgetId, String budgetName, long customerorderId, String customerorderSign,
                              String customerorderName, LocalDate validFrom, LocalDate validUntil,
                              LocalDate evaluatedUntil, BigDecimal budgetEuro, BigDecimal coveredRevenueEuro,
                              Integer alertThresholdPercent, double utilizationPercent, Double progressPercent,
                              ProgressStatus progressStatus) {
        this(budgetId, budgetName, customerorderId, customerorderSign, customerorderName, validFrom, validUntil,
            evaluatedUntil, budgetEuro, coveredRevenueEuro, alertThresholdPercent, utilizationPercent,
            progressPercent, progressStatus, false, null);
    }

    public boolean hasBudget() { return budgetEuro != null && budgetEuro.signum() != 0; }
    public boolean hasAlertThreshold() { return alertThresholdPercent != null; }

    /**
     * Whether the row has a utilization to show and to judge: the share of the budget, or for a
     * fixed price the share of the calculated hours. A fixed-price plan without a calculation has
     * none, the way a plan without a budget amount has none.
     */
    public boolean hasUtilization() {
        return fixedPrice ? hoursConsumedPercent != null : hasBudget();
    }

    /** What the utilization column shows and the thresholds read (→ {@link #hasUtilization()}). */
    public double shownUtilizationPercent() {
        return fixedPrice ? (hoursConsumedPercent == null ? 0.0 : hoursConsumedPercent) : utilizationPercent;
    }

    /**
     * The plan has reached its configured alert threshold. Purely a warning, and only meaningful
     * when a threshold is configured at all — the field is optional. Without a utilization there
     * is nothing to be a percentage of, so such a plan is never above its threshold.
     */
    public boolean isAboveThreshold() {
        return hasUtilization() && hasAlertThreshold() && shownUtilizationPercent() >= alertThresholdPercent;
    }

    /**
     * The budget is used up — for a fixed price, the calculated hours. Independent of the alert
     * threshold, so a plan without one is still flagged once it goes over.
     */
    public boolean isOverBudget() { return hasUtilization() && shownUtilizationPercent() > 100.0; }

    public double progressBarPercent() { return Math.min(shownUtilizationPercent(), 100.0); }

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
