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
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;

/**
 * The segment listing (#779) does not calculate anything of its own: it asks
 * {@link BudgetControllingService} for each budgeted order exactly as that order's own page does and
 * shows the total of that evaluation. What is tested here is therefore the grouping, the segment
 * sums and that the window is passed through unchanged.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetSegmentControllingServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 3, 31);

  private OrderBudgetService orderBudgetService;
  private BudgetControllingService budgetControllingService;
  private CustomerorderService customerorderService;
  private BudgetSegmentControllingService service;

  private static long nextId = 1;

  @BeforeEach
  public void setUp() {
    orderBudgetService = mock(OrderBudgetService.class);
    budgetControllingService = mock(BudgetControllingService.class);
    customerorderService = mock(CustomerorderService.class);
    service = new BudgetSegmentControllingService(orderBudgetService, budgetControllingService,
        customerorderService);
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

    assertThat(total.budgetEuro()).isEqualByComparingTo("1500");
    assertThat(total.revenueEuro()).isEqualByComparingTo("650");
    assertThat(total.costEuro()).isEqualByComparingTo("150");
    assertThat(total.bookedHours()).isEqualTo(Duration.ofHours(6));
    // 650 of 1500 — derived from the sums, not averaged over the two orders.
    assertThat(total.budgetUsedPercent()).isBetween(43.3, 43.4);
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
   * order — it would otherwise be missing from a page that claims to list everything budgeted.
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

  /** Several plans on one order are one line, not one per plan. */
  @Test
  public void should_evaluate_an_order_carrying_several_plans_only_once() {
    var order = order("A", segment("Industrie"));
    givenOrders(order);
    when(orderBudgetService.getAllActiveVisible())
        .thenReturn(List.of(plan("A"), plan("A"), plan("A")));
    givenEvaluations(revenue("A", "100"));

    var segments = service.compute(FROM, UNTIL).segments();

    verify(budgetControllingService).compute(eq("A"), any(), any(), anyBoolean());
    assertThat(segments.get(0).orders()).hasSize(1);
  }

  /** The columns are decided once for the whole page, over every line any of its tables shows. */
  @Test
  public void should_decide_the_columns_over_all_segments_at_once() {
    givenOrders(order("A", segment("Industrie")), order("B", segment("Öffentlicher Sektor")));
    givenEvaluations(
        evaluation("A", section(SectionKind.ORDER_LEVEL, row("100", "150", "10", Duration.ofHours(1)))),
        evaluation("B", section(SectionKind.UNPLANNED, row(null, "50", "10", Duration.ofHours(1)))));

    var columns = service.compute(FROM, UNTIL).columns();

    // The budget of the first order and its overrun are shown in both tables, so that the two stay
    // comparable side by side.
    assertThat(columns.budget()).isTrue();
    assertThat(columns.overrun()).isTrue();
  }

  // --- fixture ---------------------------------------------------------------------------------

  /**
   * The plans are built before the stubbing starts: reading {@code getSign()} off an order mock
   * inside a {@code when(...)} would count as the next interaction to stub and leave the previous
   * one unfinished.
   */
  private void givenOrders(Customerorder... orders) {
    var plans = List.of(orders).stream().map(order -> plan(order.getSign())).toList();
    when(customerorderService.getCustomerordersBySigns(any())).thenReturn(List.of(orders));
    when(orderBudgetService.getAllActiveVisible()).thenReturn(plans);
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
        .bookedHoursBeforeWindow(Duration.ZERO)
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
