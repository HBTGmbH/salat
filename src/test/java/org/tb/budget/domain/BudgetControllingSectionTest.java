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
    var section = new BudgetControllingSection(SectionKind.ORDER_LEVEL, YEAR, List.of(),
        List.of(new BudgetControllingGroup(null, null, List.of(row().build()), null)),
        row().plannedHours(Duration.ofHours(40)).build());

    assertThat(section.hasPlannedData()).isFalse();
  }

  @Test
  public void should_be_worth_showing_only_when_its_total_says_something() {
    assertThat(section(SectionKind.ORDER_LEVEL, row().build()).hasContent()).isFalse();

    var withTime = new BudgetControllingSection(SectionKind.ORDER_LEVEL, YEAR, List.of(),
        List.of(new BudgetControllingGroup(null, null, List.of(row().build()), null)),
        row().bookedHours(Duration.ofHours(8)).build());
    assertThat(withTime.hasContent()).isTrue();
  }

  @Test
  public void should_report_subtotals_only_where_groups_carry_them() {
    var grouped = new BudgetControllingSection(SectionKind.SUBORDER_LEVEL, YEAR, List.of("plan"),
        List.of(new BudgetControllingGroup("co/01", "plan", List.of(row().build()),
            row().budgetEuro(new BigDecimal("100")).build())),
        row().build());

    assertThat(grouped.hasSubtotals()).isTrue();
    assertThat(section(SectionKind.ORDER_LEVEL, row().build()).hasSubtotals()).isFalse();
  }

  private static BudgetControllingSection section(SectionKind kind, BudgetControllingRow row) {
    return new BudgetControllingSection(kind, kind == SectionKind.UNPLANNED ? null : YEAR, List.of(),
        List.of(new BudgetControllingGroup(null, null, List.of(row), null)),
        row().build());
  }

  private static BudgetControllingRow.BudgetControllingRowBuilder row() {
    return BudgetControllingRow.builder()
        .sign("co/01").label("label")
        .plannedHours(Duration.ZERO).bookedHours(Duration.ZERO);
  }
}
