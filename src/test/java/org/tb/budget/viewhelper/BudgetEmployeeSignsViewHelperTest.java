package org.tb.budget.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.BudgetEmployeeMinutes;
import org.tb.budget.viewhelper.BudgetEmployeeSignsViewHelper.Sign;

/**
 * The "Mitarbeitende" cell of the plan overview (#964). Formatting and truncation only — the order
 * comes from the query ({@code BudgetEmployeeQueryTest}) and is not re-established here.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetEmployeeSignsViewHelperTest {

  @Test
  public void carries_name_and_hours_into_the_title_of_each_sign() {
    var cell = BudgetEmployeeSignsViewHelper.from(List.of(minutes("abc", 480)));

    assertThat(cell.shown()).singleElement().satisfies(sign -> {
      assertThat(sign.employeeSign()).isEqualTo("abc");
      assertThat(sign.employeeName()).isEqualTo("Abc Person");
      assertThat(sign.hours()).isEqualTo("8:00");
    });
    assertThat(cell.hasMore()).isFalse();
  }

  /** The order the query delivered — most hours first — is handed on, not redone. */
  @Test
  public void keeps_the_order_it_was_given() {
    var cell = BudgetEmployeeSignsViewHelper.from(
        List.of(minutes("def", 480), minutes("abc", 120), minutes("ghi", 60)));

    assertThat(cell.shown()).extracting(Sign::employeeSign).containsExactly("def", "abc", "ghi");
  }

  /** A row with thirty signs would push every other column off the table. */
  @Test
  public void counts_the_rest_beyond_the_limit_instead_of_listing_it() {
    var employees = new ArrayList<BudgetEmployeeMinutes>();
    for (int i = 0; i < BudgetEmployeeSignsViewHelper.MAX_SIGNS + 3; i++) {
      employees.add(minutes("p" + i, 60));
    }

    var cell = BudgetEmployeeSignsViewHelper.from(employees);

    assertThat(cell.shown()).hasSize(BudgetEmployeeSignsViewHelper.MAX_SIGNS);
    assertThat(cell.more()).isEqualTo(3);
    assertThat(cell.hasMore()).isTrue();
  }

  @Test
  public void shows_everybody_when_the_limit_is_exactly_reached() {
    var employees = new ArrayList<BudgetEmployeeMinutes>();
    for (int i = 0; i < BudgetEmployeeSignsViewHelper.MAX_SIGNS; i++) {
      employees.add(minutes("p" + i, 60));
    }

    var cell = BudgetEmployeeSignsViewHelper.from(employees);

    assertThat(cell.shown()).hasSize(BudgetEmployeeSignsViewHelper.MAX_SIGNS);
    assertThat(cell.hasMore()).isFalse();
  }

  /** A zero or a dash there would read like something went wrong. */
  @Test
  public void leaves_the_cell_empty_for_a_plan_without_bookings() {
    assertThat(BudgetEmployeeSignsViewHelper.from(List.of()).isEmpty()).isTrue();
    assertThat(BudgetEmployeeSignsViewHelper.from(List.of()).hasMore()).isFalse();
  }

  /** The overview asks one query for every plan of the page; a plan without rows gets no entry. */
  @Test
  public void treats_a_plan_missing_from_the_aggregate_as_one_without_bookings() {
    assertThat(BudgetEmployeeSignsViewHelper.from(null).isEmpty()).isTrue();
  }

  private static BudgetEmployeeMinutes minutes(String sign, long minutes) {
    var name = Character.toUpperCase(sign.charAt(0)) + sign.substring(1) + " Person";
    return new BudgetEmployeeMinutes(1L, sign, name, minutes);
  }

}
