package de.hbt.salat.budget.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sets the booked hours of a fixed-price plan against its calculation (#1404).
 *
 * <p>A booking counts against the calculation line whose category is the cost category of the person
 * on the day of the booking ({@link EmployeeCostLookup}) and whose suborder is the booking's suborder
 * or lies above it — the line covers its subtree like a plan does ({@link BudgetScope}). Where two
 * lines of the same category lie on one branch, the deeper one takes the booking: it is the more
 * specific statement, and counting the hours on both would report them twice.
 *
 * <p><strong>No booked hour is lost.</strong> A booking no line takes — a person without an effective
 * cost category, or a category nobody calculated with on that branch — is reported in a line of its own
 * with no calculated hours: per category, the bookings without a category as „ohne Kostensatz".
 * Such a line hangs on the deepest calculated suborder above the booking, otherwise on the booking's
 * own suborder. The total counts it all, so the consumption of the plan is every hour worked against
 * every hour calculated.
 *
 * <p>Pure computation over values, so that the rules can be tested without a database; reading the
 * plan, its bookings and the categories is the job of {@code FixedPriceCalculationService}.
 */
public final class FixedPriceCalculation {

    private FixedPriceCalculation() {
    }

    /**
     * One line of the calculation as the computation needs it.
     *
     * @param costEuroPerHour what an hour of the category costs, {@code null} where costs are not
     *                        reported or the category has no rate
     */
    public record Line(long id, long suborderId, String suborderSign, String suborderLabel,
                       long categoryId, String categoryName, Duration calculatedHours,
                       BigDecimal costEuroPerHour) {}

    /**
     * One booking of the plan.
     *
     * @param suborderPath the ids of the suborders from the top level down to the booked one
     * @param categoryId   the cost category of the person on the day of the booking, {@code null}
     *                     where none applies
     * @param costEuro     what the booking cost, {@code null} where costs are not reported
     */
    public record Booking(long suborderId, List<Long> suborderPath, String suborderSign, String suborderLabel,
                          Duration duration, Long categoryId, String categoryName, BigDecimal costEuro) {

        public Booking {
            suborderPath = List.copyOf(suborderPath);
        }
    }

    /** The lines of the calculation with what was booked against them, and their total. */
    public record Result(List<FixedPriceCalculationRow> rows, FixedPriceCalculationRow total) {}

    /**
     * @param costsIncluded whether cost is reported at all; without it every cost figure stays
     *                      {@code null}, so that a zero cannot pass for a genuine figure
     */
    public static Result evaluate(List<Line> lines, List<Booking> bookings, boolean costsIncluded) {
        Map<Long, Accumulator> byLine = new LinkedHashMap<>();
        lines.stream()
            // By suborder, and within one suborder in the order the lines were entered.
            .sorted(Comparator.comparing(Line::suborderSign, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingLong(Line::id))
            .forEach(line -> byLine.put(line.id(), Accumulator.of(line, costsIncluded)));
        Map<UncalculatedKey, Accumulator> uncalculated = new LinkedHashMap<>();

        for (var booking : bookings) {
            var line = matchingLine(lines, booking);
            if (line != null) {
                byLine.get(line.id()).add(booking, costsIncluded);
                continue;
            }
            var anchor = deepestCalculatedSuborder(lines, booking);
            var key = new UncalculatedKey(anchor == null ? booking.suborderId() : anchor.suborderId(),
                booking.categoryId());
            uncalculated.computeIfAbsent(key, k -> anchor == null
                    ? Accumulator.uncalculated(booking.suborderSign(), booking.suborderLabel(), booking.categoryName(),
                        costsIncluded)
                    : Accumulator.uncalculated(anchor.suborderSign(), anchor.suborderLabel(), booking.categoryName(),
                        costsIncluded))
                .add(booking, costsIncluded);
        }

        var rows = new ArrayList<FixedPriceCalculationRow>();
        byLine.values().forEach(acc -> rows.add(acc.toRow()));
        uncalculated.values().stream()
            .map(Accumulator::toRow)
            .sorted(Comparator.comparing(FixedPriceCalculationRow::suborderSign,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FixedPriceCalculationRow::categoryName, Comparator.nullsLast(Comparator.naturalOrder())))
            .forEach(rows::add);
        return new Result(List.copyOf(rows), FixedPriceCalculationRow.total(rows, costsIncluded));
    }

    /**
     * The line that takes the booking: same category, suborder on the booking's path, and of those
     * the deepest one. {@code null} where none qualifies.
     */
    private static Line matchingLine(List<Line> lines, Booking booking) {
        if (booking.categoryId() == null) {
            return null;
        }
        return lines.stream()
            .filter(line -> line.categoryId() == booking.categoryId())
            .filter(line -> booking.suborderPath().contains(line.suborderId()))
            .max(Comparator.comparingInt(line -> booking.suborderPath().indexOf(line.suborderId())))
            .orElse(null);
    }

    /** The calculated suborder lying deepest above the booking, whatever its category. */
    private static Line deepestCalculatedSuborder(List<Line> lines, Booking booking) {
        return lines.stream()
            .filter(line -> booking.suborderPath().contains(line.suborderId()))
            .max(Comparator.comparingInt(line -> booking.suborderPath().indexOf(line.suborderId())))
            .orElse(null);
    }

    private record UncalculatedKey(long suborderId, Long categoryId) {}

    /** Collects the bookings of one row. */
    private static final class Accumulator {

        private final Long lineId;
        private final String suborderSign;
        private final String suborderLabel;
        private final String categoryName;
        private final Duration calculatedHours;
        private final BigDecimal calculatedCostEuro;
        private Duration bookedHours = Duration.ZERO;
        private BigDecimal bookedCostEuro;

        private Accumulator(Long lineId, String suborderSign, String suborderLabel, String categoryName,
                            Duration calculatedHours, BigDecimal calculatedCostEuro, boolean costsIncluded) {
            this.lineId = lineId;
            this.suborderSign = suborderSign;
            this.suborderLabel = suborderLabel;
            this.categoryName = categoryName;
            this.calculatedHours = calculatedHours;
            this.calculatedCostEuro = calculatedCostEuro;
            this.bookedCostEuro = costsIncluded ? BigDecimal.ZERO : null;
        }

        static Accumulator of(Line line, boolean costsIncluded) {
            var cost = costsIncluded && line.costEuroPerHour() != null
                ? hoursOf(line.calculatedHours()).multiply(line.costEuroPerHour()).setScale(2, RoundingMode.HALF_UP)
                : null;
            return new Accumulator(line.id(), line.suborderSign(), line.suborderLabel(), line.categoryName(),
                line.calculatedHours(), cost, costsIncluded);
        }

        static Accumulator uncalculated(String suborderSign, String suborderLabel, String categoryName,
                                        boolean costsIncluded) {
            return new Accumulator(null, suborderSign, suborderLabel, categoryName, Duration.ZERO,
                costsIncluded ? BigDecimal.ZERO : null, costsIncluded);
        }

        void add(Booking booking, boolean costsIncluded) {
            bookedHours = bookedHours.plus(booking.duration());
            if (costsIncluded) {
                bookedCostEuro = bookedCostEuro.add(Objects.requireNonNullElse(booking.costEuro(), BigDecimal.ZERO));
            }
        }

        FixedPriceCalculationRow toRow() {
            return new FixedPriceCalculationRow(lineId, suborderSign, suborderLabel, categoryName, calculatedHours,
                bookedHours, calculatedCostEuro, bookedCostEuro);
        }
    }

    static BigDecimal hoursOf(Duration duration) {
        return BigDecimal.valueOf(duration.toMinutes()).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }

}
