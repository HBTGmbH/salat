package org.tb.budget.domain;

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

  private static org.assertj.core.data.Offset<Double> within(double d) {
    return org.assertj.core.data.Offset.offset(d);
  }

  private static BudgetControllingRow.BudgetControllingRowBuilder row() {
    return BudgetControllingRow.builder()
        .sign("co/01").label("label")
        .plannedHours(Duration.ZERO).bookedHours(Duration.ZERO);
  }
}
