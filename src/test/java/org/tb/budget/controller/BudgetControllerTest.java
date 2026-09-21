package org.tb.budget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.BudgetEmployeeSign;
import org.tb.budget.domain.OrderBudget;
import org.tb.common.domain.AuditedEntity;

/**
 * The "Mitarbeitende" column of the plan overview (#964) reads one aggregate for the whole page,
 * and the page hands every plan a cell — including the plans nobody booked on, which the aggregate
 * does not mention at all.
 *
 * <p>Nothing is truncated on the way (#1047): the column lists every sign and the badges wrap
 * inside the cell. The alphabetical order comes from the query ({@code BudgetEmployeeQueryTest})
 * and is not re-established here.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetControllerTest {

  @Test
  public void gives_every_plan_of_the_page_its_signs() {
    var plan = budget(1L);
    var aggregate = Map.of(1L, List.of(sign(1L, "abc"), sign(1L, "def")));

    var byBudget = BudgetController.signsByBudget(List.of(plan), aggregate);

    assertThat(byBudget.get(1L))
        .extracting(BudgetEmployeeSign::employeeSign)
        .containsExactly("abc", "def");
  }

  /** A row with thirty signs is listed in full; the badges wrap rather than being counted. */
  @Test
  public void hands_on_every_sign_however_many_there_are() {
    var plan = budget(1L);
    var signs = new ArrayList<BudgetEmployeeSign>();
    for (int i = 0; i < 30; i++) {
      signs.add(sign(1L, "p" + i));
    }

    var byBudget = BudgetController.signsByBudget(List.of(plan), Map.of(1L, signs));

    assertThat(byBudget.get(1L)).hasSize(30);
  }

  /**
   * The cell iterates over what it is given, so a plan missing from the aggregate has to arrive as
   * an empty list rather than as {@code null}.
   */
  @Test
  public void leaves_a_plan_without_bookings_with_an_empty_cell() {
    var booked = budget(1L);
    var unbooked = budget(2L);
    var aggregate = Map.of(1L, List.of(sign(1L, "abc")));

    var byBudget = BudgetController.signsByBudget(List.of(booked, unbooked), aggregate);

    assertThat(byBudget).containsKey(2L);
    assertThat(byBudget.get(2L)).isEmpty();
  }

  @Test
  public void leaves_every_cell_empty_when_nobody_booked_on_any_plan() {
    var byBudget = BudgetController.signsByBudget(List.of(budget(1L), budget(2L)), Map.of());

    assertThat(byBudget).hasSize(2);
    assertThat(byBudget.values()).allSatisfy(signs -> assertThat(signs).isEmpty());
  }

  private static BudgetEmployeeSign sign(long budgetId, String sign) {
    var name = Character.toUpperCase(sign.charAt(0)) + sign.substring(1) + " Person";
    return new BudgetEmployeeSign(budgetId, sign, name);
  }

  private static OrderBudget budget(long id) {
    var budget = new OrderBudget();
    setId(budget, id);
    return budget;
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }

}
