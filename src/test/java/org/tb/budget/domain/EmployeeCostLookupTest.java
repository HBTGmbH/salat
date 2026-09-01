package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The lookup replaces the assignment and cost repository queries, so these tests pin the fallback
 * hierarchy and the date filter that those queries expressed in JPQL.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeeCostLookupTest {

  private static final LocalDate DATE = LocalDate.of(2026, 6, 15);

  @Test
  public void should_prefer_the_suborder_specific_assignment_over_the_general_one() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment("emp", null, "general"), assignment("emp", "so", "specific")),
        List.of(cost("general", 100), cost("specific", 200)));

    assertThat(cents(lookup, "emp", "so")).isEqualTo(200);
  }

  @Test
  public void should_fall_back_to_the_general_assignment_for_another_suborder() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment("emp", null, "general"), assignment("emp", "other", "specific")),
        List.of(cost("general", 100), cost("specific", 200)));

    assertThat(cents(lookup, "emp", "so")).isEqualTo(100);
  }

  @Test
  public void should_skip_the_suborder_step_when_no_suborder_is_given() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment("emp", "so", "specific"), assignment("emp", null, "general")),
        List.of(cost("general", 100), cost("specific", 200)));

    assertThat(cents(lookup, "emp", null)).isEqualTo(100);
  }

  @Test
  public void should_ignore_assignments_of_other_employees() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment("other", null, "general")),
        List.of(cost("general", 100)));

    assertThat(lookup.findEffectiveCost("emp", "so", DATE)).isEmpty();
  }

  @Test
  public void should_only_match_assignments_valid_on_the_given_date() {
    var expired = assignment("emp", null, "general");
    expired.setValidUntil(DATE.minusDays(1));

    var lookup = EmployeeCostLookup.of(List.of(expired), List.of(cost("general", 100)));

    assertThat(lookup.findEffectiveCost("emp", null, DATE)).isEmpty();
  }

  @Test
  public void should_only_match_costs_valid_on_the_given_date() {
    var expired = cost("general", 100);
    expired.setValidUntil(DATE.minusDays(1));
    var current = cost("general", 200);

    assertThat(cents(EmployeeCostLookup.of(
        List.of(assignment("emp", null, "general")), List.of(expired, current)), "emp", null))
        .isEqualTo(200);
  }

  @Test
  public void should_return_empty_when_the_assignment_names_an_unknown_cost() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment("emp", null, "missing")), List.of(cost("general", 100)));

    assertThat(lookup.findEffectiveCost("emp", null, DATE)).isEmpty();
  }

  @Test
  public void should_include_the_boundaries_of_the_validity_range() {
    var assignment = assignment("emp", null, "general");
    assignment.setValidFrom(DATE);
    assignment.setValidUntil(DATE);
    var cost = cost("general", 100);
    cost.setValidFrom(DATE);
    cost.setValidUntil(DATE);

    assertThat(cents(EmployeeCostLookup.of(List.of(assignment), List.of(cost)), "emp", null))
        .isEqualTo(100);
  }

  @Test
  public void should_return_empty_for_an_empty_lookup() {
    assertThat(EmployeeCostLookup.of(List.of(), List.of()).findEffectiveCost("emp", "so", DATE)).isEmpty();
  }

  private static Integer cents(EmployeeCostLookup lookup, String employeeSign, String suborderSign) {
    return lookup.findEffectiveCost(employeeSign, suborderSign, DATE)
        .map(EmployeeCost::getCostCentsPerHour)
        .orElse(null);
  }

  private static EmployeeCostAssignment assignment(String employeeSign, String suborderSign, String costName) {
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployeeSign(employeeSign);
    assignment.setSuborderSign(suborderSign);
    assignment.setEmployeeCostName(costName);
    assignment.setValidFrom(LocalDate.of(2026, 1, 1));
    assignment.setValidUntil(LocalDate.of(2026, 12, 31));
    return assignment;
  }

  private static EmployeeCost cost(String name, int cents) {
    var cost = new EmployeeCost();
    cost.setName(name);
    cost.setCostCentsPerHour(cents);
    cost.setValidFrom(LocalDate.of(2026, 1, 1));
    cost.setValidUntil(LocalDate.of(2026, 12, 31));
    return cost;
  }

}
