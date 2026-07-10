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
        return budgetEuro != null && budgetEuro.signum() != 0 && coveredRevenueEuro != null;
    }

    public double budgetUsedPercent() {
        if (!hasBudgetPercent()) return 0.0;
        return coveredRevenueEuro.divide(budgetEuro, 6, RoundingMode.HALF_UP)
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

    public boolean hasProgress() { return progressPercent != null; }

    public String progressFormatted() {
        return hasProgress() ? String.format("%.1f", progressPercent) + " %" : "—";
    }

    public String bookedHoursFormatted() { return formatHours(bookedHours); }
    public String plannedHoursFormatted() { return hasPlanned() ? formatHours(plannedHours) : "—"; }
    public String forecastHoursFormatted() { return hasForecast() ? formatHours(forecastHours) : "—"; }
}
