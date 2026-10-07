package de.hbt.salat.budget.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * One line of the calculation of a fixed-price plan with what was booked against it (#1404), or the
 * total over them.
 *
 * @param lineId             the calculation line, {@code null} for a line of bookings nobody
 *                           calculated with and for the total
 * @param categoryName       the cost category; {@code null} on the line of bookings without an
 *                           effective cost category and on the total
 * @param calculatedCostEuro the calculated hours at the rate of the category; {@code null} where costs
 *                           are not reported or the category has no rate
 * @param bookedCostEuro     what the booked hours cost; {@code null} where costs are not reported
 */
public record FixedPriceCalculationRow(
    Long lineId,
    String suborderSign,
    String suborderLabel,
    String categoryName,
    Duration calculatedHours,
    Duration bookedHours,
    BigDecimal calculatedCostEuro,
    BigDecimal bookedCostEuro
) {

    /** The total over the rows. A calculated cost is only summed where every calculated line has one. */
    static FixedPriceCalculationRow total(List<FixedPriceCalculationRow> rows, boolean costsIncluded) {
        var calculated = rows.stream().map(FixedPriceCalculationRow::calculatedHours).reduce(Duration.ZERO, Duration::plus);
        var booked = rows.stream().map(FixedPriceCalculationRow::bookedHours).reduce(Duration.ZERO, Duration::plus);
        BigDecimal calculatedCost = null;
        BigDecimal bookedCost = null;
        if (costsIncluded) {
            var complete = rows.stream().filter(FixedPriceCalculationRow::isCalculated)
                .allMatch(row -> row.calculatedCostEuro() != null);
            calculatedCost = complete
                ? rows.stream().map(FixedPriceCalculationRow::calculatedCostEuro).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
            bookedCost = rows.stream().map(FixedPriceCalculationRow::bookedCostEuro).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return new FixedPriceCalculationRow(null, null, null, null, calculated, booked, calculatedCost, bookedCost);
    }

    /** Whether the line stands for a line of the calculation rather than for bookings nobody calculated. */
    public boolean isCalculated() {
        return lineId != null;
    }

    /** Whether the bookings of this line have no effective cost category (#1404: „ohne Kostensatz"). */
    public boolean isWithoutCategory() {
        return categoryName == null;
    }

    public boolean hasCalculatedHours() {
        return calculatedHours != null && !calculatedHours.isZero();
    }

    /** Booked against calculated hours, in percent; {@code null} without calculated hours. */
    public Double consumedPercent() {
        if (!hasCalculatedHours()) {
            return null;
        }
        return 100.0 * bookedHours.toMinutes() / calculatedHours.toMinutes();
    }

    public boolean hasConsumedPercent() {
        return consumedPercent() != null;
    }

    public boolean hasCalculatedCost() {
        return calculatedCostEuro != null;
    }

    public boolean hasBookedCost() {
        return bookedCostEuro != null;
    }

    /**
     * The complete order sign with room to breathe, as the controlling shows it
     * (→ {@link BudgetControllingRow#signFormatted()}).
     */
    public String signFormatted() {
        return suborderSign == null ? null : suborderSign.replace("/", " / ");
    }

    public String calculatedHoursFormatted() {
        return format(calculatedHours);
    }

    public String bookedHoursFormatted() {
        return format(bookedHours);
    }

    private static String format(Duration d) {
        if (d == null || d.isZero()) return "—";
        return d.toHours() + ":" + String.format("%02d", d.toMinutesPart());
    }

}
