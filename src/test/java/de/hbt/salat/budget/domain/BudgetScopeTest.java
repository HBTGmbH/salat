package de.hbt.salat.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * A budget plan covers its suborder and everything below it (#1004), compared by id since #1205:
 * the plan names its order and suborder by id, and what it is compared with comes as the path of
 * suborder ids read from the current tree.
 *
 * <p>The tree of these tests: order 1 with the suborders {@code 01} (11) — below it {@code 01/D}
 * (12) with {@code 01/D/E} (13), {@code 01/A} (14) with {@code 01/A/1} (15) and {@code 01/B} (16) —
 * and {@code 02} (17).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetScopeTest {

  private static final long CO = 1L;
  private static final long OTHER_CO = 2L;

  @Test
  public void a_plan_covers_a_booking_on_its_own_suborder() {
    assertThat(BudgetScope.covers(plan(CO, 11L), at(CO, 11L))).isTrue();
  }

  @Test
  public void a_plan_covers_a_booking_below_its_suborder() {
    assertThat(BudgetScope.covers(plan(CO, 11L), at(CO, 11L, 12L))).isTrue();
    assertThat(BudgetScope.covers(plan(CO, 11L), at(CO, 11L, 12L, 13L))).isTrue();
  }

  /** A plan on the second level covers its own subtree — the point of #1004. */
  @Test
  public void a_plan_on_a_deeper_suborder_covers_its_subtree() {
    assertThat(BudgetScope.covers(plan(CO, 14L), at(CO, 11L, 14L))).isTrue();
    assertThat(BudgetScope.covers(plan(CO, 14L), at(CO, 11L, 14L, 15L))).isTrue();
  }

  @Test
  public void a_plan_on_a_deeper_suborder_does_not_cover_the_sibling_branch() {
    assertThat(BudgetScope.covers(plan(CO, 14L), at(CO, 11L, 16L))).isFalse();
  }

  /** Coverage runs downwards only: what sits above the plan is outside it. */
  @Test
  public void a_plan_on_a_deeper_suborder_does_not_cover_the_level_above_it() {
    assertThat(BudgetScope.covers(plan(CO, 14L), at(CO, 11L))).isFalse();
  }

  @Test
  public void a_plan_does_not_cover_a_booking_under_another_suborder() {
    assertThat(BudgetScope.covers(plan(CO, 11L), at(CO, 17L))).isFalse();
  }

  @Test
  public void an_order_wide_plan_covers_every_scope_of_its_order() {
    assertThat(BudgetScope.covers(plan(CO, null), at(CO, 17L, 12L))).isTrue();
    assertThat(BudgetScope.covers(plan(CO, null), OrderPosition.orderWide(CO))).isTrue();
  }

  @Test
  public void no_plan_covers_a_booking_of_another_customer_order() {
    assertThat(BudgetScope.covers(plan(CO, null), OrderPosition.orderWide(OTHER_CO))).isFalse();
    assertThat(BudgetScope.covers(plan(CO, 11L), at(OTHER_CO, 11L))).isFalse();
  }

  /** A suborder plan needs a suborder to compare against — a booking without one is outside it. */
  @Test
  public void a_suborder_plan_covers_nothing_without_a_suborder() {
    assertThat(BudgetScope.covers(plan(CO, 11L), OrderPosition.orderWide(CO))).isFalse();
    assertThat(BudgetScope.covers(plan(CO, 11L), null)).isFalse();
  }

  /**
   * The signs stored next to the ids say nothing about coverage any more (#1205): a renamed order
   * or suborder leaves the plan's sign columns behind for a moment, and the plan still covers what it
   * covered.
   */
  @Test
  public void the_signs_of_the_plan_do_not_take_part() {
    var plan = plan(CO, 14L);
    plan.setCustomerorderSign("OLD");
    plan.setSuborderSign("OLD/99");

    assertThat(BudgetScope.covers(plan, at(CO, 11L, 14L, 15L))).isTrue();
  }

  /** A suborder moved to another parent takes its bookings along: the path is read from the tree. */
  @Test
  public void a_moved_suborder_follows_its_new_parent() {
    // 01/A/1 (15) moved below 02 (17)
    assertThat(BudgetScope.covers(plan(CO, 14L), at(CO, 17L, 15L))).isFalse();
    assertThat(BudgetScope.covers(plan(CO, 17L), at(CO, 17L, 15L))).isTrue();
  }

  /**
   * A plan whose suborder the migration could not resolve covers nothing — and in particular not the
   * whole order, which would count the bookings of every other suborder against it.
   */
  @Test
  public void a_plan_with_an_unresolved_suborder_covers_nothing() {
    var plan = plan(CO, null);
    plan.setSuborderSign("CO/01");

    assertThat(plan.isOrderWide()).isFalse();
    assertThat(plan.isUnresolved()).isTrue();
    assertThat(BudgetScope.covers(plan, at(CO, 11L))).isFalse();
    assertThat(BudgetScope.covers(plan, OrderPosition.orderWide(CO))).isFalse();
  }

  @Test
  public void a_plan_with_an_unresolved_order_covers_nothing() {
    var plan = plan(null, null);

    assertThat(plan.isUnresolved()).isTrue();
    assertThat(BudgetScope.covers(plan, OrderPosition.orderWide(CO))).isFalse();
  }

  @Test
  public void blank_and_null_both_mean_the_whole_customer_order() {
    assertThat(BudgetScope.isOrderWide(null, null)).isTrue();
    assertThat(BudgetScope.isOrderWide(null, "")).isTrue();
    assertThat(BudgetScope.isOrderWide(null, "  ")).isTrue();
    assertThat(BudgetScope.isOrderWide(null, "CO/01")).isFalse();
  }

  /**
   * A suborder id is a suborder, whatever the sign column says: the column only mirrors it for the
   * reports (#1212) and is read only where the id is missing.
   */
  @Test
  public void a_suborder_id_names_a_suborder_whatever_the_sign_column_holds() {
    assertThat(BudgetScope.isOrderWide(11L, null)).isFalse();
    assertThat(BudgetScope.isOrderWide(11L, "")).isFalse();
    assertThat(BudgetScope.isOrderWide(11L, "CO/01")).isFalse();
  }

  // --- the level, which is a validation rule rather than a coverage rule ------------------------

  @Test
  public void an_order_wide_position_is_level_zero() {
    assertThat(OrderPosition.orderWide(CO).level()).isZero();
  }

  @Test
  public void the_level_is_the_depth_of_the_suborder() {
    assertThat(at(CO, 11L).level()).isEqualTo(1);
    assertThat(at(CO, 11L, 14L).level()).isEqualTo(2);
    assertThat(at(CO, 11L, 14L, 15L).level()).isEqualTo(3);
  }

  /**
   * A plan as the services store it: the ids, and the signs written next to them. The sign of the
   * suborder is what tells "no suborder" from "a suborder the migration could not resolve".
   */
  private static OrderBudget plan(Long customerorderId, Long suborderId) {
    var plan = new OrderBudget();
    plan.setCustomerorderId(customerorderId);
    plan.setCustomerorderSign("CO");
    plan.setSuborderId(suborderId);
    plan.setSuborderSign(suborderId == null ? null : "CO/" + suborderId);
    return plan;
  }

  private static OrderPosition at(long customerorderId, Long... suborderPath) {
    return new OrderPosition(customerorderId, List.of(suborderPath));
  }

}
