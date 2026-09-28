package de.hbt.salat.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * The lookup replaces the three {@code findEffective*} repository queries, so these tests pin the
 * fallback hierarchy and the date filter that those queries expressed in JPQL.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderPricingLookupTest {

  private static final LocalDate DATE = LocalDate.of(2026, 6, 15);
  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final LocalDate OPEN_END = LocalDate.of(2999, 12, 31);
  private static final long PLAN_A = 1L;
  private static final long PLAN_B = 2L;
  private static final long EMP = 11L;
  private static final long OTHER = 12L;
  /** A person without a rate of their own. */
  private static final long SOMEBODY = 13L;

  @Test
  public void should_prefer_employee_specific_over_suborder_wide_and_order_wide() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "so", null, 200),
        pricing("co", "so", EMP, 300)));

    assertThat(rate(lookup, "co", "so", EMP)).isEqualTo(300);
  }

  @Test
  public void should_fall_back_to_suborder_wide_when_no_employee_specific_rate_exists() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "so", null, 200),
        pricing("co", "other", EMP, 300)));

    assertThat(rate(lookup, "co", "so", EMP)).isEqualTo(200);
  }

  @Test
  public void should_fall_back_to_order_wide_when_no_suborder_rate_exists() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "other", null, 200)));

    assertThat(rate(lookup, "co", "so", EMP)).isEqualTo(100);
  }

  @Test
  public void should_ignore_rates_of_other_customerorders() {
    var lookup = OrderPricingLookup.of(List.of(pricing("other", null, null, 100)));

    assertThat(lookup.findEffectiveRate("co", "so", EMP, null, DATE)).isEmpty();
  }

  @Test
  public void should_only_match_rates_valid_on_the_given_date() {
    var expired = pricing("co", null, null, 100);
    expired.setValidUntil(DATE.minusDays(1));
    var future = pricing("co", null, null, 200);
    future.setValidFrom(DATE.plusDays(1));
    var current = pricing("co", null, null, 300);

    assertThat(rate(OrderPricingLookup.of(List.of(expired, future, current)), "co", null, SOMEBODY))
        .isEqualTo(300);
    assertThat(OrderPricingLookup.of(List.of(expired, future)).findEffectiveRate("co", null, SOMEBODY, null, DATE))
        .isEmpty();
  }

  @Test
  public void should_include_the_boundaries_of_the_validity_range() {
    var pricing = pricing("co", null, null, 100);
    pricing.setValidFrom(DATE);
    pricing.setValidUntil(DATE);

    assertThat(rate(OrderPricingLookup.of(List.of(pricing)), "co", null, SOMEBODY)).isEqualTo(100);
  }

  @Test
  public void should_fall_back_when_the_more_specific_rate_is_not_valid_on_the_given_date() {
    var expiredSuborderRate = pricing("co", "so", null, 200);
    expiredSuborderRate.setValidUntil(DATE.minusDays(1));

    var lookup = OrderPricingLookup.of(List.of(pricing("co", null, null, 100), expiredSuborderRate));

    assertThat(rate(lookup, "co", "so", SOMEBODY)).isEqualTo(100);
  }

  @Test
  public void should_skip_the_employee_specific_step_for_somebody_without_an_own_rate() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", "so", EMP, 300),
        pricing("co", "so", null, 200)));

    assertThat(rate(lookup, "co", "so", SOMEBODY)).isEqualTo(200);
  }

  @Test
  public void should_return_empty_for_an_empty_lookup() {
    assertThat(OrderPricingLookup.of(List.of()).findEffectiveRate("co", "so", EMP, null, DATE)).isEmpty();
  }

  /**
   * The suborder side is the complete order sign, not the bare {@code Suborder.sign} (#889). A
   * mismatch does not fail — it silently degrades to the order-wide rate — so these two tests pin
   * the format that both sides have to agree on.
   */
  @Test
  public void should_resolve_a_rate_keyed_by_the_complete_order_sign_of_a_nested_suborder() {
    var suborder = nestedSuborder("co", "01", "02");
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "co/01/02", null, 200)));

    assertThat(rate(lookup, "co", suborder.getCompleteOrderSign(), SOMEBODY)).isEqualTo(200);
  }

  @Test
  public void should_fall_back_to_the_order_wide_rate_for_a_rate_keyed_by_the_bare_suborder_sign() {
    var suborder = nestedSuborder("co", "01", "02");
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "02", null, 200)));

    assertThat(rate(lookup, "co", suborder.getCompleteOrderSign(), SOMEBODY)).isEqualTo(100);
  }

  /**
   * {@code suborderSign} is an SQL {@code LIKE} pattern matched against the complete order sign with
   * a trailing slash, the way the reporting SQL does it (#891). These tests pin that rule, the
   * specificity ranking it makes necessary, and the employee side that deliberately stays exact.
   */
  @Test
  public void should_match_a_subtree_pattern_against_the_suborder_itself_and_its_descendants() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "co/01/", null, 200)));

    assertThat(rate(lookup, "co", "co/01", SOMEBODY)).isEqualTo(200);
    assertThat(rate(lookup, "co", "co/01/02", SOMEBODY)).isEqualTo(200);
    assertThat(rate(lookup, "co", "co/01/02/03", SOMEBODY)).isEqualTo(200);
  }

  @Test
  public void should_not_let_a_subtree_pattern_spill_over_into_a_sibling_with_a_longer_sign() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "co/01/", null, 200)));

    // Without the trailing slash "co/01" would also prefix-match "co/010".
    assertThat(rate(lookup, "co", "co/010", SOMEBODY)).isEqualTo(100);
  }

  @Test
  public void should_treat_percent_as_a_wildcard_and_underscore_as_a_single_character() {
    var percent = OrderPricingLookup.of(List.of(pricing("co", "co/%/02/", null, 200)));
    assertThat(rate(percent, "co", "co/01/02", SOMEBODY)).isEqualTo(200);
    assertThat(rate(percent, "co", "co/99/02", SOMEBODY)).isEqualTo(200);
    assertThat(rate(percent, "co", "co/01/03", SOMEBODY)).isNull();

    var underscore = OrderPricingLookup.of(List.of(pricing("co", "co/0_/", null, 200)));
    assertThat(rate(underscore, "co", "co/01", SOMEBODY)).isEqualTo(200);
    assertThat(rate(underscore, "co", "co/012", SOMEBODY)).isNull();
  }

  @Test
  public void should_treat_regex_metacharacters_in_the_pattern_as_literal_text() {
    var lookup = OrderPricingLookup.of(List.of(pricing("co", "co/a.c+(x)/", null, 200)));

    assertThat(rate(lookup, "co", "co/a.c+(x)", SOMEBODY)).isEqualTo(200);
    assertThat(rate(lookup, "co", "co/abcx", SOMEBODY)).isNull();
  }

  @Test
  public void should_prefer_the_longest_matching_pattern() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", "co/", null, 100),
        pricing("co", "co/01/", null, 200),
        pricing("co", "co/01/02/", null, 300)));

    assertThat(rate(lookup, "co", "co/01/02", SOMEBODY)).isEqualTo(300);
    assertThat(rate(lookup, "co", "co/01/09", SOMEBODY)).isEqualTo(200);
    assertThat(rate(lookup, "co", "co/07", SOMEBODY)).isEqualTo(100);
  }

  @Test
  public void should_prefer_an_employee_specific_rate_over_a_more_specific_suborder_pattern() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", "co/01/02/", null, 300),
        pricing("co", "co/", EMP, 200)));

    assertThat(rate(lookup, "co", "co/01/02", EMP)).isEqualTo(200);
    assertThat(rate(lookup, "co", "co/01/02", OTHER)).isEqualTo(300);
  }

  /**
   * The chain used to try (order, suborder, employee), (order, suborder) and (order) only, so an
   * employee-specific rate without a suborder was never found — that was the majority of the rows.
   */
  @Test
  public void should_resolve_an_employee_specific_rate_that_applies_to_the_whole_order() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", null, EMP, 200)));

    assertThat(rate(lookup, "co", "co/01/02", EMP)).isEqualTo(200);
    assertThat(rate(lookup, "co", "co/01/02", OTHER)).isEqualTo(100);
  }

  /**
   * The person is matched by id (#968): a rate still carrying the sign the person had before a
   * correction or an anonymization applies unchanged, without anything following the sign.
   */
  @Test
  public void should_match_the_person_by_id_whatever_sign_the_rate_was_stored_with() {
    var stale = pricing("co", null, EMP, 200);
    stale.setEmployeeSign("old-sign");
    var lookup = OrderPricingLookup.of(List.of(pricing("co", null, null, 100), stale));

    assertThat(rate(lookup, "co", "co/01", EMP)).isEqualTo(200);
    assertThat(rate(lookup, "co", "co/01", OTHER)).isEqualTo(100);
  }

  /**
   * A rate whose person the migration could not resolve (#968) keeps its sign and has no id. It must
   * apply to nobody — read as "no person" it would price the work of everyone on the order.
   */
  @Test
  public void should_apply_a_rate_whose_person_is_unresolved_to_nobody() {
    var unresolved = pricing("co", null, null, 900);
    unresolved.setEmployeeSign("gone");
    var lookup = OrderPricingLookup.of(List.of(pricing("co", null, null, 100), unresolved));

    assertThat(rate(lookup, "co", "co/01", SOMEBODY)).isEqualTo(100);
    assertThat(OrderPricingLookup.of(List.of(unresolved)).findEffectiveRate("co", "co/01", SOMEBODY, null, DATE))
        .isEmpty();
  }

  /** Neither does it price the order as a whole, so it cannot close a gap in the coverage either. */
  @Test
  public void should_not_count_a_rate_whose_person_is_unresolved_as_order_wide() {
    var unresolved = rate("co", JAN, DEC);
    unresolved.setEmployeeSign("gone");
    var lookup = OrderPricingLookup.of(List.of(rate("co", JAN, JUN), unresolved));

    assertThat(unresolved.isOrderWide()).isFalse();
    assertThat(lookup.hasUncoveredPeriod("co", JAN, DEC)).isTrue();
  }

  /** An empty sign without an id names nobody either — the reports read it the same way. */
  @Test
  public void should_treat_an_empty_sign_without_an_id_as_a_rate_for_everyone() {
    var blank = pricing("co", null, null, 100);
    blank.setEmployeeSign("");

    assertThat(blank.isForEveryone()).isTrue();
    assertThat(rate(OrderPricingLookup.of(List.of(blank)), "co", "co/01", SOMEBODY)).isEqualTo(100);
  }

  /**
   * The coverage check of the rate list (#957). It answers a question about the order-wide rates
   * only: a rate for one suborder or one person does not claim to price the order as a whole, so
   * holding it to the order period would report a gap wherever such a rate exists.
   */
  @Test
  public void should_report_no_gap_for_an_order_period_covered_end_to_end() {
    var lookup = OrderPricingLookup.of(List.of(
        rate("co", JAN, JUN),
        rate("co", JUL, DEC)));

    assertThat(lookup.hasUncoveredPeriod("co", JAN, DEC)).isFalse();
  }

  @Test
  public void should_report_a_gap_between_two_rates() {
    var lookup = OrderPricingLookup.of(List.of(
        rate("co", JAN, JUN),
        rate("co", JUL.plusDays(1), DEC)));

    assertThat(lookup.hasUncoveredPeriod("co", JAN, DEC)).isTrue();
  }

  @Test
  public void should_report_a_gap_before_the_first_rate() {
    var lookup = OrderPricingLookup.of(List.of(rate("co", JUL, DEC)));

    assertThat(lookup.hasUncoveredPeriod("co", JAN, DEC)).isTrue();
  }

  @Test
  public void should_report_a_gap_after_the_last_rate() {
    var lookup = OrderPricingLookup.of(List.of(rate("co", JAN, JUN)));

    assertThat(lookup.hasUncoveredPeriod("co", JAN, DEC)).isTrue();
  }

  /** An order that runs on has to be met by a rate that runs on with it. */
  @Test
  public void should_report_no_gap_for_an_open_order_end_met_by_an_open_rate_end() {
    var lookup = OrderPricingLookup.of(List.of(rate("co", JAN, OPEN_END)));

    assertThat(lookup.hasUncoveredPeriod("co", JAN, null)).isFalse();
  }

  @Test
  public void should_report_a_gap_when_the_order_runs_on_beyond_its_last_rate() {
    var lookup = OrderPricingLookup.of(List.of(rate("co", JAN, DEC)));

    assertThat(lookup.hasUncoveredPeriod("co", JAN, null)).isTrue();
  }

  @Test
  public void should_report_no_gap_when_the_order_has_no_order_wide_rate_at_all() {
    var suborderRate = rate("co", JAN, JUN);
    suborderRate.setSuborderSign("co/01/");
    var employeeRate = rate("co", JAN, JUN);
    employeeRate.setEmployeeId(EMP);

    var lookup = OrderPricingLookup.of(List.of(suborderRate, employeeRate));

    assertThat(lookup.hasUncoveredPeriod("co", JAN, DEC)).isFalse();
  }

  /** A specific rate covers what it covers — it must not close the gap of the order-wide ones. */
  @Test
  public void should_ignore_the_specific_rates_when_judging_the_coverage() {
    var suborderRate = rate("co", JUL, DEC);
    suborderRate.setSuborderSign("co/01/");

    var lookup = OrderPricingLookup.of(List.of(rate("co", JAN, JUN), suborderRate));

    assertThat(lookup.hasUncoveredPeriod("co", JAN, DEC)).isTrue();
  }

  @Test
  public void should_report_no_gap_for_overlapping_rates_that_together_cover_the_period() {
    var lookup = OrderPricingLookup.of(List.of(
        rate("co", JAN, DEC),
        rate("co", JUN, JUL)));

    assertThat(lookup.hasUncoveredPeriod("co", JAN, DEC)).isFalse();
  }

  @Test
  public void should_report_no_gap_for_an_order_without_a_start() {
    var lookup = OrderPricingLookup.of(List.of(rate("co", JUL, DEC)));

    assertThat(lookup.hasUncoveredPeriod("co", null, DEC)).isFalse();
  }

  private static OrderPricing rate(String co, LocalDate validFrom, LocalDate validUntil) {
    var pricing = pricing(co, null, null, 100);
    pricing.setValidFrom(validFrom);
    pricing.setValidUntil(validUntil);
    return pricing;
  }

  /** Returns the child of {@code customerorderSign/parentSign/childSign}. */
  private static Suborder nestedSuborder(String customerorderSign, String parentSign, String childSign) {
    var customerorder = new Customerorder();
    customerorder.setSign(customerorderSign);
    var parent = new Suborder();
    parent.setCustomerorder(customerorder);
    parent.setSign(parentSign);
    var child = new Suborder();
    child.setCustomerorder(customerorder);
    child.setParentorder(parent);
    child.setSign(childSign);
    return child;
  }

  private static Integer rate(OrderPricingLookup lookup, String co, String so, long employeeId) {
    return rate(lookup, co, so, employeeId, null);
  }

  private static Integer rate(OrderPricingLookup lookup, String co, String so, long employeeId, Long planId) {
    return lookup.findEffectiveRate(co, so, employeeId, planId, DATE)
        .map(OrderPricing::getPriceCentsPerHour)
        .orElse(null);
  }

  private static OrderPricing pricing(String co, String so, Long employeeId, int cents) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign(co);
    pricing.setSuborderSign(so);
    pricing.setEmployeeId(employeeId);
    pricing.setEmployeeSign(employeeId == null ? null : "sign-" + employeeId);
    pricing.setPriceCentsPerHour(cents);
    pricing.setValidFrom(LocalDate.of(2026, 1, 1));
    pricing.setValidUntil(LocalDate.of(2026, 12, 31));
    return pricing;
  }

  // --- the budget plan as a fourth level of specificity (#1065) ---------------------------------

  @Test
  public void should_prefer_the_plan_bound_rate_for_a_booking_of_that_plan() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 12000),
        boundTo(pricing("co", null, null, 15000), PLAN_A)));

    assertThat(rate(lookup, "co", "so", SOMEBODY, PLAN_A)).isEqualTo(15000);
  }

  /**
   * The plan is a rank, not a switch. A booking of another plan is not left unpriced — it falls
   * back exactly as work by somebody without their own rate falls back to the general one.
   */
  @Test
  public void should_fall_back_to_the_plan_less_rate_for_a_booking_of_another_plan() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 12000),
        boundTo(pricing("co", null, null, 15000), PLAN_A)));

    assertThat(rate(lookup, "co", "so", SOMEBODY, PLAN_B)).isEqualTo(12000);
  }

  @Test
  public void should_fall_back_to_the_plan_less_rate_for_a_booking_without_an_assignment() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 12000),
        boundTo(pricing("co", null, null, 15000), PLAN_A)));

    assertThat(rate(lookup, "co", "so", SOMEBODY, null)).isEqualTo(12000);
  }

  /** Without a plan-less rate next to it, a booking of another plan is priced by nothing at all. */
  @Test
  public void should_resolve_nothing_when_only_a_rate_of_another_plan_exists() {
    var lookup = OrderPricingLookup.of(List.of(boundTo(pricing("co", null, null, 15000), PLAN_A)));

    assertThat(rate(lookup, "co", "so", SOMEBODY, PLAN_B)).isNull();
  }

  /** The plan sits below the employee: a personal rate wins even without a plan. */
  @Test
  public void should_prefer_an_employee_specific_plan_less_rate_over_a_plan_bound_one() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, EMP, 20000),
        boundTo(pricing("co", null, null, 15000), PLAN_A)));

    assertThat(rate(lookup, "co", "so", EMP, PLAN_A)).isEqualTo(20000);
  }

  /** And above the pattern: the shortest plan-bound pattern beats the longest plan-less one. */
  @Test
  public void should_prefer_a_plan_bound_rate_without_a_pattern_over_a_plan_less_long_pattern() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", "co/01/", null, 12000),
        boundTo(pricing("co", null, null, 15000), PLAN_A)));

    assertThat(rate(lookup, "co", "co/01", SOMEBODY, PLAN_A)).isEqualTo(15000);
  }

  /** Among plan-bound rates of the same plan the pattern decides again, as it always did. */
  @Test
  public void should_rank_two_rates_of_the_same_plan_by_their_pattern() {
    var lookup = OrderPricingLookup.of(List.of(
        boundTo(pricing("co", null, null, 15000), PLAN_A),
        boundTo(pricing("co", "co/01/", null, 18000), PLAN_A)));

    assertThat(rate(lookup, "co", "co/01", SOMEBODY, PLAN_A)).isEqualTo(18000);
  }

  /**
   * Every stored rate is plan-less, so they all rank equal on the new step and keep the order they
   * had among themselves. This is what makes the change invisible to existing figures.
   */
  @Test
  public void should_not_change_the_outcome_where_no_rate_names_a_plan() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "so", null, 200),
        pricing("co", "so", EMP, 300)));

    assertThat(rate(lookup, "co", "so", EMP, PLAN_A)).isEqualTo(300);
    assertThat(rate(lookup, "co", "so", EMP, null)).isEqualTo(300);
  }

  /**
   * A plan-bound rate prices only the bookings of its plan, so it leaves the rest of the order
   * unpriced and must not silence the gap warning of the rate list (#957).
   */
  @Test
  public void a_plan_bound_rate_does_not_cover_the_order_period() {
    var planBound = boundTo(pricing("co", null, null, 15000), PLAN_A);

    assertThat(planBound.isOrderWide()).isFalse();
    assertThat(OrderPricingLookup.of(List.of(planBound)).hasUncoveredPeriod("co", JAN, DEC)).isFalse();
  }

  /** …and the warning still appears where only plan-bound rates sit next to an order-wide one. */
  @Test
  public void reports_the_gap_a_plan_bound_rate_leaves_beside_an_order_wide_one() {
    var orderWide = pricing("co", null, null, 12000);
    orderWide.setValidFrom(JAN);
    orderWide.setValidUntil(JUN);
    var planBound = boundTo(pricing("co", null, null, 15000), PLAN_A);
    planBound.setValidFrom(JUL);
    planBound.setValidUntil(DEC);

    assertThat(OrderPricingLookup.of(List.of(orderWide, planBound)).hasUncoveredPeriod("co", JAN, DEC))
        .isTrue();
  }

  private static OrderPricing boundTo(OrderPricing pricing, long planId) {
    var plan = new OrderBudget();
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(plan, planId);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
    pricing.setOrderBudget(plan);
    return pricing;
  }

}
