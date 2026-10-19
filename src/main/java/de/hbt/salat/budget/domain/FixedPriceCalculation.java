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
import java.util.stream.Stream;

/**
 * Sets the booked hours of a fixed-price plan against its calculation (#1404).
 *
 * <p>A line covers its suborder and the subtree below it, like a plan does ({@link BudgetScope}); a
 * line without a suborder covers the whole plan (#1496). A line without a category is for every
 * person. A booking counts against exactly one line, found in two steps:
 * <ol>
 *   <li>a line of the cost category of the person on the day of the booking
 *       ({@link EmployeeCostLookup}) that covers the booking;</li>
 *   <li>otherwise a line for all categories that covers it — people without an effective cost
 *       category included.</li>
 * </ol>
 * Within a step the deepest line takes the booking: it is the more specific statement, and counting
 * the hours on both would report them twice. The whole plan lies above every suborder. The category
 * goes first because a line of it names the people the hours were calculated for; a line for all
 * categories is the pool for everybody else.
 *
 * <p><strong>No booked hour is lost.</strong> A booking no line takes — a person without an effective
 * cost category, or a category nobody calculated with on that branch — is reported in a line of its own
 * with no calculated hours: per category, the bookings without a category as „ohne Kostensatz".
 * Such a line hangs on the deepest calculated place above the booking — the whole plan counts as one —,
 * otherwise on the booking's own suborder. The total counts it all, so the consumption of the plan is
 * every hour worked against every hour calculated.
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
     * @param suborderId      {@code null} for a line for the whole plan; its sign is then the one of
     *                        the plan's scope
     * @param categoryId      {@code null} for a line for all categories
     * @param costEuroPerHour what an hour of the category costs, {@code null} where costs are not
     *                        reported, the category has no rate or the line is for all categories
     */
    public record Line(long id, Long suborderId, String suborderSign, String suborderLabel,
                       Long categoryId, String categoryName, Duration calculatedHours,
                       BigDecimal costEuroPerHour) {

        public boolean wholePlan() {
            return suborderId == null;
        }

        boolean covers(Booking booking) {
            return wholePlan() || booking.suborderPath().contains(suborderId);
        }

        /** Where the line sits on the path of a booking it covers: -1 for the whole plan, above every suborder. */
        int depthOn(Booking booking) {
            return wholePlan() ? -1 : booking.suborderPath().indexOf(suborderId);
        }
    }

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
            // The whole plan first, then by suborder, and within one place in the order the lines were entered.
            .sorted(Comparator.comparing(Line::wholePlan).reversed()
                .thenComparing(Line::suborderSign, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingLong(Line::id))
            .forEach(line -> byLine.put(line.id(), Accumulator.of(line, costsIncluded)));
        Map<UncalculatedKey, Accumulator> uncalculated = new LinkedHashMap<>();

        for (var booking : bookings) {
            var line = matchingLine(lines, booking);
            if (line != null) {
                byLine.get(line.id()).add(booking, costsIncluded);
                continue;
            }
            var anchor = deepestCoveringLine(lines, booking);
            // The suborder the line hangs on; null stands for the whole plan, which no booking is on.
            // Boxed on purpose: a long next to the Long would unbox the null of the whole plan.
            var key = new UncalculatedKey(anchor == null ? Long.valueOf(booking.suborderId()) : anchor.suborderId(),
                booking.categoryId());
            uncalculated.computeIfAbsent(key, k -> anchor == null
                    ? Accumulator.uncalculated(false, booking.suborderSign(), booking.suborderLabel(),
                        booking.categoryName(), costsIncluded)
                    : Accumulator.uncalculated(anchor.wholePlan(), anchor.suborderSign(), anchor.suborderLabel(),
                        booking.categoryName(), costsIncluded))
                .add(booking, costsIncluded);
        }

        var rows = new ArrayList<FixedPriceCalculationRow>();
        byLine.values().forEach(acc -> rows.add(acc.toRow()));
        uncalculated.values().stream()
            .map(Accumulator::toRow)
            .sorted(Comparator.comparing(FixedPriceCalculationRow::wholePlan).reversed()
                .thenComparing(FixedPriceCalculationRow::suborderSign, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(FixedPriceCalculationRow::categoryName, Comparator.nullsLast(Comparator.naturalOrder())))
            .forEach(rows::add);
        return new Result(List.copyOf(rows), FixedPriceCalculationRow.total(rows, costsIncluded));
    }

    /**
     * The line that takes the booking: the deepest covering line of the person's category, otherwise
     * the deepest covering line for all categories. {@code null} where none qualifies.
     */
    private static Line matchingLine(List<Line> lines, Booking booking) {
        var ofCategory = booking.categoryId() == null ? null
            : deepest(lines.stream().filter(line -> booking.categoryId().equals(line.categoryId())), booking);
        return ofCategory != null ? ofCategory
            : deepest(lines.stream().filter(line -> line.categoryId() == null), booking);
    }

    /** The calculated place lying deepest above the booking, whatever its category; the whole plan counts. */
    private static Line deepestCoveringLine(List<Line> lines, Booking booking) {
        return deepest(lines.stream(), booking);
    }

    private static Line deepest(Stream<Line> lines, Booking booking) {
        return lines
            .filter(line -> line.covers(booking))
            .max(Comparator.comparingInt(line -> line.depthOn(booking)))
            .orElse(null);
    }

    /** @param suborderId the suborder the line hangs on, {@code null} for the whole plan */
    private record UncalculatedKey(Long suborderId, Long categoryId) {}

    /** Collects the bookings of one row. */
    private static final class Accumulator {

        private final Long lineId;
        private final boolean wholePlan;
        private final String suborderSign;
        private final String suborderLabel;
        private final String categoryName;
        private final Duration calculatedHours;
        private final BigDecimal calculatedCostEuro;
        private Duration bookedHours = Duration.ZERO;
        private BigDecimal bookedCostEuro;

        private Accumulator(Long lineId, boolean wholePlan, String suborderSign, String suborderLabel,
                            String categoryName, Duration calculatedHours, BigDecimal calculatedCostEuro,
                            boolean costsIncluded) {
            this.lineId = lineId;
            this.wholePlan = wholePlan;
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
            return new Accumulator(line.id(), line.wholePlan(), line.suborderSign(), line.suborderLabel(),
                line.categoryName(), line.calculatedHours(), cost, costsIncluded);
        }

        static Accumulator uncalculated(boolean wholePlan, String suborderSign, String suborderLabel,
                                        String categoryName, boolean costsIncluded) {
            return new Accumulator(null, wholePlan, suborderSign, suborderLabel, categoryName, Duration.ZERO,
                costsIncluded ? BigDecimal.ZERO : null, costsIncluded);
        }

        void add(Booking booking, boolean costsIncluded) {
            bookedHours = bookedHours.plus(booking.duration());
            if (costsIncluded) {
                bookedCostEuro = bookedCostEuro.add(Objects.requireNonNullElse(booking.costEuro(), BigDecimal.ZERO));
            }
        }

        FixedPriceCalculationRow toRow() {
            return new FixedPriceCalculationRow(lineId, wholePlan, suborderSign, suborderLabel, categoryName,
                calculatedHours, bookedHours, calculatedCostEuro, bookedCostEuro);
        }
    }

    static BigDecimal hoursOf(Duration duration) {
        return BigDecimal.valueOf(duration.toMinutes()).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
    }

}
