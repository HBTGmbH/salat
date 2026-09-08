package org.tb.budget.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.BudgetBackfillCounts;
import org.tb.budget.domain.BudgetBackfillOrderResult;
import org.tb.budget.domain.BudgetBackfillResult;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetBackfillRowViewHelperTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);

  @Test
  public void should_format_the_examined_period_and_the_hours_of_an_order() {
    var row = BudgetBackfillRowViewHelper.from(orderResult("CO",
        new BudgetBackfillCounts(3, Duration.ofMinutes(150)), BudgetBackfillCounts.NONE));

    assertThat(row.label()).isEqualTo("CO");
    assertThat(row.description()).isEqualTo("CO description");
    assertThat(row.period()).isEqualTo("01.01.2026 – 31.12.2026");
    assertThat(row.assignedBookings()).isEqualTo(3);
    assertThat(row.assignedHours()).isEqualTo("2:30");
  }

  /** An outcome that did not occur must not read as a number — 0:00 looks like a measurement. */
  @Test
  public void should_show_a_dash_for_an_outcome_that_did_not_occur() {
    var row = BudgetBackfillRowViewHelper.from(orderResult("CO",
        new BudgetBackfillCounts(1, Duration.ofHours(1)), BudgetBackfillCounts.NONE));

    assertThat(row.ambiguousBookings()).isZero();
    assertThat(row.ambiguousHours()).isEqualTo("—");
  }

  @Test
  public void should_sum_the_orders_into_the_total_row_without_claiming_a_period() {
    var result = new BudgetBackfillResult(List.of(
        orderResult("CO", new BudgetBackfillCounts(2, Duration.ofHours(2)), BudgetBackfillCounts.NONE),
        orderResult("OTHER", new BudgetBackfillCounts(1, Duration.ofMinutes(30)),
            new BudgetBackfillCounts(4, Duration.ofHours(4)))));

    var totals = BudgetBackfillRowViewHelper.totals(result, "Summe");

    assertThat(totals.label()).isEqualTo("Summe");
    assertThat(totals.period()).isNull();
    assertThat(totals.assignedBookings()).isEqualTo(3);
    assertThat(totals.assignedHours()).isEqualTo("2:30");
    assertThat(totals.ambiguousBookings()).isEqualTo(4);
    assertThat(totals.ambiguousHours()).isEqualTo("4:00");
  }

  private static BudgetBackfillOrderResult orderResult(
      String sign, BudgetBackfillCounts assigned, BudgetBackfillCounts ambiguous) {
    return new BudgetBackfillOrderResult(sign, sign + " description", JAN, DEC,
        assigned, ambiguous, BudgetBackfillCounts.NONE, BudgetBackfillCounts.NONE);
  }

}
