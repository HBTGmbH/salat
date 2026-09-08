package org.tb.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.service.BudgetQueryService;
import org.tb.common.LocalDateRange;
import org.tb.common.domain.AuditedEntity;
import org.tb.customer.domain.Customer;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.invoice.domain.InvoiceSuborder;
import org.tb.invoice.service.InvoiceService.InvoiceOptions;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Billing by budget plan (#915). The plan is the more precise boundary than a suborder: it collects
 * what was booked onto that budget, however many suborders it spreads over — so the invoice must
 * contain exactly the assigned bookings of the chosen period and nothing else.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class InvoiceServiceTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);
  private static final LocalDate IN_JAN = LocalDate.of(2026, 1, 15);

  private BudgetQueryService budgetQueryService;
  private TimereportService timereportService;
  private InvoiceService service;

  @BeforeEach
  public void setUp() {
    var customerorderService = mock(CustomerorderService.class);
    var suborderService = mock(SuborderService.class);
    timereportService = mock(TimereportService.class);
    budgetQueryService = mock(BudgetQueryService.class);

    var customerorder = new Customerorder();
    customerorder.setSign("co");
    customerorder.setShortdescription("order");
    customerorder.setCustomer(new Customer());
    setId(customerorder, 1L);
    when(customerorderService.getCustomerorderById(1L)).thenReturn(customerorder);

    // Two invoicable suborders, both booked on in January.
    var first = suborder("01", 10L, customerorder);
    var second = suborder("02", 20L, customerorder);
    when(suborderService.getSubordersByCustomerorderId(1L)).thenReturn(List.of(first, second));
    when(suborderService.getSuborderById(10L)).thenReturn(first);

    when(timereportService.getTimereportsByDatesAndSuborderId(any(), any(), anyLong()))
        .thenAnswer(invocation -> {
          long suborderId = invocation.getArgument(2);
          return suborderId == 10L
              ? List.of(report(100L, 10L), report(101L, 10L))
              : List.of(report(200L, 20L));
        });

    service = new InvoiceService(customerorderService, suborderService, timereportService, budgetQueryService);
  }

  @Test
  public void should_bill_every_booking_of_the_order_without_a_narrowing() {
    var data = service.generateInvoiceData(1L, Optional.empty(), Optional.empty(), january(), options());

    assertThat(data.getSuborders()).flatExtracting(InvoiceSuborder::getTimereports)
        .extracting(tr -> tr.getId()).containsExactly(100L, 101L, 200L);
    verify(budgetQueryService, never()).getAssignedTimereportIds(anyLong());
  }

  /** The plan spans both suborders — which is the point: a suborder selection could not express it. */
  @Test
  public void should_bill_only_the_bookings_assigned_to_the_chosen_plan() {
    when(budgetQueryService.getAssignedTimereportIds(7L)).thenReturn(List.of(101L, 200L));

    var data = service.generateInvoiceData(1L, Optional.empty(), Optional.of(7L), january(), options());

    assertThat(data.getSuborders()).flatExtracting(InvoiceSuborder::getTimereports)
        .extracting(tr -> tr.getId()).containsExactly(101L, 200L);
    assertThat(data.getTotalDuration()).isEqualTo(Duration.ofHours(2));
  }

  /** A plan running longer than the billing month must still yield only that month's bookings. */
  @Test
  public void should_apply_the_period_on_top_of_the_assignment() {
    when(budgetQueryService.getAssignedTimereportIds(7L)).thenReturn(List.of(100L, 101L, 200L));
    // The period reaches the reports query, which is where the day is known.
    service.generateInvoiceData(1L, Optional.empty(), Optional.of(7L), january(), options());

    verify(timereportService).getTimereportsByDatesAndSuborderId(JAN, JAN_31, 10L);
    verify(timereportService).getTimereportsByDatesAndSuborderId(JAN, JAN_31, 20L);
  }

  /** A suborder that contributes nothing to this budget has no place on its invoice. */
  @Test
  public void should_drop_suborders_without_an_assigned_booking() {
    when(budgetQueryService.getAssignedTimereportIds(7L)).thenReturn(List.of(200L));

    var data = service.generateInvoiceData(1L, Optional.empty(), Optional.of(7L), january(), options());

    assertThat(data.getSuborders()).hasSize(1);
    assertThat(data.getSuborders().get(0).getId()).isEqualTo(20L);
  }

  /** An empty plan bills nothing — not, by accident, everything. */
  @Test
  public void should_bill_nothing_for_a_plan_without_assigned_bookings() {
    when(budgetQueryService.getAssignedTimereportIds(7L)).thenReturn(List.of());

    var data = service.generateInvoiceData(1L, Optional.empty(), Optional.of(7L), january(), options());

    assertThat(data.getSuborders()).isEmpty();
    assertThat(data.getTotalDuration()).isEqualTo(Duration.ZERO);
  }

  /** Suborders with no assigned booking only drop out in the budget case, never otherwise. */
  @Test
  public void should_keep_an_empty_suborder_when_no_plan_was_chosen() {
    when(timereportService.getTimereportsByDatesAndSuborderId(any(), any(), anyLong()))
        .thenReturn(List.of());

    var data = service.generateInvoiceData(1L, Optional.empty(), Optional.empty(), january(), options());

    assertThat(data.getSuborders()).hasSize(2);
  }

  // --- fixture ---------------------------------------------------------------------------------

  private static LocalDateRange january() {
    return new LocalDateRange(JAN, JAN_31);
  }

  private static InvoiceOptions options() {
    return InvoiceOptions.builder().showTimereports(true).build();
  }

  private static TimereportDTO report(long id, long suborderId) {
    return TimereportDTO.builder()
        .id(id)
        .suborderId(suborderId)
        .employeeSign("emp")
        .referenceday(IN_JAN)
        .duration(Duration.ofHours(1))
        .taskdescription("work")
        .build();
  }

  private static Suborder suborder(String sign, long id, Customerorder customerorder) {
    var suborder = new Suborder();
    setId(suborder, id);
    suborder.setSign(sign);
    suborder.setShortdescription(sign);
    suborder.setCustomerorder(customerorder);
    suborder.setInvoice('Y');
    return suborder;
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }

}
