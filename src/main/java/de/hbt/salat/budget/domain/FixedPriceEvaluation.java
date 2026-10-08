package de.hbt.salat.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Where a fixed-price plan stands (#1404, #1405): its calculation with what was booked against it,
 * the fixed price, the progress entered by hand and what an hour of the plan is actually worth.
 *
 * <p>The fixed price is the sum of the plan's adjustments (#1435), the price agreed. The flat rates
 * are what has been billed of it: an instalment plan enters each of them once its stage is reached,
 * so their sum grows with the project and is no basis for a calculation.
 *
 * <p>Two hourly rates, both derived here and nowhere else:
 * <ul>
 *   <li><b>calculated</b> — fixed price ÷ calculated hours: what an hour was offered at;</li>
 *   <li><b>expected at completion</b> — fixed price ÷ the hours projected from the booked ones and the
 *       progress: what an hour will have earned once the plan is finished.</li>
 * </ul>
 * A third, „so far", was dropped (#1435). Read off the progress it equals the expected rate by
 * arithmetic — (price × p) ÷ h = price ÷ (h ÷ p) —, and read off the flat rates fallen due it follows
 * the billing calendar instead of the work. What has been billed stands next to the price as an
 * amount ({@code billedEuro}).
 *
 * <p>The gross profit comes in three, for the same three points in time: calculated, so far and
 * expected at completion, the last one against the cost projected like the hours (#1435).
 *
 * <p>Where a figure cannot be computed — no fixed price, no calculation, no progress, no booking — the
 * rate carries the reason instead of a value ({@link Gap}), and the view shows a dash with it.
 *
 * @param fixedPriceEuro  the sum of the plan's adjustments
 * @param billedEuro      the flat rate amounts allocated to the plan that have fallen due by the end
 *                        of the evaluated window, but never later than today (#1436)
 * @param progressPercent the progress entered by hand, {@code null} where none was entered yet
 * @param progressStatus  progress against the consumption of the calculated hours
 */
public record FixedPriceEvaluation(
    List<FixedPriceCalculationRow> rows,
    FixedPriceCalculationRow total,
    BigDecimal fixedPriceEuro,
    BigDecimal billedEuro,
    Double progressPercent,
    ProgressStatus progressStatus,
    boolean costsIncluded
) {

    /** Why a rate has no value. */
    public enum Gap {
        NO_FIXED_PRICE, NO_CALCULATION, NO_PROGRESS, NO_BOOKINGS
    }

    /** A rate in euro per hour, or the reason there is none. */
    public record HourlyRate(BigDecimal euroPerHour, Gap gap) {

        static HourlyRate of(BigDecimal euroPerHour) {
            return new HourlyRate(euroPerHour, null);
        }

        static HourlyRate missing(Gap gap) {
            return new HourlyRate(null, gap);
        }

        public boolean hasValue() {
            return euroPerHour != null;
        }
    }

    public FixedPriceEvaluation {
        rows = List.copyOf(rows);
    }

    public boolean hasRows() {
        return !rows.isEmpty();
    }

    public boolean hasFixedPrice() {
        return fixedPriceEuro != null && fixedPriceEuro.signum() > 0;
    }

    public boolean hasProgress() {
        return progressPercent != null;
    }

    public boolean hasProgressStatus() {
        return progressStatus != null && progressStatus != ProgressStatus.UNKNOWN;
    }

    private boolean hasBookedHours() {
        return total.bookedHours() != null && !total.bookedHours().isZero();
    }

    /** Booked against calculated hours over the whole plan, {@code null} without a calculation. */
    public Double consumedPercent() {
        return total.consumedPercent();
    }

    /** The share of the fixed price the progress stands for. */
    public BigDecimal earnedByProgressEuro() {
        if (!hasFixedPrice() || !hasProgress()) {
            return null;
        }
        return fixedPriceEuro.multiply(BigDecimal.valueOf(progressPercent))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** The hours the plan will take at the pace so far: booked hours ÷ progress. */
    public Duration projectedHours() {
        if (!hasProgress() || progressPercent <= 0 || !hasBookedHours()) {
            return null;
        }
        return Duration.ofMinutes(Math.round(total.bookedHours().toMinutes() * 100.0 / progressPercent));
    }

    /** Fixed price ÷ calculated hours. */
    public HourlyRate calculatedRate() {
        if (!hasFixedPrice()) {
            return HourlyRate.missing(Gap.NO_FIXED_PRICE);
        }
        if (!total.hasCalculatedHours()) {
            return HourlyRate.missing(Gap.NO_CALCULATION);
        }
        return HourlyRate.of(perHour(fixedPriceEuro, total.calculatedHours()));
    }

    /** Fixed price ÷ projected hours. */
    public HourlyRate expectedRateAtCompletion() {
        if (!hasFixedPrice()) {
            return HourlyRate.missing(Gap.NO_FIXED_PRICE);
        }
        if (!hasProgress() || progressPercent <= 0) {
            return HourlyRate.missing(Gap.NO_PROGRESS);
        }
        if (!hasBookedHours()) {
            return HourlyRate.missing(Gap.NO_BOOKINGS);
        }
        return HourlyRate.of(perHour(fixedPriceEuro, projectedHours()));
    }

    /** Fixed price less the calculated cost; {@code null} where costs are not reported or incomplete. */
    public BigDecimal calculatedGrossProfitEuro() {
        if (!costsIncluded || !hasFixedPrice() || total.calculatedCostEuro() == null) {
            return null;
        }
        return fixedPriceEuro.subtract(total.calculatedCostEuro());
    }

    /** The share of the fixed price earned by the progress less what the booked hours cost. */
    public BigDecimal grossProfitSoFarEuro() {
        var earned = earnedByProgressEuro();
        if (!costsIncluded || earned == null || total.bookedCostEuro() == null) {
            return null;
        }
        return earned.subtract(total.bookedCostEuro());
    }

    /**
     * The cost the plan will have run up at the pace so far: actual cost ÷ progress, the way
     * {@link #projectedHours()} projects the hours; {@code null} where costs are not reported.
     */
    public BigDecimal projectedCostEuro() {
        if (!costsIncluded || !hasProgress() || progressPercent <= 0 || total.bookedCostEuro() == null) {
            return null;
        }
        return total.bookedCostEuro().multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(progressPercent), 2, RoundingMode.HALF_UP);
    }

    /** Fixed price less the projected cost (#1435); {@code null} where either is missing. */
    public BigDecimal expectedGrossProfitEuro() {
        var projectedCost = projectedCostEuro();
        if (!hasFixedPrice() || projectedCost == null) {
            return null;
        }
        return fixedPriceEuro.subtract(projectedCost);
    }

    public String progressFormatted() {
        return hasProgress() ? String.format("%.1f", progressPercent) + " %" : "—";
    }

    public String projectedHoursFormatted() {
        var hours = projectedHours();
        return hours == null ? "—" : hours.toHours() + ":" + String.format("%02d", hours.toMinutesPart());
    }

    private static BigDecimal perHour(BigDecimal amount, Duration hours) {
        return amount.divide(FixedPriceCalculation.hoursOf(hours), 2, RoundingMode.HALF_UP);
    }

}
