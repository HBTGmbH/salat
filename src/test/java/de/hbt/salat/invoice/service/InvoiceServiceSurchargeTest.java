package de.hbt.salat.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.budget.service.BudgetQueryService;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.invoice.domain.InvoiceData;
import de.hbt.salat.invoice.domain.InvoiceGroup;
import de.hbt.salat.invoice.domain.InvoiceGrouping;
import de.hbt.salat.invoice.domain.InvoiceLine;
import de.hbt.salat.invoice.domain.InvoiceSurcharge;
import de.hbt.salat.invoice.domain.InvoiceTimereport;
import de.hbt.salat.invoice.service.InvoiceService.InvoiceOptions;
import de.hbt.salat.jira.service.JiraTicketService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SurchargeKind;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Time surcharges on the invoice (#1470): positions of their own per kind and rate, or part of the
 * hours of their booking. Either way only what is billed carries a surcharge, and the total including
 * the surcharges is the same.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class InvoiceServiceSurchargeTest {

  private static final long ORDER = 1L;
  private static final long S10 = 10L;
  private static final long S20 = 20L;
  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

  private final Map<Long, List<TimereportDTO>> bookings = new HashMap<>();
  private InvoiceService service;

  @BeforeEach
  void setUp() {
    var customerorderService = mock(CustomerorderService.class);
    var suborderService = mock(SuborderService.class);
    var timereportService = mock(TimereportService.class);

    var customerorder = new Customerorder();
    customerorder.setSign("co");
    customerorder.setShortdescription("order");
    customerorder.setCustomer(new Customer());
    setId(customerorder, ORDER);
    when(customerorderService.getCustomerorderById(ORDER)).thenReturn(customerorder);
    when(suborderService.getSubordersByCustomerorderId(ORDER))
        .thenReturn(List.of(suborder("01", S10, customerorder), suborder("02", S20, customerorder)));
    when(timereportService.getTimereportsByDatesAndSuborderId(any(), any(), anyLong()))
        .thenAnswer(invocation -> bookings.getOrDefault((Long) invocation.getArgument(2), List.of()));

    service = new InvoiceService(customerorderService, suborderService, timereportService,
        mock(BudgetQueryService.class), mock(JiraTicketService.class));
  }

  @Test
  void the_surcharges_are_positions_of_their_own_per_kind_and_rate() {
    book(100, S10, 1, 240, SurchargeKind.SUNDAY, 100, null);
    book(101, S10, 2, 120, SurchargeKind.NIGHT, 25, null);
    book(102, S20, 3, 60, SurchargeKind.SUNDAY, 100, null);
    book(103, S20, 4, 60, SurchargeKind.SUNDAY, 50, null);
    book(104, S20, 5, 30, null, null, null);

    var data = generate(true);

    assertThat(data.isSurchargesSeparate()).isTrue();
    assertThat(data.getSurcharges()).extracting(InvoiceSurcharge::kind, InvoiceSurcharge::percent,
            InvoiceSurcharge::basis, InvoiceSurcharge::credit)
        .containsExactly(
            tuple(SurchargeKind.NIGHT, 25, minutes(120), minutes(30)),
            tuple(SurchargeKind.SUNDAY, 50, minutes(60), minutes(30)),
            tuple(SurchargeKind.SUNDAY, 100, minutes(300), minutes(300)));
    // the bookings stay as booked
    assertThat(data.getTotalDurationVisible()).isEqualTo(minutes(510));
    assertThat(data.getTotalDurationVisibleWithSurcharges()).isEqualTo(minutes(870));
  }

  @Test
  void only_what_is_billed_carries_a_surcharge() {
    book(100, S10, 1, 240, SurchargeKind.SUNDAY, 100, null);
    book(200, S20, 2, 60, SurchargeKind.SUNDAY, 100, null);
    book(201, S20, 3, 60, SurchargeKind.NIGHT, 25, null);

    var data = generate(true);
    // deselected: one booking, and a whole suborder
    timereport(data, 100).setVisible(false);
    data.getSuborders().stream().filter(s -> s.getId() == S20).findFirst().orElseThrow().setVisible(false);

    assertThat(data.getSurcharges()).isEmpty();
    assertThat(data.getTotalDurationVisibleWithSurcharges()).isEqualTo(Duration.ZERO);
    // still offered in the preview, where ticking a booking again brings its surcharge back
    assertThat(data.getSurchargesLoaded()).extracting(InvoiceSurcharge::getKey).containsExactly("NIGHT-25", "SUNDAY-100");
  }

  @Test
  void switched_off_the_surcharge_is_part_of_the_hours_of_its_booking() {
    book(100, S10, 1, 240, SurchargeKind.SUNDAY, 100, null);
    book(101, S10, 2, 120, SurchargeKind.NIGHT, 25, null);

    var data = generate(false);

    assertThat(data.isSurchargesPresent()).isTrue();
    assertThat(data.isSurchargesSeparate()).isFalse();
    assertThat(data.getSurcharges()).isEmpty();
    assertThat(data.getGroups().get(0).getLines()).extracting(InvoiceLine::getShare)
        .containsExactly(minutes(480), minutes(150));
    assertThat(timereport(data, 100).getBookedDuration()).isEqualTo(minutes(240));
    // the same total as with the surcharges listed apart
    assertThat(data.getTotalDurationVisible()).isEqualTo(minutes(630));
    assertThat(data.getTotalDurationVisibleWithSurcharges()).isEqualTo(minutes(630));
  }

  /** Divided among tickets (#1390), duration and surcharge are divided together. */
  @Test
  void switched_off_a_booking_under_several_tickets_divides_the_surcharge_with_its_duration() {
    book(100, S10, 1, 241, SurchargeKind.SUNDAY, 100, null, "ABC-1", "ABC-2");

    var data = service.generateInvoiceData(ORDER, Optional.empty(), Optional.empty(), january(),
        InvoiceOptions.builder().showTimereports(true).separateSurcharges(false).grouping(InvoiceGrouping.TICKET).build());

    assertThat(data.getGroups()).extracting(InvoiceGroup::getTotalDuration).containsExactly(minutes(241), minutes(241));
  }

  @Test
  void without_a_flagged_booking_the_invoice_is_unchanged() {
    book(100, S10, 1, 240, null, null, null);

    var data = generate(true);

    assertThat(data.isSurchargesPresent()).isFalse();
    assertThat(data.getSurcharges()).isEmpty();
    assertThat(data.getTotalDurationVisibleWithSurcharges()).isEqualTo(data.getTotalDurationVisible());
  }

  /** On standby the credit counts nowhere, as on the time account (#1469). */
  @Test
  void a_flag_on_standby_earns_no_surcharge() {
    book(100, S10, 1, 240, SurchargeKind.SUNDAY, 100, OrderType.BEREITSCHAFT);

    assertThat(generate(true).getSurcharges()).isEmpty();
    assertThat(generate(false).getTotalDurationVisible()).isEqualTo(minutes(240));
  }

  // --- fixture ---------------------------------------------------------------------------------

  private InvoiceData generate(boolean separate) {
    var options = InvoiceOptions.builder().showTimereports(true).separateSurcharges(separate).build();
    return service.generateInvoiceData(ORDER, Optional.empty(), Optional.empty(), january(), options);
  }

  private static LocalDateRange january() {
    return new LocalDateRange(JAN, JAN_31);
  }

  private static InvoiceTimereport timereport(InvoiceData data, long id) {
    return data.getSuborders().stream().flatMap(s -> s.getTimereports().stream())
        .filter(t -> t.getId() == id).findFirst().orElseThrow();
  }

  private void book(long id, long suborderId, int day, int minutes, SurchargeKind kind, Integer percent,
                    OrderType orderType, String... references) {
    var duration = minutes(minutes);
    bookings.computeIfAbsent(suborderId, k -> new ArrayList<>()).add(TimereportDTO.builder()
        .id(id)
        .suborderId(suborderId)
        .employeeSign("emp")
        .referenceday(JAN.plusDays(day))
        .duration(duration)
        .taskdescription("work")
        .ticketReferences(List.of(references))
        .orderType(orderType)
        .surchargeKind(kind)
        .surchargePercent(percent)
        .surchargeCredit(kind == null ? Duration.ZERO : Timereport.surchargeCreditFor(duration, percent))
        .build());
  }

  private static Duration minutes(int minutes) {
    return Duration.ofMinutes(minutes);
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
