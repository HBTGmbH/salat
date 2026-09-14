package org.tb.budget.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.AppliedRate;
import org.tb.budget.domain.AppliedRates;
import org.tb.budget.domain.AssignedBooking;
import org.tb.budget.domain.BudgetEmployees;

/**
 * Formatting only. The order of the rows comes from the query (#997,
 * {@code AssignedBookingRepositoryTest}) and is deliberately not re-established here — hence no
 * sorting test in this class.
 *
 * <p>The rates are handed in rather than resolved here (#964): they come out of the same pass that
 * builds the "Mitarbeitende" card, so a row and the card cannot name different rates for the same
 * work.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class AssignedTimereportViewHelperTest {

  private static final LocalDate JAN_01 = LocalDate.of(2026, 1, 1);

  @Test
  public void formats_the_columns_of_a_booking() {
    var rows = AssignedTimereportViewHelper.from(List.of(booking(1, JAN_01)),
        rates(1, new AppliedRate("Senior", 9500, 14000, true, true)));

    assertThat(rows).singleElement().satisfies(row -> {
      assertThat(row.id()).isEqualTo(1L);
      assertThat(row.day()).isEqualTo("01.01.2026");
      assertThat(row.suborderSign()).isEqualTo("CO/01");
      assertThat(row.employeeSign()).isEqualTo("abc");
      assertThat(row.duration()).isEqualTo("2:30");
      assertThat(row.taskDescription()).isEqualTo("task");
      assertThat(row.rate().costName()).isEqualTo("Senior");
      assertThat(row.rate().costEuroPerHour()).isEqualByComparingTo("95.00");
      assertThat(row.rate().priceEuroPerHour()).isEqualByComparingTo("140.00");
      assertThat(row.rate().missingCost()).isFalse();
      assertThat(row.rate().missingPrice()).isFalse();
    });
  }

  @Test
  public void marks_a_booking_without_a_cost_rate() {
    var rows = AssignedTimereportViewHelper.from(List.of(booking(1, JAN_01)),
        rates(1, new AppliedRate(null, null, 14000, true, true)));

    assertThat(rows.getFirst().rate().missingCost()).isTrue();
    assertThat(rows.getFirst().rate().missingPrice()).isFalse();
  }

  @Test
  public void marks_a_booking_without_a_condition() {
    var rows = AssignedTimereportViewHelper.from(List.of(booking(1, JAN_01)),
        rates(1, new AppliedRate("Senior", 9500, null, true, true)));

    assertThat(rows.getFirst().rate().missingPrice()).isTrue();
  }

  /** 0,00 EUR/h is a deliberate statement, not a gap — it is shown and not marked. */
  @Test
  public void shows_a_condition_of_zero_euro_without_marking_it() {
    var rows = AssignedTimereportViewHelper.from(List.of(booking(1, JAN_01)),
        rates(1, new AppliedRate("Senior", 9500, 0, true, true)));

    assertThat(rows.getFirst().rate().priceEuroPerHour()).isEqualByComparingTo("0.00");
    assertThat(rows.getFirst().rate().missingPrice()).isFalse();
  }

  @Test
  public void marks_a_booking_on_a_suborder_that_is_not_invoiceable_instead_of_faulting_its_rate() {
    var rows = AssignedTimereportViewHelper.from(List.of(booking(1, JAN_01)),
        rates(1, new AppliedRate("Senior", 9500, null, false, true)));

    assertThat(rows.getFirst().rate().notInvoiceable()).isTrue();
    assertThat(rows.getFirst().rate().missingPrice()).isFalse();
  }

  /** A row the pass did not cover must not read as a row for which no rate applies. */
  @Test
  public void falls_back_to_no_rates_for_a_booking_the_pass_did_not_cover() {
    var rows = AssignedTimereportViewHelper.from(List.of(booking(1, JAN_01)),
        new AppliedRates(BudgetEmployees.of(List.of(), false), Map.of()));

    assertThat(rows.getFirst().rate().hasCost()).isFalse();
    assertThat(rows.getFirst().rate().missingCost()).isFalse();
  }

  /** The rows are handed on in the order they arrived — no second opinion about it. */
  @Test
  public void keeps_the_order_it_was_given() {
    var rows = AssignedTimereportViewHelper.from(List.of(
        booking(1, JAN_01.plusDays(5)),
        booking(2, JAN_01),
        booking(3, JAN_01.plusDays(2))), AppliedRates.none(true));

    assertThat(rows).extracting(AssignedTimereportViewHelper::id).containsExactly(1L, 2L, 3L);
  }

  @Test
  public void formats_an_empty_list_as_an_empty_list() {
    assertThat(AssignedTimereportViewHelper.from(List.<AssignedBooking>of(), AppliedRates.none(true)))
        .isEmpty();
  }

  private static AppliedRates rates(long timereportId, AppliedRate rate) {
    return new AppliedRates(BudgetEmployees.of(List.of(), rate.costsIncluded()),
        Map.of(timereportId, rate));
  }

  private static AssignedBooking booking(long id, LocalDate day) {
    return new AssignedBooking(id, day, 7L, "CO/01", "abc", "Abc Person",
        Duration.ofMinutes(150), "task");
  }

}
