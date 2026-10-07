package de.hbt.salat.budget.domain;

import static de.hbt.salat.testutils.ReferenceTestUtils.customerorderWithId;
import static de.hbt.salat.testutils.ReferenceTestUtils.suborderWithId;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.budget.domain.OrderBudgetBinding.PositionedSuborder;
import de.hbt.salat.common.domain.AuditedEntity;

/**
 * A customer rate above 0 EUR in the scope of a fixed-price plan counts revenue twice (#1404). The
 * rate is saved all the same; this decides when Salat says so.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class FixedPriceRateConflictTest {

  private static final long ORDER = 1L;
  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final LocalDate OPEN = LocalDate.of(2999, 12, 31);

  /** 4711/01 (id 10) and 4711/02 (id 20) on the first level. */
  private static final List<PositionedSuborder> SUBORDERS = List.of(
      new PositionedSuborder("4711/01", new OrderPosition(ORDER, List.of(10L))),
      new PositionedSuborder("4711/02", new OrderPosition(ORDER, List.of(20L))));

  @Test
  void warns_about_a_rate_above_zero_in_the_scope_of_a_fixed_price_plan() {
    var plan = plan(1L, 20L, true);

    assertThat(conflicts(plan, "4711/02", null, 9500, JAN)).isTrue();
    // a rate for the whole order reaches the plan's suborder as well
    assertThat(conflicts(plan, null, null, 9500, JAN)).isTrue();
  }

  /** 0 EUR is a deliberate statement and earns nothing (→ AppliedRate). */
  @Test
  void accepts_a_rate_of_zero() {
    assertThat(conflicts(plan(1L, 20L, true), "4711/02", null, 0, JAN)).isFalse();
  }

  @Test
  void leaves_plans_without_a_fixed_price_alone() {
    assertThat(conflicts(plan(1L, 20L, false), "4711/02", null, 9500, JAN)).isFalse();
  }

  @Test
  void ignores_a_rate_outside_the_scope_or_the_validity_of_the_plan() {
    var plan = plan(1L, 20L, true);

    assertThat(conflicts(plan, "4711/01", null, 9500, JAN)).isFalse();
    assertThat(conflicts(plan, "4711/02", null, 9500, LocalDate.of(2027, 1, 1))).isFalse();
  }

  /** A rate bound to another plan only prices that plan's bookings. */
  @Test
  void ignores_a_rate_bound_to_another_plan_but_not_one_bound_to_this_one() {
    var plan = plan(1L, 20L, true);

    assertThat(conflicts(plan, "4711/02", 2L, 9500, JAN)).isFalse();
    assertThat(conflicts(plan, "4711/02", 1L, 9500, JAN)).isTrue();
  }

  private static boolean conflicts(OrderBudget plan, String pattern, Long ratePlanId, int cents, LocalDate from) {
    return FixedPriceRateConflict.conflicts(plan, ORDER, pattern, ratePlanId, cents, from, OPEN, SUBORDERS);
  }

  private static OrderBudget plan(long id, Long suborderId, boolean fixedPrice) {
    var plan = new OrderBudget();
    setId(plan, id);
    plan.setName("plan " + id);
    plan.setCustomerorder(customerorderWithId(ORDER));
    plan.setSuborder(suborderWithId(suborderId));
    plan.setValidFrom(JAN);
    plan.setValidUntil(DEC);
    plan.setActive(true);
    plan.setFixedPrice(fixedPrice);
    return plan;
  }

  private static void setId(AuditedEntity entity, long id) {
    try {
      Field field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
