package org.tb.budget.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.BudgetEmployee;
import org.tb.budget.domain.BudgetEmployees;
import org.tb.budget.domain.CostCategoryRate;

/**
 * The "Mitarbeitende" card as the template reads it (#964). Formatting only — the rows arrive
 * sorted by hours ({@code BudgetEmployeeServiceTest}) and are handed on in that order.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetEmployeesViewHelperTest {

  @Test
  public void formats_hours_and_rates_of_a_row() {
    var card = BudgetEmployeesViewHelper.from(BudgetEmployees.of(List.of(
        employee("abc", Duration.ofMinutes(510), 3,
            List.of(new CostCategoryRate("Senior", 9500)), List.of(14000))), true));

    assertThat(card.rows()).singleElement().satisfies(row -> {
      assertThat(row.employeeSign()).isEqualTo("abc");
      assertThat(row.employeeName()).isEqualTo("Abc Person");
      assertThat(row.hours()).isEqualTo("8:30");
      assertThat(row.bookings()).isEqualTo(3);
      assertThat(row.costs()).containsExactly(new CostCategoryRate("Senior", 9500));
      assertThat(row.costs().getFirst().euroPerHour()).isEqualByComparingTo("95.00");
      assertThat(row.pricesEuroPerHour()).singleElement().satisfies(price ->
          assertThat(price).isEqualByComparingTo("140.00"));
      assertThat(row.hasCosts()).isTrue();
      assertThat(row.hasPrices()).isTrue();
    });
  }

  /** Several rates are named side by side, in the order the service put them in. */
  @Test
  public void hands_on_every_rate_of_a_row() {
    var card = BudgetEmployeesViewHelper.from(BudgetEmployees.of(List.of(
        employee("abc", Duration.ofHours(8), 2,
            List.of(new CostCategoryRate("Senior", 9500), new CostCategoryRate("Junior", 6000)),
            List.of(14000, 15000))), true));

    assertThat(card.rows().getFirst().costs())
        .extracting(CostCategoryRate::name).containsExactly("Senior", "Junior");
    assertThat(card.rows().getFirst().pricesEuroPerHour()).hasSize(2);
  }

  /**
   * The row carries no "not invoiceable" mark: that says something about the suborder, not about
   * the person. It is reported for the plan as a whole, and row by row in the booking list.
   */
  @Test
  public void sums_the_hours_without_a_rate_and_says_there_is_something_to_report() {
    var card = BudgetEmployeesViewHelper.from(BudgetEmployees.of(List.of(
        new BudgetEmployee("abc", "Abc Person", 4, Duration.ofHours(10), List.of(), List.of(),
            Duration.ofHours(4), Duration.ofHours(3), Duration.ofHours(2))), true));

    assertThat(card.hoursWithoutCost()).isEqualTo("4:00");
    assertThat(card.hoursWithoutPrice()).isEqualTo("3:00");
    assertThat(card.hoursNotInvoiceable()).isEqualTo("2:00");
    assertThat(card.anyWithoutCost()).isTrue();
    assertThat(card.anyWithoutPrice()).isTrue();
    assertThat(card.anyNotInvoiceable()).isTrue();
    assertThat(card.hasFindings()).isTrue();
  }

  /** A card that does not report costs must not read as one on which no cost rate applies. */
  @Test
  public void reports_no_cost_side_at_all_where_costs_are_not_included() {
    var card = BudgetEmployeesViewHelper.from(BudgetEmployees.of(List.of(
        employee("abc", Duration.ofHours(8), 1, List.of(), List.of(14000))), false));

    assertThat(card.costsIncluded()).isFalse();
    assertThat(card.anyWithoutCost()).isFalse();
    assertThat(card.hasFindings()).isFalse();
  }

  @Test
  public void formats_an_empty_card_as_an_empty_card() {
    var card = BudgetEmployeesViewHelper.from(BudgetEmployees.of(List.of(), true));

    assertThat(card.isEmpty()).isTrue();
    assertThat(card.hasFindings()).isFalse();
  }

  private static BudgetEmployee employee(String sign, Duration duration, long bookings,
                                         List<CostCategoryRate> costs, List<Integer> prices) {
    var name = Character.toUpperCase(sign.charAt(0)) + sign.substring(1) + " Person";
    return new BudgetEmployee(sign, name, bookings, duration, costs, prices,
        Duration.ZERO, Duration.ZERO, Duration.ZERO);
  }

}
