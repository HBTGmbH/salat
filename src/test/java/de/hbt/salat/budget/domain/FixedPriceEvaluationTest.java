package de.hbt.salat.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.budget.domain.FixedPriceEvaluation.Gap;

/**
 * What an hour of a fixed-price plan is worth (#1405). The figures of the issue: a fixed price of
 * 48.000 EUR calculated with 400 hours, 160 of them booked at a progress of 40 %.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class FixedPriceEvaluationTest {

  @Test
  void computes_the_calculated_rate_from_the_fixed_price_and_the_calculated_hours() {
    var evaluation = evaluation("48000", 400, 160, 40.0);

    assertThat(evaluation.calculatedRate().euroPerHour()).isEqualByComparingTo("120.00");
  }

  /**
   * "Realized so far" divides the flat rates fallen due by the booked hours (#1435), "expected at
   * completion" the fixed price by the projected ones. The mockup of #1435: 48.000 EUR, 160 h at
   * 40 %, 16.000 EUR billed.
   */
  @Test
  void computes_the_realized_rate_from_the_flat_rates_fallen_due_and_the_expected_one_from_the_fixed_price() {
    var evaluation = evaluation("48000", "16000", 400, 160, 40.0);

    assertThat(evaluation.realizedRateSoFar().euroPerHour()).isEqualByComparingTo("100.00");
    // 160 h at 40 % project to 400 h
    assertThat(evaluation.projectedHours()).isEqualTo(Duration.ofHours(400));
    assertThat(evaluation.expectedRateAtCompletion().euroPerHour()).isEqualByComparingTo("120.00");
    // 48.000 x 40 % = 19.200 EUR, what the gross profit so far is read against
    assertThat(evaluation.earnedByProgressEuro()).isEqualByComparingTo("19200.00");
  }

  /** Nothing billed yet is a rate of 0 EUR, and it needs neither a fixed price nor a progress. */
  @Test
  void reports_a_realized_rate_of_zero_before_anything_was_billed() {
    assertThat(evaluation("48000", "0", 400, 160, 40.0).realizedRateSoFar().euroPerHour())
        .isEqualByComparingTo("0.00");
    assertThat(evaluation("0", "4000", 400, 160, null).realizedRateSoFar().euroPerHour())
        .isEqualByComparingTo("25.00");
  }

  /** Without a progress entry or without bookings there is no rate, but the reason. */
  @Test
  void names_the_reason_instead_of_dividing_by_zero() {
    assertThat(evaluation("48000", 400, 160, null).expectedRateAtCompletion().gap()).isEqualTo(Gap.NO_PROGRESS);
    assertThat(evaluation("48000", 400, 160, 0.0).expectedRateAtCompletion().gap()).isEqualTo(Gap.NO_PROGRESS);
    assertThat(evaluation("48000", 400, 0, 40.0).realizedRateSoFar().gap()).isEqualTo(Gap.NO_BOOKINGS);
    assertThat(evaluation("48000", 400, 0, 40.0).expectedRateAtCompletion().gap()).isEqualTo(Gap.NO_BOOKINGS);
    assertThat(evaluation("48000", 0, 160, 40.0).calculatedRate().gap()).isEqualTo(Gap.NO_CALCULATION);
    assertThat(evaluation("0", 400, 160, 40.0).calculatedRate().gap()).isEqualTo(Gap.NO_FIXED_PRICE);
    assertThat(evaluation("0", 400, 160, 40.0).expectedRateAtCompletion().gap()).isEqualTo(Gap.NO_FIXED_PRICE);
  }

  @Test
  void reports_gross_profit_only_with_costs() {
    var total = new FixedPriceCalculationRow(null, null, null, null, Duration.ofHours(400), Duration.ofHours(160),
        new BigDecimal("24000.00"), new BigDecimal("9000.00"));
    var withCosts = new FixedPriceEvaluation(List.of(), total, new BigDecimal("48000"), BigDecimal.ZERO,
        LocalDate.of(2026, 6, 30), 40.0, ProgressStatus.ON_TRACK, true);
    var withoutCosts = new FixedPriceEvaluation(List.of(), total, new BigDecimal("48000"), BigDecimal.ZERO,
        LocalDate.of(2026, 6, 30), 40.0, ProgressStatus.ON_TRACK, false);

    assertThat(withCosts.calculatedGrossProfitEuro()).isEqualByComparingTo("24000.00");
    // 19.200 EUR earned by progress less 9.000 EUR booked cost
    assertThat(withCosts.grossProfitSoFarEuro()).isEqualByComparingTo("10200.00");
    assertThat(withoutCosts.calculatedGrossProfitEuro()).isNull();
    assertThat(withoutCosts.grossProfitSoFarEuro()).isNull();
  }

  private static FixedPriceEvaluation evaluation(String fixedPrice, int calculatedHours, int bookedHours,
                                                 Double progress) {
    return evaluation(fixedPrice, "0", calculatedHours, bookedHours, progress);
  }

  private static FixedPriceEvaluation evaluation(String fixedPrice, String realized, int calculatedHours,
                                                 int bookedHours, Double progress) {
    return new FixedPriceEvaluation(List.of(), total(calculatedHours, bookedHours), new BigDecimal(fixedPrice),
        new BigDecimal(realized), LocalDate.of(2026, 6, 30), progress, ProgressStatus.UNKNOWN, false);
  }

  private static FixedPriceCalculationRow total(int calculatedHours, int bookedHours) {
    return new FixedPriceCalculationRow(null, null, null, null, Duration.ofHours(calculatedHours),
        Duration.ofHours(bookedHours), null, null);
  }
}
