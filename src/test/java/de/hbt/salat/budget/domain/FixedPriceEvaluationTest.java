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
   * "Effective so far" takes the share of the fixed price the progress stands for, not the flat rates
   * fallen due — decided for #1405. It therefore equals the rate expected at completion: (price × p) ÷ h
   * is price ÷ (h ÷ p).
   */
  @Test
  void computes_the_effective_rate_so_far_from_the_share_of_the_fixed_price_the_progress_stands_for() {
    var evaluation = evaluation("48000", 400, 200, 40.0);

    // 48.000 x 40 % = 19.200 EUR earned by 200 h
    assertThat(evaluation.earnedByProgressEuro()).isEqualByComparingTo("19200.00");
    assertThat(evaluation.effectiveRateSoFar().euroPerHour()).isEqualByComparingTo("96.00");
    // 200 h at 40 % project to 500 h
    assertThat(evaluation.projectedHours()).isEqualTo(Duration.ofHours(500));
    assertThat(evaluation.expectedRateAtCompletion().euroPerHour()).isEqualByComparingTo("96.00");
  }

  @Test
  void ignores_the_flat_rates_fallen_due_for_the_effective_rate() {
    var dueAhead = new FixedPriceEvaluation(List.of(), total(400, 160), new BigDecimal("48000"),
        new BigDecimal("48000"), LocalDate.of(2026, 6, 30), 40.0, ProgressStatus.ON_TRACK, false);

    assertThat(dueAhead.effectiveRateSoFar().euroPerHour()).isEqualByComparingTo("120.00");
  }

  /** Without a progress entry or without bookings there is no rate, but the reason. */
  @Test
  void names_the_reason_instead_of_dividing_by_zero() {
    assertThat(evaluation("48000", 400, 160, null).effectiveRateSoFar().gap()).isEqualTo(Gap.NO_PROGRESS);
    assertThat(evaluation("48000", 400, 160, null).expectedRateAtCompletion().gap()).isEqualTo(Gap.NO_PROGRESS);
    assertThat(evaluation("48000", 400, 160, 0.0).expectedRateAtCompletion().gap()).isEqualTo(Gap.NO_PROGRESS);
    assertThat(evaluation("48000", 400, 0, 40.0).effectiveRateSoFar().gap()).isEqualTo(Gap.NO_BOOKINGS);
    assertThat(evaluation("48000", 400, 0, 40.0).expectedRateAtCompletion().gap()).isEqualTo(Gap.NO_BOOKINGS);
    assertThat(evaluation("48000", 0, 160, 40.0).calculatedRate().gap()).isEqualTo(Gap.NO_CALCULATION);
    assertThat(evaluation("0", 400, 160, 40.0).calculatedRate().gap()).isEqualTo(Gap.NO_FIXED_PRICE);
    assertThat(evaluation("0", 400, 160, 40.0).effectiveRateSoFar().hasValue()).isFalse();
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
    return new FixedPriceEvaluation(List.of(), total(calculatedHours, bookedHours), new BigDecimal(fixedPrice),
        BigDecimal.ZERO, LocalDate.of(2026, 6, 30), progress, ProgressStatus.UNKNOWN, false);
  }

  private static FixedPriceCalculationRow total(int calculatedHours, int bookedHours) {
    return new FixedPriceCalculationRow(null, null, null, null, Duration.ofHours(calculatedHours),
        Duration.ofHours(bookedHours), null, null);
  }
}
