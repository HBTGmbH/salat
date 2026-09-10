package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingData;
import org.tb.budget.domain.OrderPricingRow;
import org.tb.budget.persistence.OrderPricingRepository;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.test.FixedClock;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
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
  private EmployeeService employeeService;
  private OrderPricingService service;

  @BeforeEach
  public void setUp() {
    orderPricingRepository = mock(OrderPricingRepository.class);
    customerorderService = mock(CustomerorderService.class);
    // No orders unless a test says so: a rate whose order is gone behaves as before (#957).
    when(customerorderService.getCustomerordersBySigns(any())).thenReturn(List.of());
    // The order and the employee of a written rate exist unless a test says otherwise (#958).
    when(customerorderService.getCustomerorderBySign(any())).thenReturn(new Customerorder());
    employeeService = mock(EmployeeService.class);
    when(employeeService.getEmployeeBySign(any())).thenReturn(new Employee());
    service = new OrderPricingService(orderPricingRepository, mock(SuborderService.class),
        customerorderService, employeeService);
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

  // --- the signs a rate references (#958) -----------------------------------------------------

  /**
   * The form protects the employee only as long as the input comes from its select. A rate with a
   * sign no person carries never matches during controlling: the work silently falls back to the
   * order-wide rate, which is a wrong number rather than an error.
   */
  @Test
  public void should_reject_a_new_rate_for_an_employee_that_does_not_exist() {
    when(employeeService.getEmployeeBySign("ghost")).thenReturn(null);

    assertThatThrownBy(() -> service.save(data("co", null, "ghost")))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_SIGN_UNKNOWN.getCode());
    verify(orderPricingRepository, never()).save(any());
  }

  @Test
  public void should_accept_a_new_rate_for_an_employee_that_exists() {
    service.save(data("co", null, "emp"));

    verify(orderPricingRepository).save(any());
  }

  /** No employee at all is the normal case: the rate then applies to everyone on the order. */
  @Test
  public void should_not_ask_for_an_employee_when_the_rate_names_none() {
    service.save(data("co", null, null));

    verify(employeeService, never()).getEmployeeBySign(any());
    verify(orderPricingRepository).save(any());
  }

  @Test
  public void should_reject_a_new_rate_for_a_customer_order_that_does_not_exist() {
    when(customerorderService.getCustomerorderBySign("gone")).thenReturn(null);

    assertThatThrownBy(() -> service.save(data("gone", null, null)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.BU_CUSTOMERORDER_SIGN_UNKNOWN.getCode());
    verify(orderPricingRepository, never()).save(any());
  }

  /**
   * A rate outlives its order on purpose (#957). Insisting on the order when editing would leave
   * such a rate only deletable — while editing it is how it gets corrected.
   */
  @Test
  public void should_keep_a_rate_editable_whose_customer_order_no_longer_exists() {
    var orphan = pricing("gone", TODAY.minusYears(1), OPEN_END);
    setId(orphan, 5L);
    when(orderPricingRepository.findById(5L)).thenReturn(Optional.of(orphan));
    when(customerorderService.getCustomerorderBySign("gone")).thenReturn(null);

    service.update(5L, data("gone", null, null));

    verify(orderPricingRepository).save(orphan);
  }

  @Test
  public void should_reject_an_edit_that_moves_a_rate_to_an_unknown_employee() {
    var edited = pricing("co", TODAY.minusYears(1), OPEN_END);
    setId(edited, 6L);
    when(orderPricingRepository.findById(6L)).thenReturn(Optional.of(edited));
    when(employeeService.getEmployeeBySign("ghost")).thenReturn(null);

    assertThatThrownBy(() -> service.update(6L, data("co", null, "ghost")))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_SIGN_UNKNOWN.getCode());
    verify(orderPricingRepository, never()).save(any());
  }

  // --- rates left behind on a sign nobody carries (#966) ---------------------------------------

  /**
   * Since a sign change is followed, a rate on a sign no person carries can only be a leftover from
   * before. It resolves to nothing and lets the work fall back to the order-wide rate, so the list
   * has to say so instead of leaving it to be noticed in a total.
   */
  @Test
  public void marks_a_rate_whose_employee_sign_nobody_carries() {
    given(pricingFor("ghost"));

    assertThat(service.getRows(null, false, true))
        .singleElement().extracting(OrderPricingRow::employeeUnknown).isEqualTo(true);
  }

  @Test
  public void leaves_a_rate_alone_whose_employee_still_exists() {
    givenEmployees("emp");
    given(pricingFor("emp"));

    assertThat(service.getRows(null, false, true))
        .singleElement().extracting(OrderPricingRow::employeeUnknown).isEqualTo(false);
  }

  /** A rate without an employee applies to everyone on the order — there is nothing to be unknown. */
  @Test
  public void marks_no_rate_that_names_no_employee() {
    given(pricingFor(null));

    assertThat(service.getRows(null, false, true))
        .singleElement().extracting(OrderPricingRow::employeeUnknown).isEqualTo(false);
  }

  private void givenEmployees(String... signs) {
    when(employeeService.getAllEmployeeSigns()).thenReturn(Set.of(signs));
  }

  private static OrderPricing pricingFor(String employeeSign) {
    var pricing = pricing("co", TODAY.minusYears(1), OPEN_END);
    pricing.setEmployeeSign(employeeSign);
    return pricing;
  }

  private static OrderPricingData data(String customerorderSign, String suborderSign, String employeeSign) {
    return new OrderPricingData(customerorderSign, suborderSign, employeeSign, null, 10000, TODAY, null);
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
  private static void setId(OrderPricing pricing, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(pricing, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
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
