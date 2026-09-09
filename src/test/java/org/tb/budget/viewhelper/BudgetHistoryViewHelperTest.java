package org.tb.budget.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.BudgetControllingGroup;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.BudgetControllingSection;
import org.tb.budget.domain.BudgetHistory;
import org.tb.budget.domain.SectionKind;
import org.tb.common.LocalDateRange;

/**
 * The info box explains a number the reader cannot otherwise place (#917): 40.000 EUR left in April
 * means something different for a plan worth 50.000 than for one worth 500.000. Its figures must
 * therefore add up with the table below it, or it makes things worse rather than better.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetHistoryViewHelperTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final LocalDate APR = LocalDate.of(2026, 4, 1);

  /** The chain the box shows: total − before = start, start − window = end. */
  @Test
  public void should_derive_the_remaining_budget_step_by_step() {
    var history = new BudgetHistory(new BigDecimal("100000"), new BigDecimal("60000"), JAN, DEC, true);

    var box = BudgetHistoryViewHelper.from(section(history, "25000"));

    assertThat(box.visible()).isTrue();
    assertThat(box.totalBudgetEuro()).isEqualByComparingTo("100000");
    assertThat(box.consumedBeforeEuro()).isEqualByComparingTo("60000");
    assertThat(box.availableAtWindowStartEuro()).isEqualByComparingTo("40000");
    assertThat(box.consumedInWindowEuro()).isEqualByComparingTo("25000");
    assertThat(box.availableAtWindowEndEuro()).isEqualByComparingTo("15000");
  }

  @Test
  public void should_show_the_validity_of_the_plan() {
    var box = BudgetHistoryViewHelper.from(
        section(new BudgetHistory(new BigDecimal("100"), BigDecimal.ZERO, JAN, DEC, true), "0"));

    assertThat(box.planPeriod()).isEqualTo("01.01.2026 – 31.12.2026");
  }

  /** For a plan that starts inside the window and consumed nothing, the box would only repeat itself. */
  @Test
  public void should_stay_hidden_for_a_plan_that_starts_inside_the_window() {
    var history = new BudgetHistory(new BigDecimal("100000"), BigDecimal.ZERO, APR, DEC, false);

    assertThat(BudgetHistoryViewHelper.from(section(history, "1000")).visible()).isFalse();
  }

  /** But a plan starting inside the window that already consumed something is worth explaining. */
  @Test
  public void should_appear_when_something_was_consumed_before_the_window() {
    var history = new BudgetHistory(new BigDecimal("100000"), new BigDecimal("500"), APR, DEC, false);

    assertThat(BudgetHistoryViewHelper.from(section(history, "1000")).visible()).isTrue();
  }

  @Test
  public void should_stay_hidden_for_a_section_without_any_plan() {
    var withoutBudget = new BudgetControllingSection(SectionKind.UNPLANNED, null, List.of(),
        List.of(new BudgetControllingGroup(null, null, List.of(), null)), row("800"), null);

    assertThat(BudgetHistoryViewHelper.from(withoutBudget).visible()).isFalse();
  }

  /** Spent past the end is a state of its own, so the box can say so instead of only showing a minus. */
  @Test
  public void should_report_an_exhausted_plan() {
    var history = new BudgetHistory(new BigDecimal("1000"), new BigDecimal("900"), JAN, DEC, true);

    var box = BudgetHistoryViewHelper.from(section(history, "500"));

    assertThat(box.availableAtWindowEndEuro()).isEqualByComparingTo("-400");
    assertThat(box.isExhausted()).isTrue();
  }

  @Test
  public void should_not_report_a_plan_within_its_budget_as_exhausted() {
    var history = new BudgetHistory(new BigDecimal("1000"), new BigDecimal("100"), JAN, DEC, true);

    assertThat(BudgetHistoryViewHelper.from(section(history, "500")).isExhausted()).isFalse();
  }

  /** A section without revenue must not break the chain. */
  @Test
  public void should_treat_a_missing_revenue_as_nothing_consumed() {
    var history = new BudgetHistory(new BigDecimal("1000"), new BigDecimal("100"), JAN, DEC, true);
    var section = new BudgetControllingSection(SectionKind.ORDER_LEVEL, new LocalDateRange(APR, DEC),
        List.of("plan"), List.of(new BudgetControllingGroup(null, null, List.of(), null)),
        BudgetControllingRow.builder().build(), history);

    var box = BudgetHistoryViewHelper.from(section);

    assertThat(box.consumedInWindowEuro()).isEqualByComparingTo("0");
    assertThat(box.availableAtWindowEndEuro()).isEqualByComparingTo("900");
  }

  // --- fixture ---------------------------------------------------------------------------------

  private static BudgetControllingSection section(BudgetHistory history, String revenueInWindow) {
    return new BudgetControllingSection(SectionKind.ORDER_LEVEL, new LocalDateRange(APR, DEC),
        List.of("plan"), List.of(new BudgetControllingGroup(null, null, List.of(), null)),
        row(revenueInWindow), history);
  }

  private static BudgetControllingRow row(String revenueEuro) {
    return BudgetControllingRow.builder().revenueEuro(new BigDecimal(revenueEuro)).build();
  }

}
