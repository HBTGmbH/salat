package org.tb.budget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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

  private OrderPricing pricing(String customerorderSign, LocalDate validFrom, LocalDate validUntil) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign(customerorderSign);
    pricing.setPriceCentsPerHour(10000);
    pricing.setValidFrom(validFrom);
    pricing.setValidUntil(validUntil);
    return orderPricingRepository.save(pricing);
  }

}
