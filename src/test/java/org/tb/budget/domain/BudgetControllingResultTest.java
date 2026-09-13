package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.common.LocalDateRange;

/**
 * The total over all sections of one customer order (#779). It is the line the segment listing shows
 * per order, so what it sums and how it derives its percentages is the same question in both views.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetControllingResultTest {

  private static final LocalDateRange YEAR =
      new LocalDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

  @Test
  public void should_add_up_hours_revenue_and_cost_of_every_section() {
    var result = result(
        section(SectionKind.ORDER_LEVEL, row()
            .bookedHours(Duration.ofHours(10)).bookedHoursBeforeWindow(Duration.ofHours(2))
            .plannedHours(Duration.ofHours(20))
            .budgetEuro(new BigDecimal("1000")).revenueEuro(new BigDecimal("400"))
            .costEuro(new BigDecimal("100")).build()),
        section(SectionKind.SUBORDER_LEVEL, row()
            .bookedHours(Duration.ofHours(5)).bookedHoursBeforeWindow(Duration.ofHours(1))
            .plannedHours(Duration.ofHours(30))
            .budgetEuro(new BigDecimal("500")).revenueEuro(new BigDecimal("200"))
            .flatRateRevenueEuro(new BigDecimal("50"))
            .costEuro(new BigDecimal("60")).build()));

    var total = result.total();

    assertThat(total.bookedHours()).isEqualTo(Duration.ofHours(15));
    assertThat(total.bookedHoursBeforeWindow()).isEqualTo(Duration.ofHours(3));
    assertThat(total.plannedHours()).isEqualTo(Duration.ofHours(50));
    assertThat(total.budgetEuro()).isEqualByComparingTo("1500");
    assertThat(total.revenueEuro()).isEqualByComparingTo("600");
    assertThat(total.flatRateRevenueEuro()).isEqualByComparingTo("50");
    assertThat(total.costEuro()).isEqualByComparingTo("160");
  }

  /**
   * The point of the line: percentages come out of the summed amounts, not out of the percentages of
   * the sections. Averaging those would let a section with a hundred euro of budget weigh as much as
   * one with a hundred thousand.
   */
  @Test
  public void should_derive_the_budget_utilization_from_the_sums_and_not_average_the_sections() {
    // 100 % in the one section, 10 % in the other — their mean would be 55 %.
    var result = result(
        section(SectionKind.ORDER_LEVEL,
            row().budgetEuro(new BigDecimal("100")).revenueEuro(new BigDecimal("100")).build()),
        section(SectionKind.SUBORDER_LEVEL,
            row().budgetEuro(new BigDecimal("900")).revenueEuro(new BigDecimal("90")).build()));

    // 190 of 1000.
    assertThat(result.total().budgetUsedPercent()).isCloseTo(19.0, within(0.001));
  }

  /** Same rule for the consumption of planned hours. */
  @Test
  public void should_derive_the_consumption_of_planned_hours_from_the_sums() {
    var result = result(
        section(SectionKind.ORDER_LEVEL, row()
            .plannedHours(Duration.ofHours(10)).bookedHours(Duration.ofHours(10)).build()),
        section(SectionKind.SUBORDER_LEVEL, row()
            .plannedHours(Duration.ofHours(90)).bookedHours(Duration.ofHours(9)).build()));

    assertThat(result.total().bookedPercent()).isCloseTo(19.0, within(0.001));
  }

  /**
   * Unassigned work was still worked and still cost money, but it answers to no plan. It therefore
   * raises the revenue of the order without raising its budget — the utilization has to feel that,
   * otherwise the order would look better than the sum of what happened on it.
   */
  @Test
  public void should_count_the_unplanned_section_towards_revenue_but_not_towards_the_budget() {
    var result = result(
        section(SectionKind.ORDER_LEVEL,
            row().budgetEuro(new BigDecimal("1000")).revenueEuro(new BigDecimal("500"))
                .bookedHours(Duration.ofHours(5)).build()),
        section(SectionKind.UNPLANNED,
            row().revenueEuro(new BigDecimal("250")).bookedHours(Duration.ofHours(3)).build()));

    var total = result.total();

    assertThat(total.budgetEuro()).isEqualByComparingTo("1000");
    assertThat(total.totalRevenueEuro()).isEqualByComparingTo("750");
    assertThat(total.bookedHours()).isEqualTo(Duration.ofHours(8));
    assertThat(total.budgetUsedPercent()).isCloseTo(75.0, within(0.001));
  }

  /** Without a single budget anywhere there is nothing to report in the budget columns. */
  @Test
  public void should_report_no_budget_at_all_when_no_section_carries_one() {
    var result = result(section(SectionKind.UNPLANNED,
        row().revenueEuro(new BigDecimal("250")).build()));

    assertThat(result.total().budgetEuro()).isNull();
  }

  /** Costs stay away from someone who may not see them, and a summed zero is not "no cost". */
  @Test
  public void should_leave_the_cost_empty_when_the_sections_report_none() {
    var result = result(section(SectionKind.ORDER_LEVEL,
        row().revenueEuro(new BigDecimal("250")).costEuro(null).build()));

    assertThat(result.total().costEuro()).isNull();
  }

  /** With one section its own total already is the order's, and repeating it explains nothing. */
  @Test
  public void should_offer_a_total_only_where_there_is_more_than_one_section() {
    assertThat(result(section(SectionKind.ORDER_LEVEL, row().build())).hasTotal()).isFalse();
    assertThat(result(section(SectionKind.ORDER_LEVEL, row().build()),
        section(SectionKind.UNPLANNED, row().build())).hasTotal()).isTrue();
  }

  /** A figure has to keep the header it had above, so the total offers every column any section does. */
  @Test
  public void should_offer_every_column_any_of_its_sections_offers() {
    var withBudget = section(SectionKind.ORDER_LEVEL,
        row().budgetEuro(new BigDecimal("100")).revenueEuro(new BigDecimal("150")).build());
    var withFlatRate = section(SectionKind.UNPLANNED,
        row().flatRateRevenueEuro(new BigDecimal("50")).build());

    var columns = result(withBudget, withFlatRate).totalColumns();

    assertThat(columns.budget()).isTrue();
    assertThat(columns.overrun()).isTrue();
    assertThat(columns.flatRate()).isTrue();
    assertThat(columns.planned()).isFalse();
  }

  private static BudgetControllingResult result(BudgetControllingSection... sections) {
    return new BudgetControllingResult("co", "order", null, null, YEAR, List.of(sections));
  }

  /** A section whose total is the given row — the detail rows do not matter for a sum over sections. */
  private static BudgetControllingSection section(SectionKind kind, BudgetControllingRow total) {
    return new BudgetControllingSection(kind, YEAR, List.of(), null, null,
        List.of(new BudgetControllingGroup(null, null, null, List.of(total), null, null, null)), total);
  }

  private static BudgetControllingRow.BudgetControllingRowBuilder row() {
    return BudgetControllingRow.builder()
        .plannedHours(Duration.ZERO)
        .bookedHoursBeforeWindow(Duration.ZERO)
        .bookedHours(Duration.ZERO)
        .revenueEuro(BigDecimal.ZERO)
        .flatRateRevenueEuro(BigDecimal.ZERO)
        .costEuro(BigDecimal.ZERO);
  }
}
