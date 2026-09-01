package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The planned-hours column and the consumption derived from it are only shown when some suborder
 * actually carries planned hours — otherwise both columns are nothing but dashes (#901).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetControllingResultTest {

  @Test
  public void should_report_planned_data_when_a_suborder_has_planned_hours() {
    var result = result(row(Duration.ZERO), row(Duration.ofHours(40)));

    assertThat(result.hasPlannedData()).isTrue();
  }

  @Test
  public void should_report_no_planned_data_when_every_suborder_has_zero_planned_hours() {
    var result = result(row(Duration.ZERO), row(Duration.ZERO));

    assertThat(result.hasPlannedData()).isFalse();
  }

  @Test
  public void should_report_no_planned_data_when_planned_hours_are_missing_entirely() {
    var result = result(row(null));

    assertThat(result.hasPlannedData()).isFalse();
  }

  @Test
  public void should_report_no_planned_data_without_any_suborder() {
    assertThat(result().hasPlannedData()).isFalse();
  }

  /**
   * The total row carries the planned hours of the customer order. It alone does not bring the
   * columns back, because they describe the suborder breakdown.
   */
  @Test
  public void should_ignore_planned_hours_that_only_the_total_row_carries() {
    var result = new BudgetControllingResult(row(Duration.ofHours(40)), List.of(row(Duration.ZERO)), false);

    assertThat(result.hasPlannedData()).isFalse();
  }

  /** Booked time, budget or planned hours — any one of them makes the row worth showing. */
  @Test
  public void should_treat_a_row_as_empty_only_without_time_budget_and_planned_hours() {
    assertThat(empty(Duration.ZERO, Duration.ZERO, BigDecimal.ZERO).hasContent()).isFalse();

    assertThat(empty(Duration.ofHours(3), Duration.ZERO, BigDecimal.ZERO).hasContent()).isTrue();
    assertThat(empty(Duration.ZERO, Duration.ofHours(40), BigDecimal.ZERO).hasContent()).isTrue();
    assertThat(empty(Duration.ZERO, Duration.ZERO, new BigDecimal("100")).hasContent()).isTrue();
  }

  private static BudgetControllingRow empty(Duration booked, Duration planned, BigDecimal budget) {
    return new BudgetControllingRow("co/01", "label", true, planned, booked,
        budget, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, null, null, null);
  }

  /** Revenue and cost are derived from booked time, so they cannot make an otherwise empty row real. */
  @Test
  public void should_still_be_empty_when_only_derived_figures_are_zero() {
    var derivedOnly = new BudgetControllingRow("co/01", "label", true, Duration.ZERO, Duration.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
        Duration.ZERO, BigDecimal.ZERO, null, null, null, null);

    assertThat(derivedOnly.hasContent()).isFalse();
  }

  private static BudgetControllingResult result(BudgetControllingRow... suborderRows) {
    return new BudgetControllingResult(row(Duration.ZERO), List.of(suborderRows), false);
  }

  private static BudgetControllingRow row(Duration plannedHours) {
    return new BudgetControllingRow("co/01", "label", true, plannedHours, Duration.ofHours(8),
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null,
        null, null, null, null, null, null);
  }

}
