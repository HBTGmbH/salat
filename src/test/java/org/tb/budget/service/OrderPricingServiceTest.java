package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.persistence.OrderPricingRepository;
import org.tb.common.test.FixedClock;
import org.tb.order.service.SuborderService;

/**
 * The filters of the customer rate list (#949). A rate is inactive once its validity lies entirely
 * in the past; everything else — including a rate that only starts next month — is shown by
 * default.
 */
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderPricingServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final LocalDate YESTERDAY = TODAY.minusDays(1);
  private static final LocalDate TOMORROW = TODAY.plusDays(1);
  private static final LocalDate OPEN_END = LocalDate.of(2999, 12, 31);

  private OrderPricingRepository orderPricingRepository;
  private OrderPricingService service;

  @BeforeEach
  public void setUp() {
    orderPricingRepository = mock(OrderPricingRepository.class);
    service = new OrderPricingService(orderPricingRepository, mock(SuborderService.class));
  }

  @Test
  public void leaves_out_a_rate_that_ended_yesterday() {
    given(pricing("co", TODAY.minusYears(1), YESTERDAY));

    assertThat(service.getFiltered(null, false)).isEmpty();
  }

  @Test
  public void keeps_a_rate_that_ends_today() {
    var endingToday = pricing("co", TODAY.minusYears(1), TODAY);
    given(endingToday);

    assertThat(service.getFiltered(null, false)).containsExactly(endingToday);
  }

  @Test
  public void keeps_a_rate_without_an_end_date() {
    var openEnded = pricing("co", TODAY.minusYears(1), OPEN_END);
    given(openEnded);

    assertThat(service.getFiltered(null, false)).containsExactly(openEnded);
  }

  /** A rise entered ahead of time must stay visible, or it gets entered a second time. */
  @Test
  public void keeps_a_rate_that_only_starts_tomorrow() {
    var future = pricing("co", TOMORROW, OPEN_END);
    given(future);

    assertThat(service.getFiltered(null, false)).containsExactly(future);
  }

  @Test
  public void shows_the_expired_rates_as_well_when_asked_to() {
    var expired = pricing("co", TODAY.minusYears(1), YESTERDAY);
    var current = pricing("co", TODAY, OPEN_END);
    given(expired, current);

    assertThat(service.getFiltered(null, true)).containsExactly(expired, current);
  }

  @Test
  public void narrows_the_list_to_the_chosen_customer_order() {
    var chosen = pricing("co-one", TODAY, OPEN_END);
    when(orderPricingRepository.findByCustomerorderSignOrderByValidFromAsc("co-one"))
        .thenReturn(List.of(chosen));

    assertThat(service.getFiltered("co-one", false)).containsExactly(chosen);
  }

  /** Both filters apply at once — picking an order does not bring its expired rates back. */
  @Test
  public void leaves_out_the_expired_rates_of_the_chosen_customer_order() {
    var expired = pricing("co-one", TODAY.minusYears(1), YESTERDAY);
    var current = pricing("co-one", TODAY, OPEN_END);
    when(orderPricingRepository.findByCustomerorderSignOrderByValidFromAsc("co-one"))
        .thenReturn(List.of(expired, current));

    assertThat(service.getFiltered("co-one", false)).containsExactly(current);
  }

  /** The empty option of the select submits an empty string, which means "all orders". */
  @Test
  public void treats_a_blank_customer_order_as_no_choice_at_all() {
    var any = pricing("co", TODAY, OPEN_END);
    given(any);

    assertThat(service.getFiltered("  ", false)).containsExactly(any);
  }

  private void given(OrderPricing... pricings) {
    when(orderPricingRepository.findAllByOrderByCustomerorderSignAscValidFromAsc())
        .thenReturn(List.of(pricings));
  }

  private static OrderPricing pricing(String customerorderSign, LocalDate validFrom, LocalDate validUntil) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign(customerorderSign);
    pricing.setPriceCentsPerHour(10000);
    pricing.setValidFrom(validFrom);
    pricing.setValidUntil(validUntil);
    return pricing;
  }

}
