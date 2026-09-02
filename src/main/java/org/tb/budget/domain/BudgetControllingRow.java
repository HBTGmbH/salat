package org.tb.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.Builder;
import org.tb.common.LocalDateRange;

/**
 * One line of a controlling section — a suborder, a group subtotal or the section total.
 *
 * <p>There is no "covered revenue" any more: a section reports exactly one budget period, so every
 * euro in {@code revenueEuro} was earned inside it. Revenue outside any plan lives in its own
 * {@link SectionKind#UNPLANNED} section instead of in a second column here.
 *
 * <p>Built through the generated builder — the constructor grew to fourteen positional arguments and
 * broke every test twice when a field was added.
 */
@Builder
public record BudgetControllingRow(
    String sign,
    String label,
    /** Only filled on unplanned rows, which each cover their own gaps. */
    List<LocalDateRange> periods,
    Duration plannedHours,
    Duration bookedHours,
    /** The budget of the plan this line stands for; {@code null} on lines that carry none. */
    BigDecimal budgetEuro,
    BigDecimal revenueEuro,
    BigDecimal costEuro,
    Duration forecastHours,
    BigDecimal forecastRevenueEuro,
    ForecastStatus forecastStatus,
    Double progressPercent,
    ProgressStatus progressStatus
) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

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

    public boolean hasCost() {
        return costEuro != null && costEuro.signum() != 0;
    }

    /**
     * Whether the line says anything at all. Without booked time, budget and planned hours there is
     * nothing left to report — revenue, cost and forecast are all derived from those (#901).
     */
    public boolean hasContent() {
        return hasBooked() || hasBudget() || hasPlanned();
    }

    public boolean hasBudgetPercent() {
        return hasBudget() && revenueEuro != null;
    }

    public double budgetUsedPercent() {
        if (!hasBudgetPercent()) return 0.0;
        return revenueEuro.divide(budgetEuro, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    /** What the plan was exceeded by. Going over budget is normal, so it is reported as an amount. */
    public BigDecimal overrunEuro() {
        if (!hasBudget() || revenueEuro == null) return BigDecimal.ZERO;
        var diff = revenueEuro.subtract(budgetEuro);
        return diff.signum() > 0 ? diff : BigDecimal.ZERO;
    }

    public boolean hasOverrun() {
        return overrunEuro().signum() > 0;
    }

    public boolean hasForecast() {
        return forecastHours != null;
    }

    public BigDecimal grossProfitEuro() {
        if (revenueEuro == null || costEuro == null) return null;
        return revenueEuro.subtract(costEuro);
    }

    public boolean hasGrossProfit() {
        return grossProfitEuro() != null;
    }

    public boolean hasGrossProfitMargin() {
        return hasGrossProfit() && revenueEuro != null && revenueEuro.signum() != 0;
    }

    public double grossProfitMarginPercent() {
        return grossProfitEuro().divide(revenueEuro, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    public boolean hasProgress() {
        return progressPercent != null;
    }

    public String progressFormatted() {
        return hasProgress() ? String.format("%.1f", progressPercent) + " %" : "—";
    }

    public String formatHours(Duration d) {
        if (d == null || d.isZero()) return "—";
        return d.toHours() + ":" + String.format("%02d", d.toMinutesPart());
    }

    public String bookedHoursFormatted() { return formatHours(bookedHours); }

    public String plannedHoursFormatted() { return hasPlanned() ? formatHours(plannedHours) : "—"; }

    public String forecastHoursFormatted() { return hasForecast() ? formatHours(forecastHours) : "—"; }

    /** The gaps this line covers, e.g. {@code 01.01.2026 – 28.02.2026, 01.07.2026 – 31.12.2026}. */
    public String periodsFormatted() {
        if (periods == null || periods.isEmpty()) return "—";
        return periods.stream()
            .map(p -> DATE.format(p.getFrom()) + " – " + DATE.format(p.getUntil()))
            .reduce((a, b) -> a + ", " + b)
            .orElse("—");
    }
}
