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
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.common.domain.AuditedEntity;
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

  private static long nextId = 1L;

  private BudgetControllingService service;
  private OrderBudgetRepository orderBudgetRepository;

  @BeforeEach
  public void setUp() {
    var customerorderService = mock(CustomerorderService.class);
    var suborderService = mock(SuborderService.class);
    var timereportService = mock(TimereportService.class);
    orderBudgetRepository = mock(OrderBudgetRepository.class);
    var orderPricingService = mock(OrderPricingService.class);
    var employeeCostService = mock(EmployeeCostService.class);
    var publicholidayService = mock(PublicholidayService.class);

    var customerorder = mock(Customerorder.class);
    when(customerorder.getId()).thenReturn(1L);
    when(customerorder.getSign()).thenReturn("co");

    var billed = suborder(customerorder, "01", 'Y', 10L);
    var unbilled = suborder(customerorder, "02", 'N', 20L);
    // No time reports, no budget, no planned hours — nothing to report about it.
    var untouched = suborder(customerorder, "03", 'Y', 30L);

    when(customerorderService.getCustomerorderBySign("co")).thenReturn(customerorder);
    when(suborderService.getSubordersByCustomerorderId(anyLong()))
        .thenReturn(List.of(billed, unbilled, untouched));
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

  /** A suborder with no booked time, no budget and no planned hours is only a row of dashes (#901). */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_leave_out_suborders_without_time_budget_and_planned_hours() {
    var signs = compute().suborderRows().stream().map(BudgetControllingRow::sign).toList();

    assertThat(signs).containsExactly("co/01", "co/02");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_keep_the_order_total_unchanged_by_the_omitted_rows() {
    var total = compute().total();

    assertThat(total.bookedHours()).isEqualTo(Duration.ofHours(16));
    assertThat(total.revenueEuro()).isEqualByComparingTo("800.00");
  }

  /**
   * Two active order-level plans whose validity overlaps must not make the same booking count
   * twice — that inflated an order's utilization to 145.9 % where it was really 85.6 % (#903).
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_a_booking_once_even_when_two_plans_cover_it() {
    givenBudgets(orderLevelPlan(FROM, LocalDate.of(2999, 12, 31)), orderLevelPlan(FROM, UNTIL));

    assertThat(row("co/01").coveredRevenueEuro()).isEqualByComparingTo("800.00");
    assertThat(compute().total().coveredRevenueEuro()).isEqualByComparingTo("800.00");
  }

  /**
   * A suborder plan takes the suborder out of the order-level coverage only for the periods it
   * actually covers. Otherwise its revenue is covered by nothing and disappears from every row.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_let_the_order_level_plan_cover_what_an_own_plan_does_not() {
    var ownPlanElsewhere = suborderPlan("co/01", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
    givenBudgets(orderLevelPlan(FROM, UNTIL), ownPlanElsewhere);

    assertThat(row("co/01").coveredRevenueEuro()).isEqualByComparingTo("800.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_a_booking_once_when_its_own_plan_and_the_order_level_plan_both_cover_it() {
    givenBudgets(orderLevelPlan(FROM, UNTIL), suborderPlan("co/01", FROM, UNTIL));

    assertThat(row("co/01").coveredRevenueEuro()).isEqualByComparingTo("800.00");
  }

  /**
   * Dashboard side. Revenue of a period that a suborder's own plan covers belongs to that plan's
   * row, so the order-level row must not report it as well.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_not_report_revenue_on_the_order_level_plan_that_an_own_plan_already_covers() {
    var orderLevel = orderLevelPlan(FROM, UNTIL);
    var ownPlan = suborderPlan("co/01", FROM, UNTIL);
    when(orderBudgetRepository.findAllActiveWithAdjustments()).thenReturn(List.of(orderLevel, ownPlan));

    var utilizations = service.computeUtilizationInfos(List.of(orderLevel, ownPlan));

    assertThat(utilizations.get(orderLevel.getId()).info().coveredRevenueEuro())
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(utilizations.get(ownPlan.getId()).info().coveredRevenueEuro())
        .isEqualByComparingTo("800.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_revenue_on_the_order_level_plan_outside_an_own_plans_period() {
    var orderLevel = orderLevelPlan(FROM, UNTIL);
    var ownPlanElsewhere = suborderPlan("co/01", LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));

    var utilizations = service.computeUtilizationInfos(List.of(orderLevel, ownPlanElsewhere));

    assertThat(utilizations.get(orderLevel.getId()).info().coveredRevenueEuro())
        .isEqualByComparingTo("800.00");
  }

  /**
   * Revenue that only the order-level plan covers belongs in the suborder's revenue, but must not
   * be charged against the suborder's own budget — the two columns have different scopes.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_charge_only_what_its_own_plan_covers_against_the_suborders_budget() {
    // The own plan covers 20.-30.06; the only booking is on 10.06 and thus order-level only.
    givenBudgets(withAmount(orderLevelPlan(FROM, UNTIL), "1000"),
        withAmount(suborderPlan("co/01", LocalDate.of(2026, 6, 20), UNTIL), "500"));

    var row = row("co/01");

    assertThat(row.coveredRevenueEuro()).isEqualByComparingTo("800.00");
    assertThat(row.budgetEuro()).isEqualByComparingTo("500");
    assertThat(row.budgetUsedPercent()).isZero();
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_charge_revenue_against_the_suborders_budget_where_its_own_plan_covers_it() {
    givenBudgets(withAmount(orderLevelPlan(FROM, UNTIL), "1000"),
        withAmount(suborderPlan("co/01", FROM, UNTIL), "1600"));

    assertThat(row("co/01").budgetUsedPercent()).isEqualTo(50.0);
  }

  private static OrderBudget withAmount(OrderBudget budget, String amount) {
    var adjustment = new OrderBudgetAdjustment();
    adjustment.setOrderBudget(budget);
    adjustment.setAmount(new BigDecimal(amount));
    adjustment.setEffective(budget.getValidFrom());
    budget.getAdjustments().add(adjustment);
    return budget;
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

  private void givenBudgets(OrderBudget... budgets) {
    when(orderBudgetRepository.findByCustomerorderSign("co")).thenReturn(List.of(budgets));
  }

  private static OrderBudget orderLevelPlan(LocalDate validFrom, LocalDate validUntil) {
    return plan(null, validFrom, validUntil);
  }

  private static OrderBudget suborderPlan(String suborderSign, LocalDate validFrom, LocalDate validUntil) {
    return plan(suborderSign, validFrom, validUntil);
  }

  /** A plan without adjustments: it carries no budget amount but still defines a coverage window. */
  private static OrderBudget plan(String suborderSign, LocalDate validFrom, LocalDate validUntil) {
    var budget = new OrderBudget();
    budget.setCustomerorderSign("co");
    budget.setSuborderSign(suborderSign);
    budget.setActive(true);
    budget.setValidFrom(validFrom);
    budget.setValidUntil(validUntil);
    // computeUtilizationInfos keys its result by id, so the plans need distinct ones. The entity has
    // no setter because the id is generated; a saved plan always has one.
    setId(budget, nextId++);
    return budget;
  }

  private static void setId(OrderBudget budget, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(budget, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test plan", e);
    }
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
