package org.tb.budget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderPricing;

/**
 * The customer orders offered in the filter of the rate list (#949). They come from the pricings
 * themselves, so an order that has meanwhile been hidden or has expired stays reachable — a pricing
 * refers to its order by sign and outlives it.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderPricingRepositoryTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);

  @Autowired
  private OrderPricingRepository orderPricingRepository;

  @Autowired
  private OrderBudgetRepository orderBudgetRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  /** Several rates on one order must not multiply its entry in the select. */
  @Test
  public void names_an_order_with_several_rates_only_once() {
    pricing("co-one", FROM, UNTIL);
    pricing("co-one", UNTIL.plusDays(1), UNTIL.plusYears(1));

    assertThat(orderPricingRepository.findDistinctCustomerorderSigns()).containsExactly("co-one");
  }

  @Test
  public void offers_the_orders_in_alphabetical_order() {
    pricing("co-b", FROM, UNTIL);
    pricing("co-a", FROM, UNTIL);

    assertThat(orderPricingRepository.findDistinctCustomerorderSigns())
        .containsExactly("co-a", "co-b");
  }

  @Test
  public void reads_the_rates_of_one_order_oldest_first() {
    var later = pricing("co-one", UNTIL.plusDays(1), UNTIL.plusYears(1));
    var earlier = pricing("co-one", FROM, UNTIL);
    pricing("co-other", FROM, UNTIL);

    assertThat(orderPricingRepository.findByCustomerorderSignOrderByValidFromAsc("co-one"))
        .containsExactly(earlier, later);
  }

  // --- the budget plan is part of the overlap key (#1065) ---------------------------------------

  /**
   * Two rates that differ only in their plan do not conflict: the plan-bound one narrows the
   * plan-less one, exactly as a specific pattern narrows a general one.
   */
  @Test
  public void two_rates_of_different_plans_do_not_overlap() {
    var planA = plan("A");
    var planB = plan("B");
    boundPricing("co", FROM, UNTIL, planA);

    assertThat(overlapping("co", planB.getId())).isEmpty();
  }

  @Test
  public void a_plan_bound_rate_does_not_overlap_the_plan_less_one() {
    boundPricing("co", FROM, UNTIL, plan("A"));

    assertThat(overlapping("co", null)).isEmpty();
  }

  @Test
  public void two_rates_of_the_same_plan_still_overlap() {
    var plan = plan("A");
    var existing = boundPricing("co", FROM, UNTIL, plan);

    assertThat(overlapping("co", plan.getId())).containsExactly(existing);
  }

  /** Nothing changes for the rates that name no plan — all of the existing ones. */
  @Test
  public void two_plan_less_rates_still_overlap() {
    var existing = pricing("co", FROM, UNTIL);

    assertThat(overlapping("co", null)).containsExactly(existing);
  }

  private List<OrderPricing> overlapping(String customerorderSign, Long planId) {
    return orderPricingRepository.findOverlapping(customerorderSign, null, null, planId, FROM, UNTIL, null);
  }

  private OrderBudget plan(String name) {
    var plan = new OrderBudget();
    plan.setName(name);
    plan.setCustomerorderSign("co");
    plan.setValidFrom(FROM);
    plan.setValidUntil(UNTIL);
    plan.setActive(true);
    return orderBudgetRepository.save(plan);
  }

  private OrderPricing boundPricing(String customerorderSign, LocalDate validFrom, LocalDate validUntil,
                                    OrderBudget plan) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign(customerorderSign);
    pricing.setPriceCentsPerHour(10000);
    pricing.setValidFrom(validFrom);
    pricing.setValidUntil(validUntil);
    pricing.setOrderBudget(plan);
    return orderPricingRepository.save(pricing);
  }

  private OrderPricing pricing(String customerorderSign, LocalDate validFrom, LocalDate validUntil) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign(customerorderSign);
    pricing.setPriceCentsPerHour(10000);
    pricing.setValidFrom(validFrom);
    pricing.setValidUntil(validUntil);
    return orderPricingRepository.save(pricing);
  }

}
