package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * A budget plan covers its suborder and everything below it (#1004). For the plans on the first
 * suborder level that were the only allowed ones until then this says exactly what the old rule
 * said, so no existing plan changes what it covers — that equivalence is what the first tests here
 * pin down.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetScopeTest {

  @Test
  public void a_plan_covers_a_booking_on_its_own_suborder() {
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "CO", "CO/01")).isTrue();
  }

  @Test
  public void a_plan_covers_a_booking_below_its_suborder() {
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "CO", "CO/01/D")).isTrue();
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "CO", "CO/01/D/E")).isTrue();
  }

  /** A plan on the second level covers its own subtree — the point of #1004. */
  @Test
  public void a_plan_on_a_deeper_suborder_covers_its_subtree() {
    assertThat(BudgetScope.covers(plan("CO", "CO/01/A"), "CO", "CO/01/A")).isTrue();
    assertThat(BudgetScope.covers(plan("CO", "CO/01/A"), "CO", "CO/01/A/1")).isTrue();
  }

  @Test
  public void a_plan_on_a_deeper_suborder_does_not_cover_the_sibling_branch() {
    assertThat(BudgetScope.covers(plan("CO", "CO/01/A"), "CO", "CO/01/B")).isFalse();
  }

  /** Coverage runs downwards only: what sits above the plan is outside it. */
  @Test
  public void a_plan_on_a_deeper_suborder_does_not_cover_the_level_above_it() {
    assertThat(BudgetScope.covers(plan("CO", "CO/01/A"), "CO", "CO/01")).isFalse();
  }

  @Test
  public void a_plan_does_not_cover_a_booking_under_another_suborder() {
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "CO", "CO/02")).isFalse();
  }

  /**
   * The trailing slash is the boundary, not decoration: without it a plan on {@code CO/01} would
   * swallow the unrelated suborder {@code CO/010}.
   */
  @Test
  public void the_comparison_only_matches_on_slash_boundaries() {
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "CO", "CO/010")).isFalse();
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "CO", "CO/010/A")).isFalse();
  }

  @Test
  public void an_order_wide_plan_covers_every_scope_of_its_order() {
    assertThat(BudgetScope.covers(plan("CO", null), "CO", "CO/09/D")).isTrue();
    assertThat(BudgetScope.covers(plan("CO", ""), "CO", null)).isTrue();
  }

  @Test
  public void no_plan_covers_a_booking_of_another_customer_order() {
    assertThat(BudgetScope.covers(plan("CO", null), "OTHER", null)).isFalse();
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "OTHER", "CO/01")).isFalse();
  }

  /** A suborder plan needs a suborder to compare against — a booking without one is outside it. */
  @Test
  public void a_suborder_plan_covers_nothing_without_a_suborder() {
    assertThat(BudgetScope.covers(plan("CO", "CO/01"), "CO", null)).isFalse();
  }

  @Test
  public void blank_and_null_both_mean_the_whole_customer_order() {
    assertThat(BudgetScope.isOrderWide(null)).isTrue();
    assertThat(BudgetScope.isOrderWide("")).isTrue();
    assertThat(BudgetScope.isOrderWide("  ")).isTrue();
    assertThat(BudgetScope.isOrderWide("CO/01")).isFalse();
  }

  // --- the level, which is a validation rule rather than a coverage rule ------------------------

  @Test
  public void an_order_wide_scope_is_level_zero() {
    assertThat(BudgetScope.levelOf(null)).isZero();
    assertThat(BudgetScope.levelOf(" ")).isZero();
  }

  @Test
  public void the_level_counts_the_slashes_of_the_complete_order_sign() {
    assertThat(BudgetScope.levelOf("CO/01")).isEqualTo(1);
    assertThat(BudgetScope.levelOf("CO/01/A")).isEqualTo(2);
    assertThat(BudgetScope.levelOf("CO/01/A/1")).isEqualTo(3);
  }

  private static OrderBudget plan(String customerorderSign, String suborderSign) {
    var plan = new OrderBudget();
    plan.setCustomerorderSign(customerorderSign);
    plan.setSuborderSign(suborderSign);
    return plan;
  }

}
