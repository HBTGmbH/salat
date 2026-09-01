package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;

/**
 * The lookup replaces the three {@code findEffective*} repository queries, so these tests pin the
 * fallback hierarchy and the date filter that those queries expressed in JPQL.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderPricingLookupTest {

  private static final LocalDate DATE = LocalDate.of(2026, 6, 15);

  @Test
  public void should_prefer_employee_specific_over_suborder_wide_and_order_wide() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "so", null, 200),
        pricing("co", "so", "emp", 300)));

    assertThat(rate(lookup, "co", "so", "emp")).isEqualTo(300);
  }

  @Test
  public void should_fall_back_to_suborder_wide_when_no_employee_specific_rate_exists() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "so", null, 200),
        pricing("co", "other", "emp", 300)));

    assertThat(rate(lookup, "co", "so", "emp")).isEqualTo(200);
  }

  @Test
  public void should_fall_back_to_order_wide_when_no_suborder_rate_exists() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "other", null, 200)));

    assertThat(rate(lookup, "co", "so", "emp")).isEqualTo(100);
  }

  @Test
  public void should_ignore_rates_of_other_customerorders() {
    var lookup = OrderPricingLookup.of(List.of(pricing("other", null, null, 100)));

    assertThat(lookup.findEffectiveRate("co", "so", "emp", DATE)).isEmpty();
  }

  @Test
  public void should_only_match_rates_valid_on_the_given_date() {
    var expired = pricing("co", null, null, 100);
    expired.setValidUntil(DATE.minusDays(1));
    var future = pricing("co", null, null, 200);
    future.setValidFrom(DATE.plusDays(1));
    var current = pricing("co", null, null, 300);

    assertThat(rate(OrderPricingLookup.of(List.of(expired, future, current)), "co", null, null))
        .isEqualTo(300);
    assertThat(OrderPricingLookup.of(List.of(expired, future)).findEffectiveRate("co", null, null, DATE))
        .isEmpty();
  }

  @Test
  public void should_include_the_boundaries_of_the_validity_range() {
    var pricing = pricing("co", null, null, 100);
    pricing.setValidFrom(DATE);
    pricing.setValidUntil(DATE);

    assertThat(rate(OrderPricingLookup.of(List.of(pricing)), "co", null, null)).isEqualTo(100);
  }

  @Test
  public void should_fall_back_when_the_more_specific_rate_is_not_valid_on_the_given_date() {
    var expiredSuborderRate = pricing("co", "so", null, 200);
    expiredSuborderRate.setValidUntil(DATE.minusDays(1));

    var lookup = OrderPricingLookup.of(List.of(pricing("co", null, null, 100), expiredSuborderRate));

    assertThat(rate(lookup, "co", "so", null)).isEqualTo(100);
  }

  @Test
  public void should_skip_the_employee_specific_step_when_no_employee_is_given() {
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", "so", "emp", 300),
        pricing("co", "so", null, 200)));

    assertThat(rate(lookup, "co", "so", null)).isEqualTo(200);
  }

  @Test
  public void should_return_empty_for_an_empty_lookup() {
    assertThat(OrderPricingLookup.of(List.of()).findEffectiveRate("co", "so", "emp", DATE)).isEmpty();
  }

  /**
   * The suborder key is the complete order sign, not the bare {@code Suborder.sign} (#889). The
   * lookup treats the key as opaque, so a mismatch does not fail — it silently degrades to the
   * order-wide rate. These two tests pin the format that both sides have to agree on.
   */
  @Test
  public void should_resolve_a_rate_keyed_by_the_complete_order_sign_of_a_nested_suborder() {
    var suborder = nestedSuborder("co", "01", "02");
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "co/01/02", null, 200)));

    assertThat(rate(lookup, "co", suborder.getCompleteOrderSign(), null)).isEqualTo(200);
  }

  @Test
  public void should_fall_back_to_the_order_wide_rate_for_a_rate_keyed_by_the_bare_suborder_sign() {
    var suborder = nestedSuborder("co", "01", "02");
    var lookup = OrderPricingLookup.of(List.of(
        pricing("co", null, null, 100),
        pricing("co", "02", null, 200)));

    assertThat(rate(lookup, "co", suborder.getCompleteOrderSign(), null)).isEqualTo(100);
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

  private static Integer rate(OrderPricingLookup lookup, String co, String so, String emp) {
    return lookup.findEffectiveRate(co, so, emp, DATE)
        .map(OrderPricing::getPriceCentsPerHour)
        .orElse(null);
  }

  private static OrderPricing pricing(String co, String so, String emp, int cents) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign(co);
    pricing.setSuborderSign(so);
    pricing.setEmployeeSign(emp);
    pricing.setPriceCentsPerHour(cents);
    pricing.setValidFrom(LocalDate.of(2026, 1, 1));
    pricing.setValidUntil(LocalDate.of(2026, 12, 31));
    return pricing;
  }

}
