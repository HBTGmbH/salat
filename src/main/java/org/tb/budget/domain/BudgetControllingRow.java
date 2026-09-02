package org.tb.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

public record BudgetControllingRow(
    String sign,
    String label,
    boolean isSuborder,
    Duration plannedHours,
    Duration bookedHours,
    BigDecimal budgetEuro,
    BigDecimal revenueEuro,
    BigDecimal coveredRevenueEuro,
    /**
     * The part of {@code coveredRevenueEuro} that {@code budgetEuro} actually covers. The two differ
     * for a suborder whose own plan covers only part of the period: the rest is covered by the
     * order-level plan, belongs in the revenue but must not be charged against the own budget.
     */
    BigDecimal budgetCoveredRevenueEuro,
    BigDecimal costEuro,
    Duration forecastHours,
    BigDecimal forecastRevenueEuro,
    BigDecimal forecastUncoveredRevenueEuro,
    ForecastStatus forecastStatus,
    Double progressPercent,
    ProgressStatus progressStatus
) {
    public double bookedPercent() {
        if (plannedHours == null || plannedHours.isZero()) return 0.0;
        return 100.0 * bookedHours.toMinutes() / plannedHours.toMinutes();
    }

    public boolean hasPlanned() {
        return plannedHours != null && !plannedHours.isZero();
    }

    public boolean hasBooked() {
        return bookedHours != null && !bookedHours.isZero();
    }

    public boolean hasBudget() {
        return budgetEuro != null && budgetEuro.signum() != 0;
    }

    public boolean hasRevenue() {
        return revenueEuro != null && revenueEuro.signum() != 0;
    }

    public boolean hasCoveredRevenue() {
        return coveredRevenueEuro != null && coveredRevenueEuro.signum() != 0;
    }

    public boolean hasCost() {
        return costEuro != null && costEuro.signum() != 0;
    }

    public boolean hasBudgetPercent() {
        return budgetEuro != null && budgetEuro.signum() != 0 && budgetCoveredRevenueEuro != null;
    }

    public double budgetUsedPercent() {
        if (!hasBudgetPercent()) return 0.0;
        return budgetCoveredRevenueEuro.divide(budgetEuro, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    public BigDecimal uncoveredRevenueEuro() {
        if (revenueEuro == null || coveredRevenueEuro == null) return BigDecimal.ZERO;
        var diff = revenueEuro.subtract(coveredRevenueEuro);
        return diff.signum() > 0 ? diff : BigDecimal.ZERO;
    }

    public boolean hasUncoveredRevenue() {
        return uncoveredRevenueEuro().signum() > 0;
    }

    public boolean hasForecast() {
        return forecastHours != null;
    }

    public boolean hasForecastRevenue() {
        return forecastRevenueEuro != null;
    }

    public boolean hasForecastUncoveredRevenue() {
        return forecastUncoveredRevenueEuro != null && forecastUncoveredRevenueEuro.signum() > 0;
    }

    public String formatHours(Duration d) {
        if (d == null || d.isZero()) return "—";
        return d.toHours() + ":" + String.format("%02d", d.toMinutesPart());
    }

    public BigDecimal grossProfitEuro() {
        if (coveredRevenueEuro == null || costEuro == null) return null;
        return coveredRevenueEuro.subtract(costEuro);
    }

    public boolean hasGrossProfit() { return grossProfitEuro() != null; }

    public boolean hasGrossProfitMargin() {
        return hasGrossProfit() && coveredRevenueEuro != null && coveredRevenueEuro.signum() != 0;
    }

    public double grossProfitMarginPercent() {
        return grossProfitEuro().divide(coveredRevenueEuro, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    /**
     * Whether the row says anything at all. Without booked time, budget and planned hours there is
     * nothing left to report — revenue, cost and forecast are all derived from those — so the row
     * would only be a line of dashes.
     */
    public boolean hasContent() {
        return hasBooked() || hasBudget() || hasPlanned();
    }

    public boolean hasProgress() { return progressPercent != null; }

    public String progressFormatted() {
        return hasProgress() ? String.format("%.1f", progressPercent) + " %" : "—";
    }

    public String bookedHoursFormatted() { return formatHours(bookedHours); }
    public String plannedHoursFormatted() { return hasPlanned() ? formatHours(plannedHours) : "—"; }
    public String forecastHoursFormatted() { return hasForecast() ? formatHours(forecastHours) : "—"; }
}
