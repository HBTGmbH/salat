package de.hbt.salat.budget.domain;

import static de.hbt.salat.testutils.ReferenceTestUtils.suborderWithId;
import static de.hbt.salat.testutils.ReferenceTestUtils.customerorderWithId;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.budget.domain.OrderBudgetBinding.PositionedSuborder;

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
  /** The orders and suborders of these tests by sign — the cases stay readable, the plans compare ids (#1205). */
  private static final Map<String, Long> ORDER_IDS = Map.of("co", 1L, "other", 2L);
  private static final Map<String, Long> SUBORDER_IDS = Map.of("co/01", 11L, "co/01/A", 14L, "co/02", 17L);
  private static final List<PositionedSuborder> SUBORDERS = Stream.of("co/01", "co/01/A", "co/02")
      .map(sign -> new PositionedSuborder(sign, positionOf(sign)))
      .toList();

  @Test
  public void a_plan_of_another_customer_order_never_meets_the_rate() {
    var plan = plan("other", null);

    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan, ORDER_IDS.get("co"), null, SUBORDERS)).isFalse();
    assertThat(OrderBudgetBinding.scopeMeetsSuborder(plan, positionOf(null))).isFalse();
  }

  /**
   * An order-wide rate applies everywhere in the order, so it meets every plan of it — without the
   * suborders being consulted. That matters: the list leaves hidden suborders out, and an order
   * that has none at all would otherwise reject a pairing that is plainly right.
   */
  @Test
  public void an_order_wide_pattern_meets_every_plan_of_the_order_without_any_suborder() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", "co/01"), ORDER_IDS.get("co"), null, List.of()))
        .isTrue();
  }

  /** And the mirror image: an order-wide plan covers everything of the order. */
  @Test
  public void an_order_wide_plan_meets_every_pattern_of_the_order_without_any_suborder() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", null), ORDER_IDS.get("co"), "co/01/", List.of()))
        .isTrue();
  }

  @Test
  public void a_plan_narrower_than_the_pattern_meets_it() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", "co/01/A"), ORDER_IDS.get("co"), "co/01/", SUBORDERS))
        .isTrue();
  }

  @Test
  public void a_plan_wider_than_the_pattern_meets_it() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", "co/01"), ORDER_IDS.get("co"), "co/01/A/", SUBORDERS))
        .isTrue();
  }

  @Test
  public void disjoint_scopes_do_not_meet() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", "co/02"), ORDER_IDS.get("co"), "co/01/", SUBORDERS))
        .isFalse();
  }

  /** A pattern matching no suborder at all meets nothing — there is no ground for it to stand on. */
  @Test
  public void a_pattern_matching_no_suborder_meets_no_plan() {
    assertThat(OrderBudgetBinding.scopeMeetsPattern(plan("co", "co/01"), ORDER_IDS.get("co"), "co/99/", SUBORDERS))
        .isFalse();
  }

  @Test
  public void a_plan_covers_the_suborder_of_a_flat_rate_and_its_subtree() {
    var plan = plan("co", "co/01");

    assertThat(OrderBudgetBinding.scopeMeetsSuborder(plan, positionOf("co/01"))).isTrue();
    assertThat(OrderBudgetBinding.scopeMeetsSuborder(plan, positionOf("co/01/A"))).isTrue();
    assertThat(OrderBudgetBinding.scopeMeetsSuborder(plan, positionOf("co/02"))).isFalse();
    assertThat(OrderBudgetBinding.scopeMeetsSuborder(plan, positionOf(null))).isFalse();
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

  /** The position of a complete order sign of order "co", built from its prefixes; {@code null} for the order as a whole. */
  private static OrderPosition positionOf(String suborderSign) {
    if (suborderSign == null) {
      return OrderPosition.orderWide(ORDER_IDS.get("co"));
    }
    var parts = suborderSign.split("/");
    var path = new ArrayList<Long>();
    for (int i = 2; i <= parts.length; i++) {
      path.add(SUBORDER_IDS.get(String.join("/", Arrays.copyOf(parts, i))));
    }
    return new OrderPosition(ORDER_IDS.get("co"), path);
  }

  private static OrderBudget plan(String customerorderSign, String suborderSign) {
    return plan(customerorderSign, suborderSign, JAN, DEC);
  }

  private static OrderBudget plan(String customerorderSign, String suborderSign,
                                  LocalDate validFrom, LocalDate validUntil) {
    var plan = new OrderBudget();
    plan.setName("plan");
    plan.setCustomerorder(customerorderWithId(ORDER_IDS.get(customerorderSign)));
    plan.setSuborder(suborderWithId(suborderSign == null ? null : SUBORDER_IDS.get(suborderSign)));
    plan.setValidFrom(validFrom);
    plan.setValidUntil(validUntil);
    plan.setActive(true);
    return plan;
  }

}
