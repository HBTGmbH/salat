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
            .bookedHours(Duration.ofHours(10)).revenueBeforeWindowEuro(new BigDecimal("200"))
            .plannedHours(Duration.ofHours(20))
            .budgetEuro(new BigDecimal("1000")).revenueEuro(new BigDecimal("400"))
            .costEuro(new BigDecimal("100")).build()),
        section(SectionKind.SUBORDER_LEVEL, row()
            .bookedHours(Duration.ofHours(5)).revenueBeforeWindowEuro(new BigDecimal("100"))
            .plannedHours(Duration.ofHours(30))
            .budgetEuro(new BigDecimal("500")).revenueEuro(new BigDecimal("200"))
            .flatRateRevenueEuro(new BigDecimal("50"))
            .costEuro(new BigDecimal("60")).build()));

    var total = result.total();

    assertThat(total.bookedHours()).isEqualTo(Duration.ofHours(15));
    assertThat(total.revenueBeforeWindowEuro()).isEqualByComparingTo("300");
    assertThat(total.plannedHours()).isEqualTo(Duration.ofHours(50));
    assertThat(total.revenueEuro()).isEqualByComparingTo("600");
    assertThat(total.flatRateRevenueEuro()).isEqualByComparingTo("50");
    assertThat(total.costEuro()).isEqualByComparingTo("160");
  }

  /**
   * The point of the line: percentages come out of the summed amounts, not out of the percentages of
   * the sections. Averaging those would let a line that booked an hour weigh as much as one that
   * booked a thousand.
   */
  @Test
  public void should_derive_the_consumption_of_planned_hours_from_the_sums() {
    var result = result(
        section(SectionKind.ORDER_LEVEL, row()
            .plannedHours(Duration.ofHours(10)).bookedHours(Duration.ofHours(10)).build()),
        section(SectionKind.SUBORDER_LEVEL, row()
            .plannedHours(Duration.ofHours(90)).bookedHours(Duration.ofHours(9)).build()));

    assertThat(result.total().bookedPercent()).isCloseTo(19.0, within(0.001));
  }

  /** Unassigned work was still worked and still cost money, so it belongs in the order's figures. */
  @Test
  public void should_count_the_unplanned_section_towards_the_revenue_and_the_hours() {
    var result = result(
        section(SectionKind.ORDER_LEVEL,
            row().budgetEuro(new BigDecimal("1000")).revenueEuro(new BigDecimal("500"))
                .bookedHours(Duration.ofHours(5)).build()),
        section(SectionKind.UNPLANNED,
            row().revenueEuro(new BigDecimal("250")).bookedHours(Duration.ofHours(3)).build()));

    var total = result.total();

    assertThat(total.totalRevenueEuro()).isEqualByComparingTo("750");
    assertThat(total.bookedHours()).isEqualTo(Duration.ofHours(8));
  }

  /**
   * The plans of an order have their own periods and scopes, and the unplanned section answers to
   * none of them. Adding the amounts up would produce a budget nobody agreed to, so the line carries
   * none and the view shows no budget columns for it.
   */
  @Test
  public void should_carry_no_budget_however_many_plans_the_order_has() {
    var result = result(
        section(SectionKind.ORDER_LEVEL,
            row().budgetEuro(new BigDecimal("1000")).revenueEuro(new BigDecimal("500")).build()),
        section(SectionKind.SUBORDER_LEVEL,
            row().budgetEuro(new BigDecimal("500")).revenueEuro(new BigDecimal("250")).build()));

    assertThat(result.total().budgetEuro()).isNull();
    assertThat(result.totalColumns().budget()).isFalse();
    assertThat(result.totalColumns().overrun()).isFalse();
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

  /**
   * A figure has to keep the header it had above, so the total offers every column any section does
   * — the budget columns excepted, which it has no figures for.
   */
  @Test
  public void should_offer_every_other_column_any_of_its_sections_offers() {
    var withPlanned = section(SectionKind.ORDER_LEVEL,
        row().plannedHours(Duration.ofHours(10))
            .revenueBeforeWindowEuro(new BigDecimal("800")).build());
    var withFlatRate = section(SectionKind.UNPLANNED,
        row().flatRateRevenueEuro(new BigDecimal("50")).build());

    var columns = result(withPlanned, withFlatRate).totalColumns();

    assertThat(columns.planned()).isTrue();
    assertThat(columns.flatRate()).isTrue();
    // The revenue earned before the window explains the budget columns, and those are gone.
    assertThat(columns.revenueBeforeWindow()).isFalse();
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
        .revenueBeforeWindowEuro(BigDecimal.ZERO)
        .bookedHours(Duration.ZERO)
        .revenueEuro(BigDecimal.ZERO)
        .flatRateRevenueEuro(BigDecimal.ZERO)
        .costEuro(BigDecimal.ZERO);
  }
}
