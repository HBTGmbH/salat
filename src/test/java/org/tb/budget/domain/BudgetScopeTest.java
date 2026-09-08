package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;

/**
 * Budget plans only live on first level suborders (#905), while bookings happen anywhere below.
 * The scope of a booking is therefore its first level ancestor — resolving its own suborder instead
 * would leave every deeper booking outside the plan that is supposed to hold it.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetScopeTest {

  @Test
  public void a_first_level_suborder_is_its_own_scope() {
    assertThat(BudgetScope.firstLevelSignOf(firstLevel())).isEqualTo("CO/01");
  }

  @Test
  public void a_deeper_suborder_resolves_to_its_first_level_ancestor() {
    assertThat(BudgetScope.firstLevelSignOf(below(firstLevel(), "02"))).isEqualTo("CO/01");
  }

  @Test
  public void a_suborder_three_levels_down_resolves_to_the_same_ancestor() {
    var deep = below(below(firstLevel(), "02"), "03");

    assertThat(deep.getCompleteOrderSign()).isEqualTo("CO/01/02/03");
    assertThat(BudgetScope.firstLevelSignOf(deep)).isEqualTo("CO/01");
  }

  @Test
  public void a_suborder_plan_covers_a_booking_below_its_suborder() {
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "CO", "CO/01")).isTrue();
  }

  @Test
  public void a_suborder_plan_does_not_cover_a_booking_under_another_suborder() {
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "CO", "CO/02")).isFalse();
  }

  @Test
  public void an_order_wide_plan_covers_every_scope_of_its_order() {
    assertThat(BudgetScope.covers(plan("CO", null), "CO", "CO/09")).isTrue();
    assertThat(BudgetScope.covers(plan("CO", ""), "CO", null)).isTrue();
  }

  @Test
  public void no_plan_covers_a_booking_of_another_customer_order() {
    assertThat(BudgetScope.covers(plan("CO", null), "OTHER", null)).isFalse();
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "OTHER", "CO/01")).isFalse();
  }

  @Test
  public void blank_and_null_both_mean_the_whole_customer_order() {
    assertThat(BudgetScope.isOrderWide(null)).isTrue();
    assertThat(BudgetScope.isOrderWide("")).isTrue();
    assertThat(BudgetScope.isOrderWide("  ")).isTrue();
    assertThat(BudgetScope.isOrderWide("CO/01")).isFalse();
  }

  private static Suborder firstLevel() {
    var order = new Customerorder();
    order.setSign("CO");
    var suborder = new Suborder();
    suborder.setSign("01");
    suborder.setCustomerorder(order);
    return suborder;
  }

  private static Suborder below(Suborder parent, String sign) {
    var suborder = new Suborder();
    suborder.setSign(sign);
    suborder.setCustomerorder(parent.getCustomerorder());
    suborder.setParentorder(parent);
    return suborder;
  }

  private static OrderBudget plan(String customerorderSign, String suborderSign) {
    var plan = new OrderBudget();
    plan.setCustomerorderSign(customerorderSign);
    plan.setSuborderSign(suborderSign);
    return plan;
  }

}
