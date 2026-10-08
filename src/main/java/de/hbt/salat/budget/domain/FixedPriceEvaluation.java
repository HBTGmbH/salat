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
 * <p>Three hourly rates, all derived here and nowhere else:
 * <ul>
 *   <li><b>calculated</b> — fixed price ÷ calculated hours: what an hour was offered at;</li>
 *   <li><b>realized so far</b> — the flat rates fallen due by {@code realizedUntil} ÷ the hours
 *       booked: what an hour has brought in as billed (#1435);</li>
 *   <li><b>expected at completion</b> — fixed price ÷ the hours projected from the booked ones and the
 *       progress: what an hour will have earned once the plan is finished.</li>
 * </ul>
 * The first version read „so far" off the progress as well, which made it equal to „expected at
 * completion" by arithmetic — (price × p) ÷ h = price ÷ (h ÷ p). Read off the billing it answers a
 * question of its own: an instalment plan billing ahead of the work shows a realized rate above the
 * expected one, one lagging behind a rate below it.
 *
 * <p>Where a figure cannot be computed — no fixed price, no calculation, no progress, no booking — the
 * rate carries the reason instead of a value ({@link Gap}), and the view shows a dash with it.
 *
 * @param fixedPriceEuro  the sum of the plan's adjustments
 * @param realizedEuro    the flat rate amounts allocated to the plan that have fallen due by
 *                        {@code realizedUntil}
 * @param realizedUntil   the end of the evaluated window, but never later than today: a flat rate
 *                        falling due after today has not been billed yet (#1436)
 * @param progressPercent the progress entered by hand, {@code null} where none was entered yet
 * @param progressStatus  progress against the consumption of the calculated hours
 */
public record FixedPriceEvaluation(
    List<FixedPriceCalculationRow> rows,
    FixedPriceCalculationRow total,
    BigDecimal fixedPriceEuro,
    BigDecimal realizedEuro,
    LocalDate realizedUntil,
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

    /**
     * The flat rates fallen due ÷ the hours booked (#1435). Needs neither the fixed price nor the
     * progress — only something to divide by; nothing billed yet is a rate of 0 EUR, not a gap.
     */
    public HourlyRate realizedRateSoFar() {
        if (!hasBookedHours()) {
            return HourlyRate.missing(Gap.NO_BOOKINGS);
        }
        return HourlyRate.of(perHour(realizedEuro, total.bookedHours()));
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
