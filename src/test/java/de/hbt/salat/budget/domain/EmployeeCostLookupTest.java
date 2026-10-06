package de.hbt.salat.budget.domain;

import static de.hbt.salat.testutils.ReferenceTestUtils.suborderWithId;
import static de.hbt.salat.testutils.ReferenceTestUtils.employeeWithId;
import static de.hbt.salat.testutils.ReferenceTestUtils.customerorderWithId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
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

  /** The order of {@code so} and {@code other}, and one besides — referred to by id as well (#1343). */
  private static final long ORDER = 10L;
  private static final long OTHER_ORDER = 11L;

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

    assertThat(lookup.findEffectiveCost(EMP, ORDER, SUBORDER_IDS.get("so"), DATE)).isEmpty();
  }

  @Test
  public void should_only_match_assignments_valid_on_the_given_date() {
    var expired = assignment(EMP, null, "general");
    expired.setValidUntil(DATE.minusDays(1));

    var lookup = EmployeeCostLookup.of(List.of(expired), List.of(cost("general", 100)));

    assertThat(lookup.findEffectiveCost(EMP, null, null, DATE)).isEmpty();
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

    assertThat(lookup.findEffectiveCost(EMP, null, null, DATE)).isEmpty();
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

  // --- the order step (#1343) -------------------------------------------------------------------

  @Test
  public void should_prefer_the_suborder_specific_assignment_over_the_order_specific_one() {
    var lookup = EmployeeCostLookup.of(
        List.of(toOrder(EMP, ORDER, "order"), assignment(EMP, "so", "specific"), assignment(EMP, null, "general")),
        List.of(cost("order", 300), cost("specific", 200), cost("general", 100)));

    assertThat(cents(lookup, EMP, "so")).isEqualTo(200);
  }

  @Test
  public void should_prefer_the_order_specific_assignment_over_the_general_one() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment(EMP, null, "general"), toOrder(EMP, ORDER, "order")),
        List.of(cost("general", 100), cost("order", 300)));

    assertThat(cents(lookup, EMP, "so")).isEqualTo(300);
  }

  /** The assignment to the order covers every suborder of it, also one nobody named. */
  @Test
  public void should_apply_the_order_specific_assignment_to_every_suborder_of_the_order() {
    var lookup = EmployeeCostLookup.of(
        List.of(toOrder(EMP, ORDER, "order"), assignment(EMP, "other", "specific")),
        List.of(cost("order", 300), cost("specific", 200)));

    assertThat(cents(lookup, EMP, "so")).isEqualTo(300);
    assertThat(cents(lookup, EMP, "other")).isEqualTo(200);
  }

  @Test
  public void should_not_apply_the_assignment_to_another_order() {
    var lookup = EmployeeCostLookup.of(
        List.of(toOrder(EMP, OTHER_ORDER, "order"), assignment(EMP, null, "general")),
        List.of(cost("order", 300), cost("general", 100)));

    assertThat(cents(lookup, EMP, "so")).isEqualTo(100);
  }

  @Test
  public void should_resolve_nothing_without_any_assignment_even_where_another_order_has_one() {
    var lookup = EmployeeCostLookup.of(
        List.of(toOrder(EMP, OTHER_ORDER, "order")), List.of(cost("order", 300)));

    assertThat(lookup.findEffectiveCost(EMP, ORDER, SUBORDER_IDS.get("so"), DATE)).isEmpty();
  }

  @Test
  public void should_not_apply_the_assignment_to_an_order_of_another_person() {
    var lookup = EmployeeCostLookup.of(
        List.of(toOrder(OTHER, ORDER, "order"), assignment(EMP, null, "general")),
        List.of(cost("order", 300), cost("general", 100)));

    assertThat(cents(lookup, EMP, "so")).isEqualTo(100);
  }

  /** The order-specific assignment is no general one: without an order it does not apply. */
  @Test
  public void should_skip_the_order_step_when_no_order_is_given() {
    var lookup = EmployeeCostLookup.of(
        List.of(toOrder(EMP, ORDER, "order"), assignment(EMP, null, "general")),
        List.of(cost("order", 300), cost("general", 100)));

    assertThat(lookup.findEffectiveCost(EMP, null, null, DATE))
        .map(EmployeeCost::getCostCentsPerHour).contains(100);
  }

  @Test
  public void should_only_match_an_order_specific_assignment_valid_on_the_given_date() {
    var expired = toOrder(EMP, ORDER, "order");
    expired.setValidUntil(DATE.minusDays(1));
    var lookup = EmployeeCostLookup.of(List.of(expired, assignment(EMP, null, "general")),
        List.of(cost("order", 300), cost("general", 100)));

    assertThat(cents(lookup, EMP, "so")).isEqualTo(100);
  }

  // --- standby is no exception any more (#1343) ---------------------------------------------------

  /**
   * The lookup does not know the order type at all: a standby booking without an assignment of its own
   * takes the general rate of the person — 7.50 EUR stays 7.50 EUR, 0 EUR stays 0 EUR.
   */
  @Test
  public void should_take_the_general_assignment_for_a_standby_booking_too() {
    var lookup = EmployeeCostLookup.of(
        List.of(assignment(EMP, null, "general"), assignment(OTHER, null, "nothing")),
        List.of(cost("general", 750), cost("nothing", 0)));

    assertThat(cents(lookup, EMP, "so")).isEqualTo(750);
    assertThat(cents(lookup, OTHER, "so")).isZero();
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

  @Test
  public void should_return_empty_for_an_empty_lookup() {
    assertThat(EmployeeCostLookup.of(List.of(), List.of()).findEffectiveCost(EMP, ORDER, SUBORDER_IDS.get("so"), DATE)).isEmpty();
  }

  /** The cost of a booking on that suborder of {@link #ORDER}; {@code null} for none at all. */
  private static Integer cents(EmployeeCostLookup lookup, long employeeId, String suborderSign) {
    return lookup.findEffectiveCost(employeeId, suborderSign == null ? null : ORDER,
            suborderSign == null ? null : SUBORDER_IDS.get(suborderSign), DATE)
        .map(EmployeeCost::getCostCentsPerHour)
        .orElse(null);
  }

  private static EmployeeCostAssignment assignment(long employeeId, String suborderSign, String costName) {
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployee(employeeWithId(employeeId));
    assignment.setSuborder(suborderWithId(suborderSign == null ? null : SUBORDER_IDS.get(suborderSign)));
    assignment.setCategory(CostCategoryTestUtils.named(costName));
    assignment.setValidFrom(LocalDate.of(2026, 1, 1));
    assignment.setValidUntil(LocalDate.of(2026, 12, 31));
    return assignment;
  }

  /** An assignment to the whole order (#1343). */
  private static EmployeeCostAssignment toOrder(long employeeId, long customerorderId, String costName) {
    var assignment = assignment(employeeId, null, costName);
    assignment.setCustomerorder(customerorderWithId(customerorderId));
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
