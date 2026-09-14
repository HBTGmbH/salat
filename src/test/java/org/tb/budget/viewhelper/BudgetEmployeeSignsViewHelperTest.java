package org.tb.budget.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.BudgetEmployeeSign;
import org.tb.budget.viewhelper.BudgetEmployeeSignsViewHelper.Sign;

/**
 * The "Mitarbeitende" cell of the plan overview (#964). Truncation only — the alphabetical order
 * comes from the query ({@code BudgetEmployeeQueryTest}) and is not re-established here.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetEmployeeSignsViewHelperTest {

  @Test
  public void carries_the_name_into_the_title_of_each_sign() {
    var cell = BudgetEmployeeSignsViewHelper.from(List.of(sign("abc")));

    assertThat(cell.shown()).singleElement().satisfies(shown -> {
      assertThat(shown.employeeSign()).isEqualTo("abc");
      assertThat(shown.employeeName()).isEqualTo("Abc Person");
    });
    assertThat(cell.hasMore()).isFalse();
  }

  /** The order the query delivered — alphabetical by sign — is handed on, not redone. */
  @Test
  public void keeps_the_order_it_was_given() {
    var cell = BudgetEmployeeSignsViewHelper.from(List.of(sign("abc"), sign("def"), sign("ghi")));

    assertThat(cell.shown()).extracting(Sign::employeeSign).containsExactly("abc", "def", "ghi");
  }

  /** A row with thirty signs would push every other column off the table. */
  @Test
  public void counts_the_rest_beyond_the_limit_instead_of_listing_it() {
    var employees = new ArrayList<BudgetEmployeeSign>();
    for (int i = 0; i < BudgetEmployeeSignsViewHelper.MAX_SIGNS + 3; i++) {
      employees.add(sign("p" + i));
    }

    var cell = BudgetEmployeeSignsViewHelper.from(employees);

    assertThat(cell.shown()).hasSize(BudgetEmployeeSignsViewHelper.MAX_SIGNS);
    assertThat(cell.more()).isEqualTo(3);
    assertThat(cell.hasMore()).isTrue();
  }

  @Test
  public void shows_everybody_when_the_limit_is_exactly_reached() {
    var employees = new ArrayList<BudgetEmployeeSign>();
    for (int i = 0; i < BudgetEmployeeSignsViewHelper.MAX_SIGNS; i++) {
      employees.add(sign("p" + i));
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

  private static BudgetEmployeeSign sign(String sign) {
    var name = Character.toUpperCase(sign.charAt(0)) + sign.substring(1) + " Person";
    return new BudgetEmployeeSign(1L, sign, name);
  }

}
