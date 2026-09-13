package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * Which optional columns a table shows (#779). Three views share the decision, so it lives in one
 * place: a column that is dashes everywhere stays away, and a column any part of the table needs
 * appears in all of it.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetControllingColumnsTest {

  @Test
  public void should_show_nothing_optional_for_lines_that_carry_nothing() {
    var columns = BudgetControllingColumns.of(List.of(row().build()));

    assertThat(columns).isEqualTo(BudgetControllingColumns.NONE);
  }

  @Test
  public void should_show_a_column_as_soon_as_one_line_has_something_for_it() {
    var columns = BudgetControllingColumns.of(List.of(
        row().build(),
        row().bookedHoursBeforeWindow(Duration.ofHours(3)).build(),
        row().plannedHours(Duration.ofHours(10)).build(),
        row().flatRateRevenueEuro(new BigDecimal("20")).build(),
        row().budgetEuro(new BigDecimal("100")).revenueEuro(new BigDecimal("150")).build()));

    assertThat(columns.bookedBeforeWindow()).isTrue();
    assertThat(columns.planned()).isTrue();
    assertThat(columns.flatRate()).isTrue();
    assertThat(columns.budget()).isTrue();
    assertThat(columns.overrun()).isTrue();
  }

  /** Staying within the budget is the normal case, and a column of zeroes says nothing about it. */
  @Test
  public void should_not_show_the_overrun_column_while_everything_stays_within_its_budget() {
    var columns = BudgetControllingColumns.of(List.of(
        row().budgetEuro(new BigDecimal("100")).revenueEuro(new BigDecimal("50")).build()));

    assertThat(columns.budget()).isTrue();
    assertThat(columns.overrun()).isFalse();
  }

  /** A margin needs a profit and a revenue to divide it by. */
  @Test
  public void should_show_the_margin_column_only_where_a_margin_can_be_computed() {
    var earning = row().revenueEuro(new BigDecimal("100")).costEuro(new BigDecimal("60")).build();

    assertThat(BudgetControllingColumns.of(List.of(earning)).grossProfitMargin()).isTrue();
    assertThat(BudgetControllingColumns.of(List.of(row().build())).grossProfitMargin()).isFalse();
  }

  @Test
  public void should_take_over_every_column_of_the_sets_it_is_merged_with() {
    var withBudget = new BudgetControllingColumns(false, false, false, true, true, false);
    var withPlanned = new BudgetControllingColumns(false, true, false, false, false, false);

    var merged = withBudget.merge(withPlanned);

    assertThat(merged).isEqualTo(new BudgetControllingColumns(false, true, false, true, true, false));
  }

  @Test
  public void should_survive_being_merged_with_nothing() {
    var columns = new BudgetControllingColumns(true, false, false, false, false, false);

    assertThat(columns.merge(null)).isEqualTo(columns);
    assertThat(BudgetControllingColumns.NONE.merge(columns)).isEqualTo(columns);
  }

  private static BudgetControllingRow.BudgetControllingRowBuilder row() {
    return BudgetControllingRow.builder()
        .plannedHours(Duration.ZERO)
        .bookedHoursBeforeWindow(Duration.ZERO)
        .bookedHours(Duration.ZERO)
        .revenueEuro(BigDecimal.ZERO)
        .flatRateRevenueEuro(BigDecimal.ZERO);
  }
}
