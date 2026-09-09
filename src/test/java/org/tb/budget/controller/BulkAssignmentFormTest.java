package org.tb.budget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.BulkAssignmentEmployee;

/**
 * The choice of people offers only those who booked in the current selection (#953), so a choice
 * made earlier can fall out of the offer. It is then dropped rather than kept: a kept choice would
 * narrow the run to a person the select does not even show any more.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BulkAssignmentFormTest {

  @Test
  public void keeps_the_people_the_selection_still_offers() {
    var form = form(1L, 2L);

    form.retainEmployees(List.of(employee(1L), employee(2L), employee(3L)));

    assertThat(form.getEmployeeIds()).containsExactly(1L, 2L);
  }

  @Test
  public void drops_the_people_the_selection_no_longer_offers() {
    var form = form(1L, 2L);

    form.retainEmployees(List.of(employee(2L)));

    assertThat(form.getEmployeeIds()).containsExactly(2L);
  }

  @Test
  public void drops_every_choice_when_the_selection_offers_nobody() {
    var form = form(1L, 2L);

    form.retainEmployees(List.of());

    assertThat(form.getEmployeeIds()).isEmpty();
  }

  @Test
  public void leaves_an_unmade_choice_alone() {
    var form = form();

    form.retainEmployees(List.of(employee(1L)));

    assertThat(form.getEmployeeIds()).isEmpty();
  }

  /** The choice is optional, so it must not have a say in whether the run may start. */
  @Test
  public void stays_complete_without_a_choice_of_people() {
    var form = form();
    form.setCustomerorderSign("CO");
    form.setFrom(LocalDate.of(2026, 1, 1));
    form.setUntil(LocalDate.of(2026, 12, 31));
    form.setTargetBudgetId(7L);

    assertThat(form.isComplete()).isTrue();
    assertThat(form.toData().employeeIds()).isEmpty();
  }

  private static BulkAssignmentForm form(Long... employeeIds) {
    var form = new BulkAssignmentForm();
    form.getEmployeeIds().addAll(List.of(employeeIds));
    return form;
  }

  private static BulkAssignmentEmployee employee(long id) {
    return new BulkAssignmentEmployee(id, "e" + id, "Person " + id);
  }

}
