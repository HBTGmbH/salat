package de.hbt.salat.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The booked hours of a fixed-price plan against its calculation (#1404): which line takes a
 * booking, and that no booked hour gets lost on the way.
 *
 * <p>The fixture: suborder 10 ({@code 4711/01}) and 20 ({@code 4711/02}) on the first level, 21
 * ({@code 4711/02/A}) below 20. Categories 1 (Senior) and 2 (Professional).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class FixedPriceCalculationTest {

  private static final long SENIOR = 1L;
  private static final long PROFESSIONAL = 2L;

  @Test
  void counts_a_booking_against_the_line_of_its_suborder_and_the_category_of_its_person() {
    var lines = List.of(
        line(1, 10, "4711/01", SENIOR, "Senior", 80),
        line(2, 20, "4711/02", SENIOR, "Senior", 120),
        line(3, 20, "4711/02", PROFESSIONAL, "Professional", 200));

    var result = FixedPriceCalculation.evaluate(lines, List.of(
        booking(10, List.of(10L), "4711/01", 72, SENIOR, "Senior"),
        booking(20, List.of(20L), "4711/02", 36, SENIOR, "Senior"),
        booking(20, List.of(20L), "4711/02", 52, PROFESSIONAL, "Professional")), false);

    assertThat(result.rows()).extracting(FixedPriceCalculationRow::lineId).containsExactly(1L, 2L, 3L);
    assertThat(result.rows()).extracting(FixedPriceCalculationRow::bookedHours)
        .containsExactly(Duration.ofHours(72), Duration.ofHours(36), Duration.ofHours(52));
    assertThat(result.rows().get(0).consumedPercent()).isEqualTo(90.0);
    // the mockup of the issue: 160 of 400 hours
    assertThat(result.total().calculatedHours()).isEqualTo(Duration.ofHours(400));
    assertThat(result.total().bookedHours()).isEqualTo(Duration.ofHours(160));
    assertThat(result.total().consumedPercent()).isEqualTo(40.0);
  }

  /** A line covers its subtree like a plan does (→ BudgetScope). */
  @Test
  void counts_a_booking_below_the_suborder_of_a_line_against_that_line() {
    var lines = List.of(line(2, 20, "4711/02", SENIOR, "Senior", 120));

    var result = FixedPriceCalculation.evaluate(lines,
        List.of(booking(21, List.of(20L, 21L), "4711/02/A", 8, SENIOR, "Senior")), false);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).bookedHours()).isEqualTo(Duration.ofHours(8));
  }

  /** Two lines of one category on one branch: the deeper one is the more specific statement. */
  @Test
  void counts_a_booking_against_the_deepest_line_only() {
    var lines = List.of(
        line(2, 20, "4711/02", SENIOR, "Senior", 120),
        line(4, 21, "4711/02/A", SENIOR, "Senior", 40));

    var result = FixedPriceCalculation.evaluate(lines,
        List.of(booking(21, List.of(20L, 21L), "4711/02/A", 8, SENIOR, "Senior")), false);

    assertThat(result.rows()).filteredOn(row -> row.lineId() == 4L).singleElement()
        .extracting(FixedPriceCalculationRow::bookedHours).isEqualTo(Duration.ofHours(8));
    assertThat(result.rows()).filteredOn(row -> row.lineId() == 2L).singleElement()
        .extracting(FixedPriceCalculationRow::bookedHours).isEqualTo(Duration.ZERO);
    assertThat(result.total().bookedHours()).isEqualTo(Duration.ofHours(8));
  }

  /**
   * A booking by a person without an effective cost category on the day is not lost: it appears in a
   * line of its own, "ohne Kostensatz", under the calculated suborder it was booked below, and the
   * total counts it.
   */
  @Test
  void reports_bookings_without_a_cost_category_in_a_line_of_their_own() {
    var lines = List.of(line(2, 20, "4711/02", SENIOR, "Senior", 120));

    var result = FixedPriceCalculation.evaluate(lines, List.of(
        booking(20, List.of(20L), "4711/02", 10, SENIOR, "Senior"),
        booking(21, List.of(20L, 21L), "4711/02/A", 6, null, null)), false);

    var withoutCategory = result.rows().stream().filter(FixedPriceCalculationRow::isWithoutCategory).toList();
    assertThat(withoutCategory).singleElement().satisfies(row -> {
      assertThat(row.isCalculated()).isFalse();
      assertThat(row.suborderSign()).isEqualTo("4711/02");
      assertThat(row.bookedHours()).isEqualTo(Duration.ofHours(6));
      assertThat(row.calculatedHours()).isEqualTo(Duration.ZERO);
      assertThat(row.consumedPercent()).isNull();
    });
    assertThat(result.total().bookedHours()).isEqualTo(Duration.ofHours(16));
  }

  /** A category nobody calculated with on that branch, or a suborder outside every line. */
  @Test
  void reports_bookings_nobody_calculated_with_per_category_and_suborder() {
    var lines = List.of(line(1, 10, "4711/01", SENIOR, "Senior", 80));

    var result = FixedPriceCalculation.evaluate(lines, List.of(
        booking(10, List.of(10L), "4711/01", 4, PROFESSIONAL, "Professional"),
        booking(10, List.of(10L), "4711/01", 2, PROFESSIONAL, "Professional"),
        booking(20, List.of(20L), "4711/02", 3, SENIOR, "Senior")), false);

    assertThat(result.rows()).hasSize(3);
    assertThat(result.rows().get(0).lineId()).isEqualTo(1L);
    assertThat(result.rows().subList(1, 3))
        .extracting(FixedPriceCalculationRow::suborderSign, FixedPriceCalculationRow::categoryName,
            FixedPriceCalculationRow::bookedHours)
        .containsExactly(
            tuple("4711/01", "Professional", Duration.ofHours(6)),
            tuple("4711/02", "Senior", Duration.ofHours(3)));
    assertThat(result.total().bookedHours()).isEqualTo(Duration.ofHours(9));
  }

  @Test
  void prices_calculated_and_booked_hours_only_where_costs_are_reported() {
    var lines = List.of(new FixedPriceCalculation.Line(1, 10, "4711/01", "Konzept", SENIOR, "Senior",
        Duration.ofHours(80), new BigDecimal("60.00")));
    var bookings = List.of(new FixedPriceCalculation.Booking(10, List.of(10L), "4711/01", "Konzept",
        Duration.ofHours(10), SENIOR, "Senior", new BigDecimal("600.00")));

    var withCosts = FixedPriceCalculation.evaluate(lines, bookings, true);
    var withoutCosts = FixedPriceCalculation.evaluate(lines, bookings, false);

    assertThat(withCosts.total().calculatedCostEuro()).isEqualByComparingTo("4800.00");
    assertThat(withCosts.total().bookedCostEuro()).isEqualByComparingTo("600.00");
    assertThat(withoutCosts.total().calculatedCostEuro()).isNull();
    assertThat(withoutCosts.total().bookedCostEuro()).isNull();
  }

  /** A calculated line without a rate leaves the calculated total open rather than understating it. */
  @Test
  void leaves_the_calculated_cost_open_where_a_line_has_no_rate() {
    var lines = List.of(
        new FixedPriceCalculation.Line(1, 10, "4711/01", "Konzept", SENIOR, "Senior", Duration.ofHours(80),
            new BigDecimal("60.00")),
        new FixedPriceCalculation.Line(2, 20, "4711/02", "Umsetzung", PROFESSIONAL, "Professional",
            Duration.ofHours(10), null));

    var result = FixedPriceCalculation.evaluate(lines, List.of(), true);

    assertThat(result.total().calculatedCostEuro()).isNull();
  }

  private static FixedPriceCalculation.Line line(long id, long suborderId, String sign, long categoryId,
                                                 String categoryName, int hours) {
    return new FixedPriceCalculation.Line(id, suborderId, sign, sign, categoryId, categoryName,
        Duration.ofHours(hours), null);
  }

  private static FixedPriceCalculation.Booking booking(long suborderId, List<Long> path, String sign, int hours,
                                                       Long categoryId, String categoryName) {
    return new FixedPriceCalculation.Booking(suborderId, path, sign, sign, Duration.ofHours(hours), categoryId,
        categoryName, null);
  }
}
