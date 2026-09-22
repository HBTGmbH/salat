package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * Which budget plans a rate or a flat rate can be bound to (#1065). The rule is the ground both the
 * select and the saving stand on, so the cases worth pinning are the ones where the two could drift
 * apart: the shortcuts that answer without looking at a single suborder, and the boundaries.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderBudgetBindingTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final List<String> SUBORDERS = List.of("co/01", "co/01/A", "co/02");

  @Test
  public void a_plan_of_another_customer_order_never_meets_the_rate() {
    var plan = plan("other", null);

    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan, "co", null, SUBORDERS)).isFalse();
    assertThat(OrderBudgetBinding.scopeMeetsSuborder(plan, "co", null)).isFalse();
  }

  /**
   * An order-wide rate applies everywhere in the order, so it meets every plan of it — without the
   * suborders being consulted. That matters: the list leaves hidden suborders out, and an order
   * that has none at all would otherwise reject a pairing that is plainly right.
   */
  @Test
  public void an_order_wide_pattern_meets_every_plan_of_the_order_without_any_suborder() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", "co/01"), "co", null, List.of()))
        .isTrue();
  }

  /** And the mirror image: an order-wide plan covers everything of the order. */
  @Test
  public void an_order_wide_plan_meets_every_pattern_of_the_order_without_any_suborder() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", null), "co", "co/01/", List.of()))
        .isTrue();
  }

  @Test
  public void a_plan_narrower_than_the_pattern_meets_it() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", "co/01/A"), "co", "co/01/", SUBORDERS))
        .isTrue();
  }

  @Test
  public void a_plan_wider_than_the_pattern_meets_it() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", "co/01"), "co", "co/01/A/", SUBORDERS))
        .isTrue();
  }

  @Test
  public void disjoint_scopes_do_not_meet() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", "co/02"), "co", "co/01/", SUBORDERS))
        .isFalse();
  }

  /** A pattern matching no suborder at all meets nothing — there is no ground for it to stand on. */
  @Test
  public void a_pattern_matching_no_suborder_meets_no_plan() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", "co/01"), "co", "co/99/", SUBORDERS))
        .isFalse();
  }

  @Test
  public void a_plan_covers_the_suborder_of_a_flat_rate_and_its_subtree() {
    var plan = plan("co", "co/01");

    assertThat(OrderBudgetBinding.scopeMeetsSuborder(plan, "co", "co/01")).isTrue();
    assertThat(OrderBudgetBinding.scopeMeetsSuborder(plan, "co", "co/01/A")).isTrue();
    assertThat(OrderBudgetBinding.scopeMeetsSuborder(plan, "co", "co/02")).isFalse();
    assertThat(OrderBudgetBinding.scopeMeetsSuborder(plan, "co", null)).isFalse();
  }

  @Test
  public void periods_that_only_touch_still_overlap() {
    var plan = plan("co", null, JAN, JUN);

    assertThat(OrderBudgetBinding.periodsOverlap(plan, JUN, DEC)).isTrue();
    assertThat(OrderBudgetBinding.periodsOverlap(plan, JUL, DEC)).isFalse();
  }

  /** An open rate end arrives as the sentinel, so it needs no case of its own. */
  @Test
  public void an_open_rate_end_reaches_every_later_plan() {
    var plan = plan("co", null, JUL, DEC);

    assertThat(OrderBudgetBinding.periodsOverlap(plan, JAN, LocalDate.of(2999, 12, 31))).isTrue();
  }

  private static OrderBudget plan(String customerorderSign, String suborderSign) {
    return plan(customerorderSign, suborderSign, JAN, DEC);
  }

  private static OrderBudget plan(String customerorderSign, String suborderSign,
                                  LocalDate validFrom, LocalDate validUntil) {
    var plan = new OrderBudget();
    plan.setName("plan");
    plan.setCustomerorderSign(customerorderSign);
    plan.setSuborderSign(suborderSign);
    plan.setValidFrom(validFrom);
    plan.setValidUntil(validUntil);
    plan.setActive(true);
    return plan;
  }

}
