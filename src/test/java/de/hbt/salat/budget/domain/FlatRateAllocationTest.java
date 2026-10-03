package de.hbt.salat.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.domain.AuditedEntity;

/**
 * Which plan a flat rate amount counts against (#972). The rule mirrors the one a booking follows,
 * and its most important property is what it refuses to do: where two plans qualify it picks
 * neither, because counting the amount twice would report revenue that does not exist and picking
 * one would count it against a plan nobody chose.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class FlatRateAllocationTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final LocalDate IN_H1 = LocalDate.of(2026, 3, 10);

  /**
   * The suborders of the order these tests book on, by complete order sign — the signs keep the
   * cases readable, coverage itself is decided by id (#1205).
   */
  private static final long CO_ID = 1L;
  private static final long OTHER_CO_ID = 2L;
  private static final Map<String, Long> SUBORDER_IDS = Map.of(
      "co/01", 11L, "co/01/D", 12L, "co/01/A", 14L, "co/01/A/1", 15L, "co/01/B", 16L, "co/02", 17L);

  /** Where a flat rate sits, read from the tree above — what {@code OrderPositions} answers in the application. */
  private static final Function<OrderFlatRate, Optional<OrderPosition>> POSITIONS =
      rate -> Optional.of(positionOf(rate.getSuborderSign()));

  @Test
  public void the_single_plan_covering_the_due_date_holds_the_amount() {
    var plan = plan("year", null, JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan), POSITIONS)).contains(plan);
  }

  @Test
  public void a_plan_whose_period_ends_before_the_due_date_does_not_hold_it() {
    var plan = plan("H2", null, JUL, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan), POSITIONS)).isEmpty();
  }

  @Test
  public void the_boundaries_of_the_period_belong_to_the_plan() {
    var plan = plan("H1", null, JAN, JUN, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, JAN), List.of(plan), POSITIONS)).contains(plan);
    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, JUN), List.of(plan), POSITIONS)).contains(plan);
  }

  @Test
  public void an_inactive_plan_holds_nothing() {
    var plan = plan("archived", null, JAN, DEC, false);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan), POSITIONS)).isEmpty();
  }

  /** An order-wide plan covers the order and everything in it, as it does for a booking. */
  @Test
  public void an_order_wide_plan_holds_a_suborder_flat_rate() {
    var plan = plan("year", null, JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount("co/01/D", IN_H1), List.of(plan), POSITIONS)).contains(plan);
  }

  @Test
  public void a_suborder_plan_holds_a_flat_rate_below_its_suborder() {
    var plan = plan("co/01", "co/01", JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount("co/01/D", IN_H1), List.of(plan), POSITIONS)).contains(plan);
  }

  @Test
  public void a_suborder_plan_does_not_hold_a_flat_rate_of_another_suborder() {
    var plan = plan("co/01", "co/01", JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount("co/02", IN_H1), List.of(plan), POSITIONS)).isEmpty();
  }

  /** A plan on any level holds what lies in its subtree, not only a first level one (#1004). */
  @Test
  public void a_plan_on_a_deeper_suborder_holds_the_flat_rates_of_its_subtree() {
    var plan = plan("co/01/A", "co/01/A", JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount("co/01/A", IN_H1), List.of(plan), POSITIONS)).contains(plan);
    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount("co/01/A/1", IN_H1), List.of(plan), POSITIONS)).contains(plan);
    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount("co/01/B", IN_H1), List.of(plan), POSITIONS)).isEmpty();
    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount("co/01", IN_H1), List.of(plan), POSITIONS)).isEmpty();
  }

  /** The order level lies above every suborder, so no suborder plan reaches it. */
  @Test
  public void a_suborder_plan_does_not_hold_an_order_wide_flat_rate() {
    var plan = plan("co/01", "co/01", JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan), POSITIONS)).isEmpty();
  }

  @Test
  public void two_plans_covering_the_same_amount_hold_neither() {
    var first = plan("A", null, JAN, DEC, true);
    var second = plan("B", null, JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(first, second), POSITIONS)).isEmpty();
  }

  /** Overlapping plans are legitimate (#914); only the ones actually covering the amount compete. */
  @Test
  public void plans_of_different_scopes_do_not_compete_for_the_same_amount() {
    var wide = plan("year", null, JAN, DEC, true);
    var narrow = plan("co/01", "co/01", JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(wide, narrow), POSITIONS)).contains(wide);
  }

  @Test
  public void a_plan_of_another_customer_order_holds_nothing() {
    var plan = plan("other", null, JAN, DEC, true);
    plan.setCustomerorderId(OTHER_CO_ID);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan), POSITIONS)).isEmpty();
  }

  // --- a flat rate that names its plan (#1065) --------------------------------------------------

  /** The whole point: the ambiguous case #1004 made common now has an answer. */
  @Test
  public void a_named_plan_wins_against_the_derivation() {
    var first = withId(plan("A", null, JAN, DEC, true), 1L);
    var second = withId(plan("B", null, JAN, DEC, true), 2L);
    var dueAmount = boundTo(dueAmount(null, IN_H1), second);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount, List.of(first, second), POSITIONS)).contains(second);
  }

  /**
   * Period and scope are not weighed again — the saving did that. A due date outside the plan is
   * therefore no special case, which is what keeps this rule to one line.
   */
  @Test
  public void a_named_plan_is_not_judged_by_period_and_scope_again() {
    var plan = withId(plan("H2", "co/01", JUL, DEC, true), 1L);
    var dueAmount = boundTo(dueAmount("co/02", IN_H1), plan);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount, List.of(plan), POSITIONS)).contains(plan);
  }

  /**
   * A deactivated plan is not evaluated, so its amounts are reported without a budget — the same
   * answer its bookings get. Falling back to the derivation would move the amount to a plan
   * somebody else picked.
   */
  @Test
  public void a_named_plan_that_is_no_longer_active_holds_nothing() {
    var named = withId(plan("archived", null, JAN, DEC, false), 1L);
    var other = withId(plan("current", null, JAN, DEC, true), 2L);
    var dueAmount = boundTo(dueAmount(null, IN_H1), named);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount, List.of(other), POSITIONS)).isEmpty();
  }

  /** Without a named plan nothing changes — every existing flat rate is unbound. */
  @Test
  public void an_unbound_flat_rate_is_still_derived() {
    var plan = withId(plan("year", null, JAN, DEC, true), 1L);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan), POSITIONS)).contains(plan);
  }

  /** A flat rate the migration could not resolve sits nowhere, so no derived plan holds it (#1205). */
  @Test
  public void a_flat_rate_without_a_position_is_held_by_no_derived_plan() {
    var plan = plan("year", null, JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan), rate -> Optional.empty()))
        .isEmpty();
  }

  /** The position of a complete order sign of the tree above, built from its prefixes. */
  private static OrderPosition positionOf(String suborderSign) {
    if (suborderSign == null) {
      return OrderPosition.orderWide(CO_ID);
    }
    var parts = suborderSign.split("/");
    var path = new ArrayList<Long>();
    for (int i = 2; i <= parts.length; i++) {
      path.add(SUBORDER_IDS.get(String.join("/", Arrays.copyOf(parts, i))));
    }
    return new OrderPosition(CO_ID, path);
  }

  private static FlatRateDueAmount boundTo(FlatRateDueAmount dueAmount, OrderBudget plan) {
    dueAmount.flatRate().setOrderBudget(plan);
    return dueAmount;
  }

  private static FlatRateDueAmount dueAmount(String suborderSign, LocalDate due) {
    var rate = new OrderFlatRate();
    rate.setCustomerorderId(CO_ID);
    rate.setCustomerorderSign("co");
    rate.setSuborderId(suborderSign == null ? null : SUBORDER_IDS.get(suborderSign));
    rate.setSuborderSign(suborderSign);
    rate.setRhythm(FlatRateRhythm.ONCE);
    rate.setValidFrom(due);
    rate.setValidUntil(due);
    rate.setAmount(new BigDecimal("100"));
    return new FlatRateDueAmount(rate, due, rate.getAmount());
  }

  /** The id is generated, so there is no setter; a stored plan always has one. */
  private static OrderBudget withId(OrderBudget plan, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(plan, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
    return plan;
  }

  private static OrderBudget plan(String name, String suborderSign, LocalDate from, LocalDate until,
                                  boolean active) {
    var plan = new OrderBudget();
    plan.setName(name);
    plan.setCustomerorderId(CO_ID);
    plan.setCustomerorderSign("co");
    plan.setSuborderId(suborderSign == null ? null : SUBORDER_IDS.get(suborderSign));
    plan.setSuborderSign(suborderSign);
    plan.setValidFrom(from);
    plan.setValidUntil(until);
    plan.setActive(active);
    return plan;
  }

}
