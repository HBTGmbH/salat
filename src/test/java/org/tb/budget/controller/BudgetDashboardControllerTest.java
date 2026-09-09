package org.tb.budget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.common.domain.AuditedEntity;
import org.tb.employee.domain.Employee;

/**
 * The responsible filter of the dashboard offers only the responsibles of the chosen segment
 * (#952), so a remembered choice can fall out of the offer. It is then dropped rather than applied:
 * the select would show "all" while the list stayed filtered by a value nobody can see — the same
 * invisible filtering the filter guards against when it is not offered at all.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetDashboardControllerTest {

  @Test
  public void keeps_a_responsible_the_filter_offers() {
    var offered = List.of(employee(1L), employee(2L));

    assertThat(BudgetDashboardController.offeredResponsibleId(2L, offered)).isEqualTo(2L);
  }

  @Test
  public void drops_a_responsible_the_filter_does_not_offer() {
    var offered = List.of(employee(1L), employee(2L));

    assertThat(BudgetDashboardController.offeredResponsibleId(3L, offered)).isNull();
  }

  @Test
  public void drops_a_responsible_when_the_filter_offers_nobody() {
    assertThat(BudgetDashboardController.offeredResponsibleId(1L, List.of())).isNull();
  }

  @Test
  public void leaves_an_unset_filter_unset() {
    assertThat(BudgetDashboardController.offeredResponsibleId(null, List.of(employee(1L)))).isNull();
  }

  private static Employee employee(long id) {
    var employee = new Employee();
    setId(employee, id);
    return employee;
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
