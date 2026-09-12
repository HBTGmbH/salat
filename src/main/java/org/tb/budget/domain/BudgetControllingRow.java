package org.tb.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import lombok.Builder;

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
    Duration plannedHours,
    /**
     * Hours booked before the evaluated window opened (#917). Reported next to the window's own
     * hours so a reader sees how much of the work already predates the period being looked at; the
     * amounts in this line cover both.
     */
    Duration bookedHoursBeforeWindow,
    Duration bookedHours,
    /** The budget of the plan this line stands for; {@code null} on lines that carry none. */
    BigDecimal budgetEuro,
    BigDecimal revenueEuro,
    /**
     * Revenue from flat rates falling due in this line's span (#972) — amounts agreed for the order
     * rather than earned by the hour. Reported apart from {@code revenueEuro} so a reader can tell
     * the two sources apart; every figure derived from revenue uses {@link #totalRevenueEuro()},
     * which is their sum.
     */
    BigDecimal flatRateRevenueEuro,
    BigDecimal costEuro,
    Duration forecastHours,
    BigDecimal forecastRevenueEuro,
    ForecastStatus forecastStatus,
    /**
     * Whether this line is a flat rate rather than a suborder (#972). Such a line has no hours at
     * all, so the view says what the amount in it stands for instead of leaving a row of dashes.
     */
    boolean flatRate
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

    public boolean hasFlatRateRevenue() {
        return flatRateRevenueEuro != null && flatRateRevenueEuro.signum() != 0;
    }

    /**
     * Everything this line earns: hours times rate plus the flat rates falling due in it (#972).
     * {@code null} only when neither source says anything, which keeps the "no data" case
     * distinguishable from a genuine zero.
     */
    public BigDecimal totalRevenueEuro() {
        if (revenueEuro == null && flatRateRevenueEuro == null) {
            return null;
        }
        return orZero(revenueEuro).add(orZero(flatRateRevenueEuro));
    }

    public boolean hasTotalRevenue() {
        var total = totalRevenueEuro();
        return total != null && total.signum() != 0;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public boolean hasCost() {
        return costEuro != null && costEuro.signum() != 0;
    }

    /**
     * Whether the line says anything at all. Hours-based revenue, cost and forecast are all derived
     * from booked time, budget and planned hours (#901) — but a flat rate is not: it is due whether
     * or not anybody booked, so a line carrying nothing else still has something to report (#972).
     */
    public boolean hasContent() {
        return hasBooked() || hasBookedBeforeWindow() || hasBudget() || hasPlanned() || hasFlatRateRevenue();
    }

    public boolean hasBudgetPercent() {
        return hasBudget() && totalRevenueEuro() != null;
    }

    public double budgetUsedPercent() {
        if (!hasBudgetPercent()) return 0.0;
        return totalRevenueEuro().divide(budgetEuro, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    /** What the plan was exceeded by. Going over budget is normal, so it is reported as an amount. */
    public BigDecimal overrunEuro() {
        if (!hasBudget() || totalRevenueEuro() == null) return BigDecimal.ZERO;
        var diff = totalRevenueEuro().subtract(budgetEuro);
        return diff.signum() > 0 ? diff : BigDecimal.ZERO;
    }

    public boolean hasOverrun() {
        return overrunEuro().signum() > 0;
    }

    public boolean hasForecast() {
        return forecastHours != null;
    }

    public BigDecimal grossProfitEuro() {
        if (totalRevenueEuro() == null || costEuro == null) return null;
        return totalRevenueEuro().subtract(costEuro);
    }

    public boolean hasGrossProfit() {
        return grossProfitEuro() != null;
    }

    /**
     * A flat rate line has no cost of its own — an agreed amount is not worked — so its margin can
     * only ever be 100 % (#972). A column of that says nothing and invites a comparison with the
     * lines around it, which do carry cost. Aggregates are unaffected: a subtotal or total that
     * includes flat rates has a margin worth reading.
     */
    public boolean hasGrossProfitMargin() {
        return !flatRate && hasGrossProfit() && hasTotalRevenue();
    }

    public double grossProfitMarginPercent() {
        return grossProfitEuro().divide(totalRevenueEuro(), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    public String formatHours(Duration d) {
        if (d == null || d.isZero()) return "—";
        return d.toHours() + ":" + String.format("%02d", d.toMinutesPart());
    }

    public String bookedHoursFormatted() { return formatHours(bookedHours); }

    /**
     * The complete order sign with room to breathe: {@code 1612 / 01 / D} instead of
     * {@code 1612/01/D}. The line must not break, so the cell sets {@code text-nowrap} — the spaces
     * are there to be read, not to wrap at.
     */
    public String signFormatted() {
        return sign == null ? null : sign.replace("/", " / ");
    }

    public boolean hasBookedBeforeWindow() {
        return bookedHoursBeforeWindow != null && !bookedHoursBeforeWindow.isZero();
    }

    public String bookedHoursBeforeWindowFormatted() { return formatHours(bookedHoursBeforeWindow); }

    public String plannedHoursFormatted() { return hasPlanned() ? formatHours(plannedHours) : "—"; }

    public String forecastHoursFormatted() { return hasForecast() ? formatHours(forecastHours) : "—"; }
}
