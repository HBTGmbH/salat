package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingRow;
import org.tb.budget.persistence.OrderPricingRepository;
import org.tb.common.test.FixedClock;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * The filters of the customer rate list (#949, #957). Two things can have expired, and they are
 * filtered apart: the rate itself — its validity lies entirely in the past — and the customer order
 * it hangs off. Everything else, including a rate that only starts next month, is shown by default.
 */
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderPricingServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final LocalDate YESTERDAY = TODAY.minusDays(1);
  private static final LocalDate TOMORROW = TODAY.plusDays(1);
  private static final LocalDate OPEN_END = LocalDate.of(2999, 12, 31);

  private OrderPricingRepository orderPricingRepository;
  private CustomerorderService customerorderService;
  private OrderPricingService service;

  @BeforeEach
  public void setUp() {
    orderPricingRepository = mock(OrderPricingRepository.class);
    customerorderService = mock(CustomerorderService.class);
    // No orders unless a test says so: a rate whose order is gone behaves as before (#957).
    when(customerorderService.getCustomerordersBySigns(any())).thenReturn(List.of());
    service = new OrderPricingService(orderPricingRepository, mock(SuborderService.class),
        customerorderService);
  }

  @Test
  public void leaves_out_a_rate_that_ended_yesterday() {
    given(pricing("co", TODAY.minusYears(1), YESTERDAY));

    assertThat(pricingsOf(service.getRows(null, false, true))).isEmpty();
  }

  @Test
  public void keeps_a_rate_that_ends_today() {
    var endingToday = pricing("co", TODAY.minusYears(1), TODAY);
    given(endingToday);

    assertThat(pricingsOf(service.getRows(null, false, true))).containsExactly(endingToday);
  }

  @Test
  public void keeps_a_rate_without_an_end_date() {
    var openEnded = pricing("co", TODAY.minusYears(1), OPEN_END);
    given(openEnded);

    assertThat(pricingsOf(service.getRows(null, false, true))).containsExactly(openEnded);
  }

  /** A rise entered ahead of time must stay visible, or it gets entered a second time. */
  @Test
  public void keeps_a_rate_that_only_starts_tomorrow() {
    var future = pricing("co", TOMORROW, OPEN_END);
    given(future);

    assertThat(pricingsOf(service.getRows(null, false, true))).containsExactly(future);
  }

  @Test
  public void shows_the_expired_rates_as_well_when_asked_to() {
    var expired = pricing("co", TODAY.minusYears(1), YESTERDAY);
    var current = pricing("co", TODAY, OPEN_END);
    given(expired, current);

    assertThat(pricingsOf(service.getRows(null, true, true))).containsExactly(expired, current);
  }

  @Test
  public void narrows_the_list_to_the_chosen_customer_order() {
    var chosen = pricing("co-one", TODAY, OPEN_END);
    when(orderPricingRepository.findByCustomerorderSignOrderByValidFromAsc("co-one"))
        .thenReturn(List.of(chosen));

    assertThat(pricingsOf(service.getRows("co-one", false, true))).containsExactly(chosen);
  }

  /** Both filters apply at once — picking an order does not bring its expired rates back. */
  @Test
  public void leaves_out_the_expired_rates_of_the_chosen_customer_order() {
    var expired = pricing("co-one", TODAY.minusYears(1), YESTERDAY);
    var current = pricing("co-one", TODAY, OPEN_END);
    when(orderPricingRepository.findByCustomerorderSignOrderByValidFromAsc("co-one"))
        .thenReturn(List.of(expired, current));

    assertThat(pricingsOf(service.getRows("co-one", false, true))).containsExactly(current);
  }

  /** The empty option of the select submits an empty string, which means "all orders". */
  @Test
  public void treats_a_blank_customer_order_as_no_choice_at_all() {
    var any = pricing("co", TODAY, OPEN_END);
    given(any);

    assertThat(pricingsOf(service.getRows("  ", false, true))).containsExactly(any);
  }

  // --- the validity of the order behind the rate (#957) ---------------------------------------

  @Test
  public void leaves_out_the_rates_of_an_order_whose_validity_has_expired() {
    given(pricing("co", TODAY.minusYears(1), OPEN_END));
    givenOrder("co", TODAY.minusYears(2), YESTERDAY);

    assertThat(service.getRows(null, false, false)).isEmpty();
  }

  @Test
  public void keeps_the_rates_of_an_order_that_ends_today() {
    var rate = pricing("co", TODAY.minusYears(1), OPEN_END);
    given(rate);
    givenOrder("co", TODAY.minusYears(2), TODAY);

    assertThat(pricingsOf(service.getRows(null, false, false))).containsExactly(rate);
  }

  @Test
  public void keeps_the_rates_of_an_order_without_an_end() {
    var rate = pricing("co", TODAY.minusYears(1), OPEN_END);
    given(rate);
    givenOrder("co", TODAY.minusYears(2), null);

    assertThat(pricingsOf(service.getRows(null, false, false))).containsExactly(rate);
  }

  @Test
  public void shows_the_rates_of_expired_orders_as_well_when_asked_to() {
    var rate = pricing("co", TODAY.minusYears(1), OPEN_END);
    given(rate);
    givenOrder("co", TODAY.minusYears(2), YESTERDAY);

    assertThat(pricingsOf(service.getRows(null, false, true))).containsExactly(rate);
  }

  /**
   * The order is the only way into the rate, so a rate whose order is gone has to stay visible —
   * otherwise it could not be reached through the user interface at all.
   */
  @Test
  public void keeps_a_rate_whose_customer_order_no_longer_exists() {
    var orphan = pricing("gone", TODAY.minusYears(1), OPEN_END);
    given(orphan);

    assertThat(pricingsOf(service.getRows(null, false, false))).containsExactly(orphan);
  }

  /** The two switches are independent: an expired rate of a valid order needs the other one. */
  @Test
  public void applies_the_two_switches_apart_from_each_other() {
    var expiredRate = pricing("co", TODAY.minusYears(1), YESTERDAY);
    given(expiredRate);
    givenOrder("co", TODAY.minusYears(2), null);

    assertThat(service.getRows(null, false, false)).isEmpty();
    assertThat(pricingsOf(service.getRows(null, true, false))).containsExactly(expiredRate);
  }

  /** Coverage is judged against all stored rates, not against the ones the filter leaves over. */
  @Test
  public void judges_the_coverage_against_the_rates_the_filter_leaves_out_as_well() {
    var expired = pricing("co", TODAY.minusYears(2), YESTERDAY);
    var current = pricing("co", TODAY, OPEN_END);
    given(expired, current);
    givenOrder("co", TODAY.minusYears(2), null);

    var rows = service.getRows(null, false, false);

    assertThat(rows).singleElement()
        .extracting(row -> row.deviation().uncoveredOrderPeriod()).isEqualTo(false);
  }

  private void givenOrder(String sign, LocalDate fromDate, LocalDate untilDate) {
    var order = new Customerorder();
    order.setSign(sign);
    order.setFromDate(fromDate);
    order.setUntilDate(untilDate);
    when(customerorderService.getCustomerordersBySigns(any())).thenReturn(List.of(order));
  }

  private static List<OrderPricing> pricingsOf(List<OrderPricingRow> rows) {
    return rows.stream().map(OrderPricingRow::pricing).toList();
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
