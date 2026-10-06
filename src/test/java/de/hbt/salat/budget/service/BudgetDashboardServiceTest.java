package de.hbt.salat.budget.service;

import static de.hbt.salat.testutils.ReferenceTestUtils.suborderWithId;
import static de.hbt.salat.testutils.ReferenceTestUtils.employeeWithId;
import static de.hbt.salat.testutils.ReferenceTestUtils.customerorderWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.BudgetDashboardRow;
import de.hbt.salat.budget.domain.FlatRateRhythm;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderBudgetAdjustment;
import de.hbt.salat.budget.domain.OrderBudgetScopeEntry;
import de.hbt.salat.budget.domain.OrderFlatRate;
import de.hbt.salat.budget.domain.OrderFlatRateLookup;
import de.hbt.salat.budget.domain.OrderPricing;
import de.hbt.salat.budget.domain.OrderPricingLookup;
import de.hbt.salat.budget.domain.PlanBooking;
import de.hbt.salat.budget.domain.ProgressMode;
import de.hbt.salat.budget.domain.ProgressStatus;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.TimereportBudgetAssignmentRepository;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.dailyreport.domain.Publicholiday;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.service.PublicholidayService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * The dashboard filters by customer segment and by order responsible (#920). Both filters are
 * resolved to the ids of the customer orders (#1340) and handed to the query; two set filters
 * intersect. What the
 * user is allowed to see is decided further down, in {@code OrderBudgetService}.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetDashboardServiceTest {

  private static final long SEGMENT_ID = 7L;
  private static final long RESPONSIBLE_ID = 42L;
  private static final long ORDER_A = 1L;
  private static final long ORDER_B = 2L;
  private static final long ORDER_C = 3L;

  private OrderBudgetService orderBudgetService;
  private BudgetControllingService budgetControllingService;
  private CustomerorderService customerorderService;
  private BudgetDashboardService service;

  @BeforeEach
  public void setUp() {
    orderBudgetService = mock(OrderBudgetService.class);
    budgetControllingService = mock(BudgetControllingService.class);
    customerorderService = mock(CustomerorderService.class);
    service = new BudgetDashboardService(orderBudgetService, budgetControllingService, customerorderService);

    when(orderBudgetService.getAllActiveVisible(any())).thenReturn(List.of());
    when(budgetControllingService.computeUtilizationInfos(anyList())).thenReturn(Map.of());
  }

  @Test
  public void without_a_filter_nothing_is_restricted() {
    service.computeDashboard(null, null);

    assertThat(capturedRestriction()).isNull();
    verify(customerorderService, never()).getIdsByCustomerSegmentId(SEGMENT_ID);
    verify(customerorderService, never()).getIdsByResponsibleHbtEmployeeId(RESPONSIBLE_ID);
  }

  @Test
  public void the_segment_filter_restricts_to_the_orders_of_that_segment() {
    when(customerorderService.getIdsByCustomerSegmentId(SEGMENT_ID)).thenReturn(List.of(ORDER_A, ORDER_B));

    service.computeDashboard(SEGMENT_ID, null);

    assertThat(capturedRestriction()).containsExactlyInAnyOrder(ORDER_A, ORDER_B);
  }

  @Test
  public void the_responsible_filter_restricts_to_the_orders_of_that_employee() {
    when(customerorderService.getIdsByResponsibleHbtEmployeeId(RESPONSIBLE_ID)).thenReturn(List.of(ORDER_B, ORDER_C));

    service.computeDashboard(null, RESPONSIBLE_ID);

    assertThat(capturedRestriction()).containsExactlyInAnyOrder(ORDER_B, ORDER_C);
  }

  @Test
  public void both_filters_together_keep_only_what_they_agree_on() {
    when(customerorderService.getIdsByCustomerSegmentId(SEGMENT_ID)).thenReturn(List.of(ORDER_A, ORDER_B));
    when(customerorderService.getIdsByResponsibleHbtEmployeeId(RESPONSIBLE_ID)).thenReturn(List.of(ORDER_B, ORDER_C));

    service.computeDashboard(SEGMENT_ID, RESPONSIBLE_ID);

    assertThat(capturedRestriction()).containsExactly(ORDER_B);
  }

  /**
   * An empty restriction is an answer, not a missing filter: no order matches, so no plan may show
   * up. Were it turned into {@code null} the dashboard would list everything instead.
   */
  @Test
  public void a_filter_matching_no_order_yields_an_empty_restriction_not_an_absent_one() {
    when(customerorderService.getIdsByCustomerSegmentId(SEGMENT_ID)).thenReturn(List.of());

    service.computeDashboard(SEGMENT_ID, null);

    assertThat(capturedRestriction()).isNotNull().isEmpty();
  }

  @Test
  public void filters_that_have_no_order_in_common_yield_an_empty_restriction() {
    when(customerorderService.getIdsByCustomerSegmentId(SEGMENT_ID)).thenReturn(List.of(ORDER_A));
    when(customerorderService.getIdsByResponsibleHbtEmployeeId(RESPONSIBLE_ID)).thenReturn(List.of(ORDER_C));

    service.computeDashboard(SEGMENT_ID, RESPONSIBLE_ID);

    assertThat(capturedRestriction()).isNotNull().isEmpty();
  }

  @SuppressWarnings("unchecked")
  private Collection<Long> capturedRestriction() {
    var captor = ArgumentCaptor.forClass(Collection.class);
    verify(orderBudgetService).getAllActiveVisible(captor.capture());
    return captor.getValue();
  }

  /**
   * The figures of every row, computed by the real {@link BudgetControllingService} over a fixed
   * set of orders, plans, bookings, rates and flat rates (#1222). The collaborators below it answer
   * from that one data set, whatever they are asked, so the expected numbers do not depend on how
   * the service loads its data — they pinned the rows before the loading was rebuilt, and they
   * have to hold after it.
   *
   * <p>The fixture covers what decides a figure: a plan-bound and a personal rate, a booking after
   * today, one on a suborder that is not invoiceable, one on a hidden suborder, an unassigned one, a
   * named, an unnamed and an unallocatable flat rate, an adjustment that takes effect in the future,
   * a plan that has ended, an open-ended one, one without a budget, time and scope progress with
   * holidays, and an order the user may not see.
   */
  @Nested
  @FixedClock("2026-06-15T10:00:00")
  class Figures {

    private static final long EMPLOYEE = 5L;
    private static final long OTHER_EMPLOYEE = 6L;

    private final List<Customerorder> orders = new ArrayList<>();
    private final List<Suborder> suborders = new ArrayList<>();
    private final List<OrderBudget> plans = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();
    private final List<OrderPricing> rates = new ArrayList<>();
    private final List<OrderFlatRate> flatRates = new ArrayList<>();
    private final List<Publicholiday> holidays = new ArrayList<>();
    /** Every order sign, order id and plan id a collaborator was asked about. */
    private final Set<Object> asked = new HashSet<>();

    /** A booking as the database holds it: the report and the plan it is assigned to, if any. */
    private record Booking(TimereportDTO report, Long planId) {}

    private BudgetDashboardService figures;
    private long nextId = 1000;

    @BeforeEach
    public void setUpFixture() {
      var a = order(1, "A");
      var b = order(2, "B");
      var c = order(3, "C");
      var a01 = suborder(11, a, "01", null, 'Y', false);
      var a01x = suborder(12, a, "X", a01, 'Y', false);
      var a02 = suborder(13, a, "02", null, 'N', false);
      var a03 = suborder(14, a, "03", null, 'Y', true);
      var b01 = suborder(21, b, "01", null, 'Y', false);
      var c01 = suborder(31, c, "01", null, 'Y', false);

      // A: two plans side by side on level 1. The second adjustment of the first one takes effect
      // in September and must not count yet.
      var p1 = plan(101, "A", "A/01", "2026-01-01", "2026-12-31", ProgressMode.TIME);
      adjust(p1, "1000", "2026-01-01");
      adjust(p1, "500", "2026-09-01");
      p1.setAlertThresholdPercent(80);
      var p2 = plan(102, "A", "A/02", "2026-01-01", "2026-12-31", ProgressMode.TIME);
      adjust(p2, "2000", "2026-01-01");
      // B: an order-wide plan that has ended, measured by scope, then an open-ended plan on level 1
      // without any budget.
      var p3 = plan(103, "B", null, "2026-02-01", "2026-05-31", ProgressMode.SCOPE);
      adjust(p3, "800", "2026-02-01");
      scope(p3, "2026-03-01", 40);
      scope(p3, "2026-05-15", 90);
      scope(p3, "2026-07-01", 100);
      var p4 = plan(104, "B", "B/01", "2026-06-01", "2999-12-31", ProgressMode.TIME);
      // C: nobody on this dashboard may see it.
      var p5 = plan(105, "C", null, "2026-01-01", "2026-12-31", ProgressMode.TIME);
      adjust(p5, "100", "2026-01-01");

      book(a01x, "2026-03-10", 8, 0, EMPLOYEE, p1);
      book(a01, "2026-06-20", 4, 0, EMPLOYEE, p1);         // after today
      book(a02, "2026-04-01", 8, 0, EMPLOYEE, p2);         // not invoiceable
      book(a03, "2026-04-02", 8, 0, EMPLOYEE, p1);         // hidden suborder
      book(a01, "2026-04-03", 2, 30, EMPLOYEE, null);      // unassigned
      book(a01, "2026-05-04", 1, 1, OTHER_EMPLOYEE, p1);   // personal rate, odd minutes
      book(b01, "2026-03-15", 8, 0, EMPLOYEE, p3);
      book(b01, "2026-06-05", 3, 0, EMPLOYEE, p4);
      book(c01, "2026-03-01", 8, 0, EMPLOYEE, p5);

      rates.add(rate("A", null, 10000));
      var planBound = rate("A", null, 12000);
      planBound.setOrderBudget(p1);
      rates.add(planBound);
      var personal = rate("A", null, 15000);
      personal.setEmployee(employeeWithId(OTHER_EMPLOYEE));
      rates.add(personal);
      rates.add(rate("B", null, 10000));
      rates.add(rate("C", null, 10000));

      var monthly = flatRate("A", FlatRateRhythm.MONTHLY, "2026-01-01", "2026-12-31", "100");
      monthly.setOrderBudget(p1);
      flatRates.add(monthly);
      // Order-wide and unnamed: neither plan of A covers the whole order, so it lands nowhere.
      flatRates.add(flatRate("A", FlatRateRhythm.ONCE, "2026-02-01", "2026-02-01", "50"));
      // Unnamed, covered by the order-wide plan of B alone.
      flatRates.add(flatRate("B", FlatRateRhythm.ONCE, "2026-03-31", "2026-03-31", "200"));
      flatRates.add(flatRate("C", FlatRateRhythm.ONCE, "2026-03-31", "2026-03-31", "999"));

      holidays.add(new Publicholiday(LocalDate.of(2026, 1, 1), "Neujahr"));
      holidays.add(new Publicholiday(LocalDate.of(2026, 4, 3), "Karfreitag"));
      holidays.add(new Publicholiday(LocalDate.of(2026, 12, 25), "Weihnachten"));

      figures = new BudgetDashboardService(visiblePlans(), controllingService(), mock(CustomerorderService.class));
    }

    @Test
    public void every_row_carries_the_figures_of_its_plan() {
      var rows = figures.computeDashboard(null, null);

      assertThat(rows).extracting(BudgetDashboardRow::budgetId,
              BudgetDashboardRow::customerorderSign,
              BudgetDashboardRow::customerorderName,
              BudgetDashboardRow::evaluatedUntil,
              BudgetDashboardRow::budgetEuro,
              BudgetDashboardRow::coveredRevenueEuro,
              BudgetDashboardRow::utilizationPercent,
              BudgetDashboardRow::progressPercent,
              BudgetDashboardRow::progressStatus)
          .containsExactly(
              // 8 h at the plan-bound 120 EUR/h, 61 min at the personal 150 EUR/h, six monthly
              // flat rates of 100 EUR up to today; only the first adjustment is in force. The time
              // progress counts 115 of 257 working days — the three holidays are not among them.
              tuple(101L, "A", "Order A", LocalDate.of(2026, 6, 15),
                  new BigDecimal("1000"), new BigDecimal("1712.50005000"), 171.25,
                  115.0 * 100 / 257, ProgressStatus.BEHIND),
              // Its only booking is on a suborder that is not invoiceable.
              tuple(102L, "A", "Order A", LocalDate.of(2026, 6, 15),
                  new BigDecimal("2000"), BigDecimal.ZERO, 0.0,
                  115.0 * 100 / 257, ProgressStatus.AHEAD),
              // Ended in May: 8 h at 100 EUR/h plus the unnamed flat rate it alone covers,
              // against the last scope entry up to today.
              tuple(103L, "B", "Order B", LocalDate.of(2026, 5, 31),
                  new BigDecimal("800"), new BigDecimal("1000.00000000"), 125.0,
                  90.0, ProgressStatus.BEHIND),
              // Open-ended and without a budget: revenue, but neither a share nor a status.
              tuple(104L, "B", "Order B", LocalDate.of(2026, 6, 15),
                  BigDecimal.ZERO, new BigDecimal("300.00000000"), 0.0,
                  null, ProgressStatus.UNKNOWN));
    }

    @Test
    public void the_threshold_and_the_overrun_follow_from_the_figures() {
      var rows = figures.computeDashboard(null, null);

      assertThat(rows).extracting(BudgetDashboardRow::isAboveThreshold, BudgetDashboardRow::isOverBudget,
              BudgetDashboardRow::isBehindPlan)
          .containsExactly(
              tuple(true, true, true),
              tuple(false, false, false),
              tuple(false, true, true),
              tuple(false, false, false));
    }

    /**
     * What the user may not see is not only missing from the page, it is never asked for: neither
     * its order nor its plan reaches any of the collaborators the figures are computed from.
     */
    @Test
    public void an_order_the_user_may_not_see_goes_into_no_figure() {
      var rows = figures.computeDashboard(null, null);

      assertThat(rows).extracting(BudgetDashboardRow::customerorderSign).doesNotContain("C");
      assertThat(asked).isNotEmpty().doesNotContain("C", 3L, 105L);
    }

    /** The dashboard asks {@code OrderBudgetService} for the visible plans; C is not among them. */
    private OrderBudgetService visiblePlans() {
      var orderBudgetService = mock(OrderBudgetService.class);
      when(orderBudgetService.getAllActiveVisible(any())).thenAnswer(i -> plans.stream()
          .filter(p -> p.getCustomerorderId() != orderIdOf("C"))
          .toList());
      return orderBudgetService;
    }

    private BudgetControllingService controllingService() {
      var customerorderService = mock(CustomerorderService.class);
      var suborderService = mock(SuborderService.class);
      var timereportService = mock(TimereportService.class);
      var orderBudgetRepository = mock(OrderBudgetRepository.class);
      var assignmentRepository = mock(TimereportBudgetAssignmentRepository.class);
      var orderPricingService = mock(OrderPricingService.class);
      var orderFlatRateService = mock(OrderFlatRateService.class);
      var publicholidayService = mock(PublicholidayService.class);

      when(customerorderService.getCustomerordersByIds(any())).thenAnswer(i -> {
        Collection<Long> ids = askAll(i.getArgument(0));
        return orders.stream().filter(o -> ids.contains(o.getId())).toList();
      });
      // Like the real method: hidden suborders included.
      when(suborderService.getSubordersByCustomerorderIds(any())).thenAnswer(i -> {
        Collection<Long> ids = askAll(i.getArgument(0));
        return suborders.stream().filter(so -> ids.contains(so.getCustomerorder().getId())).toList();
      });
      when(suborderService.getSuborderById(anyLong())).thenAnswer(i ->
          suborders.stream().filter(so -> so.getId().equals(i.<Long>getArgument(0))).findFirst().orElse(null));
      when(orderBudgetRepository.findByCustomerorderIdInAndActive(any(), any())).thenAnswer(i -> {
        Collection<Long> ids = askAll(i.getArgument(0));
        return plans.stream()
            .filter(p -> ids.contains(p.getCustomerorderId()) && p.getActive().equals(i.getArgument(1)))
            .toList();
      });
      when(orderBudgetRepository.findWithScopeEntriesByIdIn(any())).thenAnswer(i -> {
        Collection<Long> ids = askAll(i.getArgument(0));
        return plans.stream().filter(p -> ids.contains(p.getId())).toList();
      });
      // Like the real query: the assigned bookings of the plans up to the day, without a lower bound.
      when(assignmentRepository.findPlanBookings(any(), any())).thenAnswer(i -> {
        Collection<Long> ids = askAll(i.getArgument(0));
        LocalDate until = i.getArgument(1);
        return bookings.stream()
            .filter(bk -> bk.planId() != null && ids.contains(bk.planId()))
            .filter(bk -> !bk.report().getReferenceday().isAfter(until))
            .map(bk -> new PlanBooking(bk.planId(), bk.report().getSuborderId(), bk.report().getEmployeeId(),
                bk.report().getReferenceday(), bk.report().getDuration()))
            .toList();
      });
      when(orderPricingService.lookupFor(any())).thenAnswer(i -> {
        Collection<Long> ids = askAll(i.getArgument(0));
        return OrderPricingLookup.of(rates.stream().filter(r -> ids.contains(r.getCustomerorderId())).toList());
      });
      when(orderFlatRateService.lookupFor(any())).thenAnswer(i -> {
        Collection<Long> ids = askAll(i.getArgument(0));
        return OrderFlatRateLookup.of(flatRates.stream().filter(r -> ids.contains(r.getCustomerorderId())).toList());
      });
      when(publicholidayService.getPublicHolidayDatesBetween(any(), any())).thenAnswer(i -> {
        LocalDate from = i.getArgument(0);
        LocalDate until = i.getArgument(1);
        return holidays.stream()
            .map(Publicholiday::getRefdate)
            .filter(day -> !day.isBefore(from) && !day.isAfter(until))
            .collect(Collectors.toSet());
      });

      var budgetAuthorization = mock(BudgetAuthorization.class);
      when(budgetAuthorization.isAuthorizedForCustomerorder(anyString())).thenReturn(true);
      return new BudgetControllingService(customerorderService, suborderService, timereportService,
          orderBudgetRepository, assignmentRepository, orderPricingService, orderFlatRateService,
          mock(EmployeeCostService.class), publicholidayService, budgetAuthorization,
          new OrderPositions(suborderService));
    }

    private <T> T ask(T value) {
      asked.add(value);
      return value;
    }

    private <T> Collection<T> askAll(Collection<T> values) {
      asked.addAll(values);
      return values;
    }

    private Customerorder order(long id, String sign) {
      var order = new Customerorder();
      setId(order, id);
      order.setSign(sign);
      order.setShortdescription("Order " + sign);
      orders.add(order);
      return order;
    }

    private Suborder suborder(long id, Customerorder order, String sign, Suborder parent, char invoice,
                              boolean hide) {
      var suborder = new Suborder();
      setId(suborder, id);
      suborder.setCustomerorder(order);
      suborder.setParentorder(parent);
      suborder.setSign(sign);
      suborder.setShortdescription(sign);
      suborder.setInvoice(invoice);
      suborder.setHide(hide);
      suborder.deriveCompleteOrderSign();
      suborders.add(suborder);
      return suborder;
    }

    private OrderBudget plan(long id, String orderSign, String suborderSign, String from, String until,
                             ProgressMode progressMode) {
      var plan = new OrderBudget();
      setId(plan, id);
      plan.setName("plan " + id);
      // by id, as the services store it (#1205)
      plan.setCustomerorder(customerorderWithId(orderIdOf(orderSign)));
      plan.setSuborder(suborderWithId(suborderSign == null ? null : suborders.stream()
          .filter(so -> so.getCompleteOrderSign().equals(suborderSign)).findFirst().orElseThrow().getId()));
      plan.setActive(true);
      plan.setValidFrom(LocalDate.parse(from));
      plan.setValidUntil(LocalDate.parse(until));
      plan.setProgressMode(progressMode);
      plans.add(plan);
      return plan;
    }

    private Long orderIdOf(String orderSign) {
      return orders.stream().filter(o -> o.getSign().equals(orderSign)).findFirst().orElseThrow().getId();
    }

    private static void adjust(OrderBudget plan, String amount, String effective) {
      var adjustment = new OrderBudgetAdjustment();
      adjustment.setOrderBudget(plan);
      adjustment.setAmount(new BigDecimal(amount));
      adjustment.setEffective(LocalDate.parse(effective));
      plan.getAdjustments().add(adjustment);
    }

    private static void scope(OrderBudget plan, String refdate, int percent) {
      var entry = new OrderBudgetScopeEntry();
      entry.setOrderBudget(plan);
      entry.setRefdate(LocalDate.parse(refdate));
      entry.setPercent(percent);
      plan.getScopeEntries().add(entry);
    }

    private void book(Suborder suborder, String day, int hours, int minutes, long employeeId, OrderBudget plan) {
      var report = TimereportDTO.builder()
          .id(nextId++)
          .customerorderId(suborder.getCustomerorder().getId())
          .customerorderSign(suborder.getCustomerorder().getSign())
          .completeOrderSign(suborder.getCompleteOrderSign())
          .suborderId(suborder.getId())
          .employeeId(employeeId)
          .referenceday(LocalDate.parse(day))
          .durationhours(hours)
          .durationminutes(minutes)
          .duration(Duration.ofHours(hours).plusMinutes(minutes))
          .build();
      bookings.add(new Booking(report, plan == null ? null : plan.getId()));
    }

    private OrderPricing rate(String orderSign, String suborderSign, int centsPerHour) {
      var rate = new OrderPricing();
      rate.setCustomerorder(customerorderWithId(orderIdOf(orderSign)));
      rate.setSuborderSign(suborderSign);
      rate.setPriceCentsPerHour(centsPerHour);
      rate.setValidFrom(LocalDate.of(2026, 1, 1));
      rate.setValidUntil(LocalDate.of(2026, 12, 31));
      return rate;
    }

    private OrderFlatRate flatRate(String orderSign, FlatRateRhythm rhythm, String from, String until,
                                   String amount) {
      var rate = new OrderFlatRate();
      setId(rate, nextId++);
      rate.setCustomerorder(customerorderWithId(orderIdOf(orderSign)));
      rate.setDescription(rhythm + " " + orderSign);
      rate.setRhythm(rhythm);
      rate.setAmount(new BigDecimal(amount));
      rate.setValidFrom(LocalDate.parse(from));
      rate.setValidUntil(LocalDate.parse(until));
      return rate;
    }
  }

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
