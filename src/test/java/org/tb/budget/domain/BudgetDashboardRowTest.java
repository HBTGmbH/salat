package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The dashboard shows a plan red once it is over budget and amber once it has reached its own
 * {@code alertThresholdPercent}. The threshold is optional, and it used to be the only thing that
 * could colour a row at all, so a plan at 150% without one went unflagged (#899).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetDashboardRowTest {

  @Test
  public void should_be_over_budget_above_100_percent_even_without_an_alert_threshold() {
    var row = row(null, 150.0);

    assertThat(row.isOverBudget()).isTrue();
    assertThat(row.isAboveThreshold()).isFalse();
  }

  @Test
  public void should_not_be_over_budget_at_exactly_100_percent() {
    assertThat(row(null, 100.0).isOverBudget()).isFalse();
  }

  @Test
  public void should_warn_but_not_flag_over_budget_at_the_alert_threshold() {
    var row = row(80, 85.0);

    assertThat(row.isAboveThreshold()).isTrue();
    assertThat(row.isOverBudget()).isFalse();
  }

  @Test
  public void should_not_warn_below_the_alert_threshold() {
    assertThat(row(80, 79.0).isAboveThreshold()).isFalse();
  }

  /** No threshold means no warning at all — not a warning at some assumed default. */
  @Test
  public void should_not_warn_without_a_configured_threshold() {
    assertThat(row(null, 99.0).isAboveThreshold()).isFalse();
    assertThat(row(null, 99.0).isOverBudget()).isFalse();
  }

  @Test
  public void should_not_be_over_budget_without_a_budget() {
    assertThat(noBudget(null, 150.0).isOverBudget()).isFalse();
  }

  /**
   * Exactly one of "Überbucht", "Alert", "OK" and "—" must apply. Without a budget amount the row
   * shows "—", so neither of the two states that would also render must be true.
   */
  @Test
  public void should_be_neither_over_budget_nor_above_threshold_without_a_budget() {
    var noBudget = noBudget(0, 0.0);

    assertThat(noBudget.isOverBudget()).isFalse();
    assertThat(noBudget.isAboveThreshold()).isFalse();
  }

  private static BudgetDashboardRow noBudget(Integer alertThresholdPercent, double utilizationPercent) {
    return new BudgetDashboardRow(1L, "plan", "co", "order", LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 12, 31), BigDecimal.ZERO, BigDecimal.TEN,
        alertThresholdPercent, utilizationPercent);
  }

  @Test
  public void should_cap_the_progress_bar_at_100_percent() {
    assertThat(row(null, 150.0).progressBarPercent()).isEqualTo(100.0);
  }

  private static BudgetDashboardRow row(Integer alertThresholdPercent, double utilizationPercent) {
    return new BudgetDashboardRow(1L, "plan", "co", "order",
        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
        new BigDecimal("1000"), new BigDecimal("1500"),
        alertThresholdPercent, utilizationPercent);
  }

}
