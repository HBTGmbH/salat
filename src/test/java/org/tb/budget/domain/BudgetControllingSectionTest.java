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

/**
 * A section covers exactly one budget period, so which columns make sense follows from its kind
 * rather than from the whole view (#905).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetControllingSectionTest {

  private static final LocalDateRange YEAR =
      new LocalDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

  @Test
  public void should_offer_a_budget_column_only_where_there_is_a_budget() {
    assertThat(section(SectionKind.ORDER_LEVEL, row().build()).hasBudgetColumn()).isTrue();
    assertThat(section(SectionKind.SUBORDER_LEVEL, row().build()).hasBudgetColumn()).isTrue();
    assertThat(section(SectionKind.UNPLANNED, row().build()).hasBudgetColumn()).isFalse();
  }

  /** An empty column of dashes says nothing, so it only appears when something went over budget. */
  @Test
  public void should_offer_the_overrun_column_only_when_something_went_over_budget() {
    var overrun = row().budgetEuro(new BigDecimal("100")).revenueEuro(new BigDecimal("150")).build();
    var within = row().budgetEuro(new BigDecimal("100")).revenueEuro(new BigDecimal("50")).build();

    assertThat(section(SectionKind.ORDER_LEVEL, overrun).hasOverrunData()).isTrue();
    assertThat(section(SectionKind.ORDER_LEVEL, within).hasOverrunData()).isFalse();
  }

  /** Bookings without a budget cannot exceed one. */
  @Test
  public void should_not_offer_the_overrun_column_without_a_budget() {
    assertThat(section(SectionKind.UNPLANNED, row().build()).hasOverrunData()).isFalse();
  }

  @Test
  public void should_report_planned_data_when_a_row_has_planned_hours() {
    assertThat(section(SectionKind.ORDER_LEVEL,
        row().plannedHours(Duration.ofHours(40)).build()).hasPlannedData()).isTrue();
    assertThat(section(SectionKind.ORDER_LEVEL, row().build()).hasPlannedData()).isFalse();
  }

  /** Planned hours only on the total describe the customer order, not the suborder breakdown. */
  @Test
  public void should_ignore_planned_hours_that_only_the_total_carries() {
    var section = new BudgetControllingSection(SectionKind.ORDER_LEVEL, YEAR, List.of(), null, null,
        List.of(new BudgetControllingGroup(null, null, List.of(row().build()), null)),
        row().plannedHours(Duration.ofHours(40)).build());

    assertThat(section.hasPlannedData()).isFalse();
  }

  @Test
  public void should_be_worth_showing_only_when_its_total_says_something() {
    assertThat(section(SectionKind.ORDER_LEVEL, row().build()).hasContent()).isFalse();

    var withTime = new BudgetControllingSection(SectionKind.ORDER_LEVEL, YEAR, List.of(), null, null,
        List.of(new BudgetControllingGroup(null, null, List.of(row().build()), null)),
        row().bookedHours(Duration.ofHours(8)).build());
    assertThat(withTime.hasContent()).isTrue();
  }

  /** The header of that column carries a date and is the widest of the table — not worth dashes. */
  @Test
  public void should_offer_the_column_of_hours_booked_before_the_window_only_when_there_are_any() {
    var before = row().bookedHoursBeforeWindow(Duration.ofHours(8)).build();

    assertThat(section(SectionKind.ORDER_LEVEL, before).hasBookedBeforeWindowData()).isTrue();
    assertThat(section(SectionKind.ORDER_LEVEL, row().build()).hasBookedBeforeWindowData()).isFalse();
  }

  /** Zero hours before the window is the same as none — nothing had been booked yet. */
  @Test
  public void should_not_count_zero_hours_before_the_window_as_data() {
    var zero = row().bookedHoursBeforeWindow(Duration.ZERO).build();

    assertThat(section(SectionKind.ORDER_LEVEL, zero).hasBookedBeforeWindowData()).isFalse();
  }

  /**
   * Unlike the planned hours the total counts here: it is a line of the table like any other, and
   * the column is shown for the whole section.
   */
  @Test
  public void should_offer_the_column_when_only_the_total_was_booked_before_the_window() {
    var section = new BudgetControllingSection(SectionKind.ORDER_LEVEL, YEAR, List.of(), null, null,
        List.of(new BudgetControllingGroup(null, null, List.of(row().build()), null)),
        row().bookedHoursBeforeWindow(Duration.ofHours(8)).build());

    assertThat(section.hasBookedBeforeWindowData()).isTrue();
  }

  /** A margin needs a revenue to divide by, so without one the column is dashes throughout. */
  @Test
  public void should_offer_the_margin_column_only_where_a_margin_can_be_computed() {
    var withMargin = row().revenueEuro(new BigDecimal("100")).costEuro(new BigDecimal("60")).build();
    var withoutRevenue = row().costEuro(new BigDecimal("60")).build();

    assertThat(section(SectionKind.ORDER_LEVEL, withMargin).hasGrossProfitMarginData()).isTrue();
    assertThat(section(SectionKind.ORDER_LEVEL, withoutRevenue).hasGrossProfitMarginData()).isFalse();
  }

  @Test
  public void should_report_subtotals_only_where_groups_carry_them() {
    var grouped = new BudgetControllingSection(SectionKind.SUBORDER_LEVEL, YEAR, List.of("plan"), null, null,
        List.of(new BudgetControllingGroup("co/01", "plan", List.of(row().build()),
            row().budgetEuro(new BigDecimal("100")).build())),
        row().build());

    assertThat(grouped.hasSubtotals()).isTrue();
    assertThat(section(SectionKind.ORDER_LEVEL, row().build()).hasSubtotals()).isFalse();
  }

  private static BudgetControllingSection section(SectionKind kind, BudgetControllingRow row) {
    return new BudgetControllingSection(kind, kind == SectionKind.UNPLANNED ? null : YEAR, List.of(), null, null,
        List.of(new BudgetControllingGroup(null, null, List.of(row), null)),
        row().build());
  }

  private static BudgetControllingRow.BudgetControllingRowBuilder row() {
    return BudgetControllingRow.builder()
        .sign("co/01").label("label")
        .plannedHours(Duration.ZERO).bookedHours(Duration.ZERO);
  }
}
