package de.hbt.salat.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetControllingRowTest {

  /** Booked time, budget or planned hours — any one of them makes the line worth showing (#901). */
  @Test
  public void should_treat_a_line_as_empty_only_without_time_budget_and_planned_hours() {
    assertThat(row().build().hasContent()).isFalse();

    assertThat(row().bookedHours(Duration.ofHours(3)).build().hasContent()).isTrue();
    assertThat(row().plannedHours(Duration.ofHours(40)).build().hasContent()).isTrue();
    assertThat(row().budgetEuro(new BigDecimal("100")).build().hasContent()).isTrue();
  }

  /** Revenue and cost are derived from booked time, so they cannot make an otherwise empty line real. */
  @Test
  public void should_still_be_empty_when_only_derived_figures_are_set() {
    var derived = row().revenueEuro(new BigDecimal("500")).costEuro(new BigDecimal("400")).build();

    assertThat(derived.hasContent()).isFalse();
  }

  // --- flat rates (#972) -----------------------------------------------------------------------

  /**
   * A flat rate is not derived from booked time — it is due whether or not anybody booked. A line
   * carrying nothing else therefore has something to report, unlike one with only revenue on it.
   */
  @Test
  public void should_treat_a_line_with_only_a_flat_rate_as_content() {
    assertThat(row().flatRateRevenueEuro(new BigDecimal("1000")).build().hasContent()).isTrue();
  }

  @Test
  public void should_add_hourly_and_flat_rate_revenue_into_the_total() {
    var mixed = row().revenueEuro(new BigDecimal("800")).flatRateRevenueEuro(new BigDecimal("200")).build();

    assertThat(mixed.totalRevenueEuro()).isEqualByComparingTo("1000");
    assertThat(mixed.hasRevenue()).isTrue();
    assertThat(mixed.hasFlatRateRevenue()).isTrue();
  }

  /** Without either source there is no total at all, which keeps "no data" apart from a real zero. */
  @Test
  public void should_report_no_total_revenue_without_either_source() {
    assertThat(row().build().totalRevenueEuro()).isNull();
    assertThat(row().build().hasTotalRevenue()).isFalse();
  }

  @Test
  public void should_measure_the_budget_against_the_total_revenue() {
    var mixed = row().budgetEuro(new BigDecimal("1000"))
        .revenueEuro(new BigDecimal("400")).flatRateRevenueEuro(new BigDecimal("600")).build();

    assertThat(mixed.budgetUsedPercent()).isCloseTo(100.0, within(0.01));
    assertThat(mixed.hasOverrun()).isFalse();
  }

  /** A flat rate can be what pushes a plan over, so the overrun has to see it. */
  @Test
  public void should_report_an_overrun_a_flat_rate_causes() {
    var over = row().budgetEuro(new BigDecimal("1000"))
        .revenueEuro(new BigDecimal("800")).flatRateRevenueEuro(new BigDecimal("500")).build();

    assertThat(over.overrunEuro()).isEqualByComparingTo("300");
  }

  /**
   * The line of a flat rate itself has no cost, so its margin would always read 100 % — a number
   * that says nothing next to the lines that do carry cost.
   */
  @Test
  public void should_report_no_margin_on_a_flat_rate_line() {
    var flatRateLine = row().flatRateRevenueEuro(new BigDecimal("1000"))
        .costEuro(BigDecimal.ZERO).flatRate(true).build();

    assertThat(flatRateLine.hasGrossProfitMargin()).isFalse();
    // The amount itself stays readable; only the percentage is dropped.
    assertThat(flatRateLine.grossProfitEuro()).isEqualByComparingTo("1000");
  }

  /** An agreed amount is not worked, so it lifts the gross profit and the margin of an aggregate. */
  @Test
  public void should_count_a_flat_rate_towards_gross_profit_and_margin() {
    var mixed = row().revenueEuro(new BigDecimal("800")).flatRateRevenueEuro(new BigDecimal("200"))
        .costEuro(new BigDecimal("600")).build();

    assertThat(mixed.grossProfitEuro()).isEqualByComparingTo("400");
    assertThat(mixed.grossProfitMarginPercent()).isCloseTo(40.0, within(0.01));
  }

  /** Going over budget is normal, so it is reported as an amount rather than only as a percentage. */
  @Test
  public void should_report_the_amount_a_budget_was_exceeded_by() {
    var over = row().budgetEuro(new BigDecimal("700800")).revenueEuro(new BigDecimal("1022534.38")).build();

    assertThat(over.hasOverrun()).isTrue();
    assertThat(over.overrunEuro()).isEqualByComparingTo("321734.38");
    assertThat(over.budgetUsedPercent()).isCloseTo(145.9, within(0.1));
  }

  @Test
  public void should_report_no_overrun_within_the_budget() {
    var within = row().budgetEuro(new BigDecimal("1000")).revenueEuro(new BigDecimal("800")).build();

    assertThat(within.hasOverrun()).isFalse();
    assertThat(within.overrunEuro()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  /** The sign gets separators to read by; the cell keeps it on one line. */
  @Test
  public void should_space_out_the_complete_order_sign() {
    assertThat(row().sign("1612/01/D").build().signFormatted()).isEqualTo("1612 / 01 / D");
  }

  /** The total line has no sign — the template puts its own label there. */
  @Test
  public void should_report_no_sign_for_a_line_without_one() {
    assertThat(BudgetControllingRow.builder().build().signFormatted()).isNull();
  }

  @Test
  public void should_report_no_overrun_without_a_budget() {
    assertThat(row().revenueEuro(new BigDecimal("800")).build().hasOverrun()).isFalse();
  }

  /**
   * The mockup of #1406: 300 h on an invoiceable suborder at 95 EUR/h, 60 h on an internal one. The
   * average rate reads the billable hours only, the effective rate every hour booked.
   */
  @Test
  public void should_count_unbilled_hours_in_the_effective_rate_but_not_in_the_average_one() {
    var billable = row().bookedHours(Duration.ofHours(300)).billableHours(Duration.ofHours(300))
        .revenueEuro(new BigDecimal("28500")).flatRateRevenueEuro(BigDecimal.ZERO).build();
    var internal = row().bookedHours(Duration.ofHours(60)).billableHours(Duration.ZERO)
        .revenueEuro(BigDecimal.ZERO).flatRateRevenueEuro(BigDecimal.ZERO).build();

    var total = BudgetControllingRow.sum(null, null, java.util.List.of(billable, internal), null, false);

    assertThat(billable.averageHourlyRateEuro()).isEqualByComparingTo("95.00");
    assertThat(billable.effectiveHourlyRateEuro()).isEqualByComparingTo("95.00");
    assertThat(internal.hasAverageHourlyRate()).isFalse();
    assertThat(internal.effectiveHourlyRateEuro()).isEqualByComparingTo("0.00");
    assertThat(total.averageHourlyRateEuro()).isEqualByComparingTo("95.00");
    assertThat(total.effectiveHourlyRateEuro()).isEqualByComparingTo("79.17");
  }

  /**
   * Flat rates count towards both rates (#1436), so the two differ in the hours they divide by and
   * nothing else. The example of #1436: 100 h booked, 80 h of them billable at 100 EUR/h, 10.000 EUR
   * of flat rates fallen due.
   */
  @Test
  public void should_count_flat_rates_in_both_rates() {
    var line = row().bookedHours(Duration.ofHours(100)).billableHours(Duration.ofHours(80))
        .revenueEuro(new BigDecimal("8000")).flatRateRevenueEuro(new BigDecimal("10000")).build();

    assertThat(line.averageHourlyRateEuro()).isEqualByComparingTo("225.00");
    assertThat(line.effectiveHourlyRateEuro()).isEqualByComparingTo("180.00");
  }

  /** Flat rates without a billable hour leave the average rate nothing to divide by (#1436). */
  @Test
  public void should_have_no_average_rate_without_billable_hours_even_with_flat_rates() {
    var line = row().bookedHours(Duration.ofHours(10)).billableHours(Duration.ZERO)
        .revenueEuro(BigDecimal.ZERO).flatRateRevenueEuro(new BigDecimal("1000")).build();

    assertThat(line.hasAverageHourlyRate()).isFalse();
    assertThat(line.effectiveHourlyRateEuro()).isEqualByComparingTo("100.00");
  }

  /** Without hours there is nothing to divide by: the view shows a dash. */
  @Test
  public void should_have_no_hourly_rate_without_hours() {
    var flatRateOnly = row().flatRateRevenueEuro(new BigDecimal("1000")).build();

    assertThat(flatRateOnly.effectiveHourlyRateEuro()).isNull();
    assertThat(flatRateOnly.averageHourlyRateEuro()).isNull();
  }

  private static org.assertj.core.data.Offset<Double> within(double d) {
    return org.assertj.core.data.Offset.offset(d);
  }

  private static BudgetControllingRow.BudgetControllingRowBuilder row() {
    return BudgetControllingRow.builder()
        .sign("co/01").label("label")
        .plannedHours(Duration.ZERO).bookedHours(Duration.ZERO);
  }
}
