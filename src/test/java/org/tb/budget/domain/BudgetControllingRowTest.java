package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.common.LocalDateRange;

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

  @Test
  public void should_report_no_overrun_without_a_budget() {
    assertThat(row().revenueEuro(new BigDecimal("800")).build().hasOverrun()).isFalse();
  }

  @Test
  public void should_list_every_gap_of_an_unplanned_line() {
    var gaps = row().periods(List.of(
        new LocalDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)),
        new LocalDateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31)))).build();

    assertThat(gaps.periodsFormatted()).isEqualTo("01.01.2026 – 28.02.2026, 01.07.2026 – 31.12.2026");
  }

  @Test
  public void should_render_a_dash_without_periods() {
    assertThat(row().build().periodsFormatted()).isEqualTo("—");
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
