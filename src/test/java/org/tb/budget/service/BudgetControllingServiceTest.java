package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.common.test.FixedClock;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.PublicholidayService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Work booked on a suborder that is not invoiceable is not billed to the customer, so it must not
 * show up as revenue anywhere in the controlling (#897).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetControllingServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 6, 30);

  private BudgetControllingService service;

  @BeforeEach
  public void setUp() {
    var customerorderService = mock(CustomerorderService.class);
    var suborderService = mock(SuborderService.class);
    var timereportService = mock(TimereportService.class);
    var orderBudgetRepository = mock(OrderBudgetRepository.class);
    var orderPricingService = mock(OrderPricingService.class);
    var employeeCostService = mock(EmployeeCostService.class);
    var publicholidayService = mock(PublicholidayService.class);

    var customerorder = mock(Customerorder.class);
    when(customerorder.getId()).thenReturn(1L);
    when(customerorder.getSign()).thenReturn("co");

    var billed = suborder(customerorder, "01", 'Y', 10L);
    var unbilled = suborder(customerorder, "02", 'N', 20L);

    when(customerorderService.getCustomerorderBySign("co")).thenReturn(customerorder);
    when(suborderService.getSubordersByCustomerorderId(anyLong())).thenReturn(List.of(billed, unbilled));
    when(publicholidayService.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());
    when(orderBudgetRepository.findByCustomerorderSign("co")).thenReturn(List.of());
    when(timereportService.getTimereportsByDatesAndCustomerOrderId(any(), any(), anyLong()))
        .thenReturn(List.of(eightHoursOn(10L), eightHoursOn(20L)));
    // One order-wide rate of 100 EUR/h, so both suborders would earn the same if invoiceable.
    when(orderPricingService.lookupFor(any())).thenReturn(OrderPricingLookup.of(List.of(orderWideRate())));

    service = new BudgetControllingService(customerorderService, suborderService, timereportService,
        orderBudgetRepository, orderPricingService, employeeCostService, publicholidayService);
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_earn_revenue_for_an_invoiceable_suborder() {
    assertThat(row("co/01").revenueEuro()).isEqualByComparingTo("800.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_earn_no_revenue_for_a_suborder_that_is_not_invoiceable() {
    var row = row("co/02");

    assertThat(row.revenueEuro()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(row.coveredRevenueEuro()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_still_report_the_booked_hours_of_a_suborder_that_is_not_invoiceable() {
    assertThat(row("co/02").bookedHours()).isEqualTo(Duration.ofHours(8));
  }

  /** A definite zero rather than "unknown", which would suppress the order total. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_forecast_no_revenue_for_a_suborder_that_is_not_invoiceable() {
    var row = row("co/02");

    assertThat(row.forecastHours()).isNotNull();
    assertThat(row.forecastRevenueEuro()).isEqualByComparingTo(BigDecimal.ZERO);
    // Without a budget every remaining day is uncovered, which is where projected revenue shows up.
    assertThat(row.forecastUncoveredRevenueEuro()).isNull();
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_only_the_invoiceable_suborder_in_the_order_total() {
    assertThat(compute().total().revenueEuro()).isEqualByComparingTo("800.00");
  }

  private BudgetControllingRow row(String completeOrderSign) {
    return compute().suborderRows().stream()
        .filter(r -> completeOrderSign.equals(r.sign()))
        .findFirst().orElseThrow();
  }

  private org.tb.budget.domain.BudgetControllingResult compute() {
    return service.compute("co", FROM, UNTIL, false);
  }

  private static Suborder suborder(Customerorder customerorder, String sign, char invoice, long id) {
    var suborder = mock(Suborder.class);
    when(suborder.getId()).thenReturn(id);
    when(suborder.getSign()).thenReturn(sign);
    when(suborder.getCustomerorder()).thenReturn(customerorder);
    when(suborder.getCompleteOrderSign()).thenReturn("co/" + sign);
    when(suborder.isInvoiceable()).thenReturn(invoice == 'Y');
    return suborder;
  }

  private static TimereportDTO eightHoursOn(long suborderId) {
    return TimereportDTO.builder()
        .suborderId(suborderId)
        .employeeSign("emp")
        .referenceday(LocalDate.of(2026, 6, 10))
        .duration(Duration.ofHours(8))
        .build();
  }

  private static OrderPricing orderWideRate() {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign("co");
    pricing.setPriceCentsPerHour(10000);
    pricing.setValidFrom(LocalDate.of(2026, 1, 1));
    pricing.setValidUntil(LocalDate.of(2026, 12, 31));
    return pricing;
  }

}
