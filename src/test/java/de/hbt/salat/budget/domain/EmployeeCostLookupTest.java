package de.hbt.salat.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.testutils.CostCategoryTestUtils;

/**
 * The lookup replaces the assignment and cost repository queries, so these tests pin the fallback
 * hierarchy and the date filter that those queries expressed in JPQL.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeeCostLookupTest {

  private static final LocalDate DATE = LocalDate.of(2026, 6, 15);
  private static final long EMP = 1L;
  private static final long OTHER = 2L;

  /** The suborders by sign — the assignments refer to them by id (#1205). */
  private static final Map<String, Long> SUBORDER_IDS = Map.of("so", 100L, "other", 101L);

  @Test
  public void should_prefer_the_suborder_specific_assignment_over_the_general_one() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment(EMP, null, "general"), assignment(EMP, "so", "specific")),
        List.of(cost("general", 100), cost("specific", 200)));

    assertThat(cents(lookup, EMP, "so")).isEqualTo(200);
  }

  @Test
  public void should_fall_back_to_the_general_assignment_for_another_suborder() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment(EMP, null, "general"), assignment(EMP, "other", "specific")),
        List.of(cost("general", 100), cost("specific", 200)));

    assertThat(cents(lookup, EMP, "so")).isEqualTo(100);
  }

  @Test
  public void should_skip_the_suborder_step_when_no_suborder_is_given() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment(EMP, "so", "specific"), assignment(EMP, null, "general")),
        List.of(cost("general", 100), cost("specific", 200)));

    assertThat(cents(lookup, EMP, null)).isEqualTo(100);
  }

  @Test
  public void should_ignore_assignments_of_other_employees() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment(OTHER, null, "general")),
        List.of(cost("general", 100)));

    assertThat(lookup.findEffectiveCost(EMP, SUBORDER_IDS.get("so"), OrderType.STANDARD, DATE)).isEmpty();
  }

  @Test
  public void should_only_match_assignments_valid_on_the_given_date() {
    var expired = assignment(EMP, null, "general");
    expired.setValidUntil(DATE.minusDays(1));

    var lookup = EmployeeCostLookup.of(List.of(expired), List.of(cost("general", 100)));

    assertThat(lookup.findEffectiveCost(EMP, null, OrderType.STANDARD, DATE)).isEmpty();
  }

  @Test
  public void should_only_match_costs_valid_on_the_given_date() {
    var expired = cost("general", 100);
    expired.setValidUntil(DATE.minusDays(1));
    var current = cost("general", 200);

    assertThat(cents(EmployeeCostLookup.of(
        List.of(assignment(EMP, null, "general")), List.of(expired, current)), EMP, null))
        .isEqualTo(200);
  }

  @Test
  public void should_return_empty_when_the_assignment_names_an_unknown_cost() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment(EMP, null, "missing")), List.of(cost("general", 100)));

    assertThat(lookup.findEffectiveCost(EMP, null, OrderType.STANDARD, DATE)).isEmpty();
  }

  @Test
  public void should_include_the_boundaries_of_the_validity_range() {
    var assignment = assignment(EMP, null, "general");
    assignment.setValidFrom(DATE);
    assignment.setValidUntil(DATE);
    var cost = cost("general", 100);
    cost.setValidFrom(DATE);
    cost.setValidUntil(DATE);

    assertThat(cents(EmployeeCostLookup.of(List.of(assignment), List.of(cost)), EMP, null))
        .isEqualTo(100);
  }

  @Test
  public void should_use_the_suborder_specific_assignment_of_a_standby_order() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment(EMP, null, "general"), assignment(EMP, "so", "standby")),
        List.of(cost("general", 100), cost("standby", 20)));

    assertThat(cents(lookup, EMP, "so", OrderType.BEREITSCHAFT)).isEqualTo(20);
  }

  @Test
  public void should_not_fall_back_to_the_general_assignment_for_a_standby_order() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment(EMP, null, "general")), List.of(cost("general", 100)));

    assertThat(lookup.findEffectiveCost(EMP, SUBORDER_IDS.get("so"), OrderType.BEREITSCHAFT, DATE)).isEmpty();
    // the very same constellation on a standard order does fall back
    assertThat(cents(lookup, EMP, "so")).isEqualTo(100);
  }

  /**
   * The person is matched by id (#968): an assignment still carrying the sign the person had before
   * a correction or an anonymization resolves unchanged, without anything following the sign.
   */
  @Test
  public void should_match_the_person_by_id_whatever_sign_the_assignment_was_stored_with() {
    var stale = assignment(EMP, null, "general");
    stale.setEmployeeSign("old-sign");

    assertThat(cents(EmployeeCostLookup.of(List.of(stale), List.of(cost("general", 100))), EMP, null))
        .isEqualTo(100);
  }

  /**
   * Assignment and rate period meet over the id of their category (#1209). The same category read
   * twice and renamed in between — what a rename between two reads amounts to — resolves unchanged;
   * the name decides nothing.
   */
  @Test
  public void should_match_the_category_by_id_whatever_name_it_carries() {
    var assignment = assignment(EMP, null, "general");
    var cost = cost("general", 100);
    cost.getCategory().setName("renamed");

    assertThat(cents(EmployeeCostLookup.of(List.of(assignment), List.of(cost)), EMP, null)).isEqualTo(100);
  }

  /** Two categories with names that differ only in case are two categories, as the names were before. */
  @Test
  public void should_tell_categories_apart_by_id_even_where_the_names_differ_only_in_case() {
    var lookup = EmployeeCostLookup.of(List.of(assignment(EMP, null, "Senior")),
        List.of(cost("senior", 100), cost("Senior", 200)));

    assertThat(cents(lookup, EMP, null)).isEqualTo(200);
  }

  /** An assignment the migration could not resolve names nobody and costs nobody's work (#968). */
  @Test
  public void should_not_match_an_assignment_whose_person_is_unresolved() {
    var unresolved = assignment(EMP, null, "general");
    unresolved.setEmployeeId(null);

    var lookup = EmployeeCostLookup.of(List.of(unresolved), List.of(cost("general", 100)));

    assertThat(lookup.findEffectiveCost(EMP, null, OrderType.STANDARD, DATE)).isEmpty();
  }

  @Test
  public void should_return_empty_for_an_empty_lookup() {
    assertThat(EmployeeCostLookup.of(List.of(), List.of()).findEffectiveCost(EMP, SUBORDER_IDS.get("so"), OrderType.STANDARD, DATE)).isEmpty();
  }

  private static Integer cents(EmployeeCostLookup lookup, long employeeId, String suborderSign) {
    return cents(lookup, employeeId, suborderSign, OrderType.STANDARD);
  }

  private static Integer cents(EmployeeCostLookup lookup, long employeeId, String suborderSign, OrderType orderType) {
    return lookup.findEffectiveCost(employeeId, suborderSign == null ? null : SUBORDER_IDS.get(suborderSign),
        orderType, DATE)
        .map(EmployeeCost::getCostCentsPerHour)
        .orElse(null);
  }

  private static EmployeeCostAssignment assignment(long employeeId, String suborderSign, String costName) {
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployeeId(employeeId);
    assignment.setEmployeeSign("sign-" + employeeId);
    assignment.setSuborderSign(suborderSign);
    assignment.setSuborderId(suborderSign == null ? null : SUBORDER_IDS.get(suborderSign));
    assignment.setCategory(CostCategoryTestUtils.named(costName));
    assignment.setValidFrom(LocalDate.of(2026, 1, 1));
    assignment.setValidUntil(LocalDate.of(2026, 12, 31));
    return assignment;
  }

  private static EmployeeCost cost(String name, int cents) {
    var cost = new EmployeeCost();
    cost.setCategory(CostCategoryTestUtils.named(name));
    cost.setCostCentsPerHour(cents);
    cost.setValidFrom(LocalDate.of(2026, 1, 1));
    cost.setValidUntil(LocalDate.of(2026, 12, 31));
    return cost;
  }

}
