package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.BudgetControllingGroup;
import org.tb.budget.domain.BudgetControllingResult;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.BudgetControllingSection;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.SectionKind;
import org.tb.budget.domain.SegmentControllingGroup;
import org.tb.budget.domain.SegmentControllingOrder;
import org.tb.common.LocalDateRange;
import org.tb.common.domain.AuditedEntity;
import org.tb.customer.domain.Customer;
import org.tb.customer.domain.CustomerSegment;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;

/**
 * The segment listing (#779) does not calculate anything of its own: it asks
 * {@link BudgetControllingService} for each order exactly as that order's own page does and shows
 * the total of that evaluation. What is tested here is therefore which orders reach the page, the
 * grouping, the segment sums and that the window is passed through unchanged.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetSegmentControllingServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 3, 31);

  private OrderBudgetService orderBudgetService;
  private OrderFlatRateService orderFlatRateService;
  private BudgetControllingService budgetControllingService;
  private CustomerorderService customerorderService;
  private TimereportService timereportService;
  private BudgetSegmentControllingService service;

  private static long nextId = 1;

  @BeforeEach
  public void setUp() {
    orderBudgetService = mock(OrderBudgetService.class);
    orderFlatRateService = mock(OrderFlatRateService.class);
    budgetControllingService = mock(BudgetControllingService.class);
    customerorderService = mock(CustomerorderService.class);
    timereportService = mock(TimereportService.class);
    service = new BudgetSegmentControllingService(orderBudgetService, orderFlatRateService,
        budgetControllingService, customerorderService, timereportService);

    when(orderBudgetService.getAllActiveVisible()).thenReturn(List.of());
    when(orderFlatRateService.getCustomerorderSignsWithFlatRate()).thenReturn(List.of());
    when(timereportService.getCustomerorderSignsWithReportsBetween(any(), any())).thenReturn(List.of());
  }

  @Test
  public void should_group_the_orders_by_the_segment_of_their_customer() {
    var publicSector = segment("Öffentlicher Sektor");
    var industry = segment("Industrie");
    givenOrders(order("A", publicSector), order("B", industry), order("C", publicSector));
    givenEvaluations(revenue("A", "100"), revenue("B", "200"), revenue("C", "300"));

    var segments = service.compute(FROM, UNTIL).segments();

    // By segment name, orders by sign inside it.
    assertThat(segments).extracting(SegmentControllingGroup::segmentName)
        .containsExactly("Industrie", "Öffentlicher Sektor");
    assertThat(segments.get(1).orders()).extracting(SegmentControllingOrder::customerorderSign)
        .containsExactly("A", "C");
  }

  @Test
  public void should_sum_the_orders_of_a_segment() {
    var segment = segment("Industrie");
    givenOrders(order("A", segment), order("B", segment));
    givenEvaluations(
        evaluation("A", section(SectionKind.ORDER_LEVEL, row("1000", "400", "100",
            Duration.ofHours(4)))),
        evaluation("B", section(SectionKind.ORDER_LEVEL, row("500", "250", "50",
            Duration.ofHours(2)))));

    var total = service.compute(FROM, UNTIL).segments().get(0).total();

    assertThat(total.revenueEuro()).isEqualByComparingTo("650");
    assertThat(total.costEuro()).isEqualByComparingTo("150");
    assertThat(total.bookedHours()).isEqualTo(Duration.ofHours(6));
    assertThat(total.grossProfitEuro()).isEqualByComparingTo("500");
    // 500 of 650 — derived from the sums, not averaged over the two orders.
    assertThat(total.grossProfitMarginPercent()).isBetween(76.9, 77.0);
  }

  /**
   * The orders of a segment answer to different plans, and some to none at all. A sum over their
   * budgets would be a figure nobody agreed to, so neither the line nor the table has one.
   */
  @Test
  public void should_report_no_budget_for_a_segment() {
    var segment = segment("Industrie");
    givenOrders(order("A", segment), order("B", segment));
    givenEvaluations(
        evaluation("A", section(SectionKind.ORDER_LEVEL, row("1000", "400", "100", Duration.ofHours(4)))),
        evaluation("B", section(SectionKind.UNPLANNED, row(null, "250", "50", Duration.ofHours(2)))));

    var result = service.compute(FROM, UNTIL);

    assertThat(result.segments().get(0).total().budgetEuro()).isNull();
    assertThat(result.columns().budget()).isFalse();
    assertThat(result.columns().overrun()).isFalse();
    // With the budget columns gone, what was earned before the window has nothing left to explain.
    assertThat(result.columns().revenueBeforeWindow()).isFalse();
    assertThat(result.columns().planned()).isFalse();
  }

  /** The line is the order's own total, so it has to carry the order's name rather than "Summe". */
  @Test
  public void should_name_the_line_after_the_order_and_its_customer() {
    var order = order("A", segment("Industrie"));
    when(order.getShortdescription()).thenReturn("Support 2026");
    when(order.getCustomer().getShortname()).thenReturn("Kunde");
    givenOrders(order);
    givenEvaluations(revenue("A", "100"));

    var line = service.compute(FROM, UNTIL).segments().get(0).orders().get(0).total();

    assertThat(line.sign()).isEqualTo("A");
    assertThat(line.label()).isEqualTo("Kunde - Support 2026");
  }

  /**
   * An order whose customer is in no segment is a gap in the master data, not a reason to drop the
   * order — it would otherwise be missing from a page that claims to list everything.
   */
  @Test
  public void should_list_the_orders_without_a_segment_in_a_group_of_their_own_at_the_end() {
    givenOrders(order("A", segment("Industrie")), order("B", null));
    givenEvaluations(revenue("A", "100"), revenue("B", "200"));

    var segments = service.compute(FROM, UNTIL).segments();

    assertThat(segments).extracting(SegmentControllingGroup::hasSegment).containsExactly(true, false);
    assertThat(segments.get(1).orders()).extracting(SegmentControllingOrder::customerorderSign)
        .containsExactly("B");
  }

  /** Nothing happened on the order inside the window — a line of dashes helps nobody. */
  @Test
  public void should_leave_out_an_order_whose_evaluation_has_nothing_to_report() {
    givenOrders(order("A", segment("Industrie")), order("B", segment("Industrie")));
    givenEvaluations(revenue("A", "100"),
        new BudgetControllingResult("B", "order B", null, null, window(), List.of()));

    var segments = service.compute(FROM, UNTIL).segments();

    assertThat(segments.get(0).orders()).extracting(SegmentControllingOrder::customerorderSign)
        .containsExactly("A");
  }

  /** Every order is evaluated once, over the window the page was asked for, costs included. */
  @Test
  public void should_evaluate_every_budgeted_order_over_the_chosen_window() {
    givenOrders(order("A", segment("Industrie")));
    givenEvaluations(revenue("A", "100"));

    service.compute(FROM, UNTIL);

    verify(budgetControllingService).compute("A", FROM, UNTIL, true);
  }

  /** Several plans on one order, and bookings besides — still one line and one evaluation. */
  @Test
  public void should_evaluate_an_order_only_once_however_often_it_is_named() {
    givenOrders(order("A", segment("Industrie")));
    when(orderBudgetService.getAllActiveVisible())
        .thenReturn(List.of(plan("A"), plan("A"), plan("A")));
    when(orderFlatRateService.getCustomerorderSignsWithFlatRate()).thenReturn(List.of("A"));
    givenEvaluations(revenue("A", "100"));

    var segments = service.compute(FROM, UNTIL).segments();

    verify(budgetControllingService).compute(eq("A"), any(), any(), anyBoolean());
    assertThat(segments.get(0).orders()).hasSize(1);
  }

  /**
   * The reason the page does not start from the plans: an order billed by the hour without any
   * budget still earns and still costs, and leaving it out would make its segment look more or less
   * profitable than it is.
   */
  @Test
  public void should_list_an_order_that_has_no_budget_plan_at_all() {
    givenOrders(order("A", segment("Industrie")));
    when(orderBudgetService.getAllActiveVisible()).thenReturn(List.of());
    givenEvaluations(evaluation("A", section(SectionKind.UNPLANNED,
        row(null, "900", "300", Duration.ofHours(9)))));

    var segment = service.compute(FROM, UNTIL).segments().get(0);

    assertThat(segment.orders()).extracting(SegmentControllingOrder::customerorderSign)
        .containsExactly("A");
    assertThat(segment.total().grossProfitEuro()).isEqualByComparingTo("600");
  }

  /** A plan without a booking in the window can still carry a flat rate falling due. */
  @Test
  public void should_list_a_budgeted_order_nobody_booked_on() {
    var order = order("A", segment("Industrie"));
    when(orderBudgetService.getAllActiveVisible()).thenReturn(List.of(plan("A")));
    when(customerorderService.getCustomerordersBySigns(any())).thenReturn(List.of(order));
    givenEvaluations(revenue("A", "100"));

    assertThat(service.compute(FROM, UNTIL).segments().get(0).orders())
        .extracting(SegmentControllingOrder::customerorderSign).containsExactly("A");
  }

  /** An order carrying nothing but a flat rate is pure revenue and has to be in the picture. */
  @Test
  public void should_list_an_order_that_is_named_only_by_its_flat_rate() {
    var order = order("A", segment("Industrie"));
    when(orderFlatRateService.getCustomerorderSignsWithFlatRate()).thenReturn(List.of("A"));
    when(customerorderService.getCustomerordersBySigns(any())).thenReturn(List.of(order));
    givenEvaluations(revenue("A", "100"));

    assertThat(service.compute(FROM, UNTIL).segments().get(0).orders())
        .extracting(SegmentControllingOrder::customerorderSign).containsExactly("A");
  }

  /** The columns are decided once for the whole page, over every line any of its tables shows. */
  @Test
  public void should_decide_the_columns_over_all_segments_at_once() {
    givenOrders(order("A", segment("Industrie")), order("B", segment("Öffentlicher Sektor")));
    givenEvaluations(
        evaluation("A", section(SectionKind.ORDER_LEVEL,
            row("100", "150", "10", Duration.ofHours(1)).toBuilder()
                .flatRateRevenueEuro(new BigDecimal("40")).build())),
        evaluation("B", section(SectionKind.UNPLANNED, row(null, "50", "10", Duration.ofHours(1)))));

    var columns = service.compute(FROM, UNTIL).columns();

    // Only the first order has a flat rate, and the column appears in both tables, so that the two
    // stay comparable side by side.
    assertThat(columns.flatRate()).isTrue();
  }

  // --- fixture ---------------------------------------------------------------------------------

  /**
   * Orders that were booked on in the window — the usual case, and the one that does not depend on a
   * plan existing at all.
   *
   * <p>The signs are read off the mocks before the stubbing starts: a {@code getSign()} inside a
   * {@code when(...)} would count as the next interaction to stub and leave the previous one
   * unfinished.
   */
  private void givenOrders(Customerorder... orders) {
    var signs = List.of(orders).stream().map(Customerorder::getSign).toList();
    when(customerorderService.getCustomerordersBySigns(any())).thenReturn(List.of(orders));
    when(timereportService.getCustomerorderSignsWithReportsBetween(any(), any())).thenReturn(signs);
  }

  private void givenEvaluations(BudgetControllingResult... results) {
    for (var result : results) {
      when(budgetControllingService.compute(eq(result.customerorderSign()), any(), any(), anyBoolean()))
          .thenReturn(result);
    }
  }

  private static BudgetControllingResult revenue(String sign, String revenue) {
    return evaluation(sign, section(SectionKind.ORDER_LEVEL,
        row("1000", revenue, "0", Duration.ofHours(1))));
  }

  private static BudgetControllingResult evaluation(String sign, BudgetControllingSection... sections) {
    return new BudgetControllingResult(sign, "order " + sign, null, null, window(), List.of(sections));
  }

  private static LocalDateRange window() {
    return new LocalDateRange(FROM, UNTIL);
  }

  private static BudgetControllingSection section(SectionKind kind, BudgetControllingRow total) {
    return new BudgetControllingSection(kind, window(), List.of(), null, null,
        List.of(new BudgetControllingGroup(null, null, null, List.of(total), null, null, null)), total);
  }

  private static BudgetControllingRow row(String budget, String revenue, String cost, Duration booked) {
    return BudgetControllingRow.builder()
        .plannedHours(Duration.ZERO)
        .revenueBeforeWindowEuro(BigDecimal.ZERO)
        .bookedHours(booked)
        .budgetEuro(budget == null ? null : new BigDecimal(budget))
        .revenueEuro(new BigDecimal(revenue))
        .flatRateRevenueEuro(BigDecimal.ZERO)
        .costEuro(new BigDecimal(cost))
        .build();
  }

  private static Customerorder order(String sign, CustomerSegment segment) {
    var customer = mock(Customer.class);
    when(customer.getSegment()).thenReturn(segment);
    var customerorder = mock(Customerorder.class);
    when(customerorder.getSign()).thenReturn(sign);
    when(customerorder.getShortdescription()).thenReturn("order " + sign);
    when(customerorder.getCustomer()).thenReturn(customer);
    return customerorder;
  }

  private static CustomerSegment segment(String name) {
    var segment = new CustomerSegment();
    setId(segment, nextId++);
    segment.setName(name);
    return segment;
  }

  private static OrderBudget plan(String customerorderSign) {
    var plan = new OrderBudget();
    setId(plan, nextId++);
    plan.setCustomerorderSign(customerorderSign);
    plan.setActive(true);
    return plan;
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
  private static void setId(AuditedEntity entity, long id) {
    try {
      Field field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }
}
