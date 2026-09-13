package org.tb.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
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
@Builder(toBuilder = true)
public record BudgetControllingRow(
    String sign,
    String label,
    Duration plannedHours,
    /**
     * Everything this line earned before the evaluated window opened — hours times rate plus the flat
     * rates that fell due back then.
     *
     * <p>Reported in euro rather than in hours (#779): what predates the window matters as a share of
     * the budget, and hours cannot be compared with the amounts next to them. It is what the budget
     * columns add to the window's own revenue, and it is why they can read against the whole plan
     * while every other column stays inside the period being looked at.
     */
    BigDecimal revenueBeforeWindowEuro,
    Duration bookedHours,
    /** The budget of the plan this line stands for; {@code null} on lines that carry none. */
    BigDecimal budgetEuro,
    /**
     * What the hours booked <em>inside</em> the window earned (#779). Revenue, cost and hours all
     * describe the same period now — otherwise profit and margin would divide an amount covering
     * years by the cost of one quarter.
     */
    BigDecimal revenueEuro,
    /**
     * Revenue from flat rates falling due inside the window (#972) — amounts agreed for the order
     * rather than earned by the hour. Reported apart from {@code revenueEuro} so a reader can tell
     * the two sources apart; every figure derived from revenue uses {@link #totalRevenueEuro()},
     * which is their sum.
     */
    BigDecimal flatRateRevenueEuro,
    /** What the work booked inside the window cost; {@code null} where costs are not reported. */
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

    /**
     * The sum over several lines: a group subtotal, a section total, the total over the sections of
     * an order (#779) or the total over the orders of a segment.
     *
     * <p>Only the absolute figures are added. Everything relative — consumption, budget utilization,
     * margin — is derived from them by the accessors above, so an aggregate is computed from its own
     * sums and never from the percentages of its parts. Averaging those would weight a line that
     * booked an hour like one that booked a thousand.
     *
     * <p>{@code budgetEuro} is passed in rather than summed: a budget belongs to a plan, and the
     * lines below it carry none. Where several plans answer for one sum, the caller adds their
     * budgets; where no plan does, it passes {@code null} and the budget columns stay empty.
     *
     * @param includeCosts whether cost is reported at all. Without the privilege it is not summed
     *                     but left {@code null}, which is what keeps the cost columns away — a
     *                     summed zero would look like a genuine figure.
     */
    public static BudgetControllingRow sum(String sign, String label, List<BudgetControllingRow> rows,
                                           BigDecimal budgetEuro, boolean includeCosts) {
        return BudgetControllingRow.builder()
            .sign(sign)
            .label(label)
            .plannedHours(sumHours(rows, BudgetControllingRow::plannedHours))
            .revenueBeforeWindowEuro(sumAmount(rows, BudgetControllingRow::revenueBeforeWindowEuro))
            .bookedHours(sumHours(rows, BudgetControllingRow::bookedHours))
            .budgetEuro(budgetEuro)
            .revenueEuro(sumAmount(rows, BudgetControllingRow::revenueEuro))
            .flatRateRevenueEuro(sumAmount(rows, BudgetControllingRow::flatRateRevenueEuro))
            .costEuro(includeCosts ? sumAmount(rows, BudgetControllingRow::costEuro) : null)
            .build();
    }

    /** The budgets of several lines, or {@code null} where not one of them carries a budget. */
    public static BigDecimal sumBudget(List<BudgetControllingRow> rows) {
        var budgets = rows.stream().map(BudgetControllingRow::budgetEuro).filter(b -> b != null).toList();
        return budgets.isEmpty() ? null : budgets.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Duration sumHours(List<BudgetControllingRow> rows,
                                     Function<BudgetControllingRow, Duration> hours) {
        return rows.stream().map(hours).filter(d -> d != null).reduce(Duration.ZERO, Duration::plus);
    }

    private static BigDecimal sumAmount(List<BudgetControllingRow> rows,
                                        Function<BudgetControllingRow, BigDecimal> amount) {
        return rows.stream().map(amount).map(BudgetControllingRow::orZero)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

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
        return hasBooked() || hasRevenueBeforeWindow() || hasBudget() || hasPlanned() || hasFlatRateRevenue();
    }

    /**
     * Everything the plan has consumed by the end of the window, the part earned before it included
     * (#917). The budget columns read against this and not against {@link #totalRevenueEuro()}: a
     * budget is granted for the whole plan, and measuring one quarter of consumption against all of
     * it would report every plan as barely touched.
     */
    public BigDecimal cumulativeRevenueEuro() {
        var inWindow = totalRevenueEuro();
        if (inWindow == null && revenueBeforeWindowEuro == null) {
            return null;
        }
        return orZero(inWindow).add(orZero(revenueBeforeWindowEuro));
    }

    public boolean hasBudgetPercent() {
        return hasBudget() && cumulativeRevenueEuro() != null;
    }

    public double budgetUsedPercent() {
        if (!hasBudgetPercent()) return 0.0;
        return cumulativeRevenueEuro().divide(budgetEuro, 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    /** What the plan was exceeded by. Going over budget is normal, so it is reported as an amount. */
    public BigDecimal overrunEuro() {
        if (!hasBudget() || cumulativeRevenueEuro() == null) return BigDecimal.ZERO;
        var diff = cumulativeRevenueEuro().subtract(budgetEuro);
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

    public boolean hasRevenueBeforeWindow() {
        return revenueBeforeWindowEuro != null && revenueBeforeWindowEuro.signum() != 0;
    }

    public String plannedHoursFormatted() { return hasPlanned() ? formatHours(plannedHours) : "—"; }

    public String forecastHoursFormatted() { return hasForecast() ? formatHours(forecastHours) : "—"; }
}
