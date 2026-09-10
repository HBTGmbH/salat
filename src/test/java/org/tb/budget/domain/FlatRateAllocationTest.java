package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

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

  @Test
  public void the_single_plan_covering_the_due_date_holds_the_amount() {
    var plan = plan("year", null, JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan))).contains(plan);
  }

  @Test
  public void a_plan_whose_period_ends_before_the_due_date_does_not_hold_it() {
    var plan = plan("H2", null, JUL, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan))).isEmpty();
  }

  @Test
  public void the_boundaries_of_the_period_belong_to_the_plan() {
    var plan = plan("H1", null, JAN, JUN, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, JAN), List.of(plan))).contains(plan);
    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, JUN), List.of(plan))).contains(plan);
  }

  @Test
  public void an_inactive_plan_holds_nothing() {
    var plan = plan("archived", null, JAN, DEC, false);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan))).isEmpty();
  }

  /** An order-wide plan covers the order and everything in it, as it does for a booking. */
  @Test
  public void an_order_wide_plan_holds_a_suborder_flat_rate() {
    var plan = plan("year", null, JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount("co/01/D", IN_H1), List.of(plan))).contains(plan);
  }

  @Test
  public void a_suborder_plan_holds_a_flat_rate_below_its_suborder() {
    var plan = plan("co/01", "co/01", JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount("co/01/D", IN_H1), List.of(plan))).contains(plan);
  }

  @Test
  public void a_suborder_plan_does_not_hold_a_flat_rate_of_another_suborder() {
    var plan = plan("co/01", "co/01", JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount("co/02", IN_H1), List.of(plan))).isEmpty();
  }

  /** The order level is not below any first level suborder, so no suborder plan reaches it. */
  @Test
  public void a_suborder_plan_does_not_hold_an_order_wide_flat_rate() {
    var plan = plan("co/01", "co/01", JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan))).isEmpty();
  }

  @Test
  public void two_plans_covering_the_same_amount_hold_neither() {
    var first = plan("A", null, JAN, DEC, true);
    var second = plan("B", null, JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(first, second))).isEmpty();
  }

  /** Overlapping plans are legitimate (#914); only the ones actually covering the amount compete. */
  @Test
  public void plans_of_different_scopes_do_not_compete_for_the_same_amount() {
    var wide = plan("year", null, JAN, DEC, true);
    var narrow = plan("co/01", "co/01", JAN, DEC, true);

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(wide, narrow))).contains(wide);
  }

  @Test
  public void a_plan_of_another_customer_order_holds_nothing() {
    var plan = plan("other", null, JAN, DEC, true);
    plan.setCustomerorderSign("other");

    assertThat(FlatRateAllocation.uniquePlanFor(dueAmount(null, IN_H1), List.of(plan))).isEmpty();
  }

  private static FlatRateDueAmount dueAmount(String suborderSign, LocalDate due) {
    var rate = new OrderFlatRate();
    rate.setCustomerorderSign("co");
    rate.setSuborderSign(suborderSign);
    rate.setRhythm(FlatRateRhythm.ONCE);
    rate.setValidFrom(due);
    rate.setValidUntil(due);
    rate.setAmount(new BigDecimal("100"));
    return new FlatRateDueAmount(rate, due, rate.getAmount());
  }

  private static OrderBudget plan(String name, String suborderSign, LocalDate from, LocalDate until,
                                  boolean active) {
    var plan = new OrderBudget();
    plan.setName(name);
    plan.setCustomerorderSign("co");
    plan.setSuborderSign(suborderSign);
    plan.setValidFrom(from);
    plan.setValidUntil(until);
    plan.setActive(active);
    return plan;
  }

}
