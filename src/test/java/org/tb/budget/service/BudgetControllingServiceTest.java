package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.BudgetControllingResult;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.BudgetControllingSection;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.domain.SectionKind;
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
 * The controlling reports one section per budget period plus one for the time no plan covers, so
 * within a section the period is the coverage (#905). Bookings happen on suborders of any depth
 * while plans only live on the customer order or on the first suborder level.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetControllingServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);

  private static final LocalDate IN_H1 = LocalDate.of(2026, 3, 10);
  private static final LocalDate IN_H2 = LocalDate.of(2026, 9, 10);

  private OrderBudgetRepository orderBudgetRepository;
  private TimereportService timereportService;
  private SuborderService suborderService;
  private BudgetControllingService service;
  private Customerorder customerorder;

  @BeforeEach
  public void setUp() {
    var customerorderService = mock(CustomerorderService.class);
    suborderService = mock(SuborderService.class);
    timereportService = mock(TimereportService.class);
    orderBudgetRepository = mock(OrderBudgetRepository.class);
    var orderPricingService = mock(OrderPricingService.class);
    var employeeCostService = mock(EmployeeCostService.class);
    var publicholidayService = mock(PublicholidayService.class);

    customerorder = mock(Customerorder.class);
    when(customerorder.getId()).thenReturn(1L);
    when(customerorder.getSign()).thenReturn("co");
    when(customerorder.getShortdescription()).thenReturn("order");

    // co/01 and co/02 are first level; co/01/D hangs below co/01 and is where the work is booked.
    var first = suborder("co/01", 'Y', 10L, null);
    var deep = suborder("co/01/D", 'Y', 11L, first);
    var second = suborder("co/02", 'Y', 20L, null);

    when(customerorderService.getCustomerorderBySign("co")).thenReturn(customerorder);
    when(suborderService.getSubordersByCustomerorderId(anyLong())).thenReturn(List.of(first, deep, second));
    when(publicholidayService.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());
    when(orderBudgetRepository.findByCustomerorderSign("co")).thenReturn(List.of());
    givenReports(eightHoursOn(11L, IN_H1), eightHoursOn(20L, IN_H2));
    // One order-wide rate of 100 EUR/h — 8 h are worth 800 EUR wherever they are booked.
    when(orderPricingService.lookupFor(any())).thenReturn(OrderPricingLookup.of(List.of(orderWideRate())));

    // These tests are about the evaluation, so authorization lets every order through.
    var budgetAuthorization = mock(BudgetAuthorization.class);
    when(budgetAuthorization.isAuthorizedForCustomerorder(anyString())).thenReturn(true);

    service = new BudgetControllingService(customerorderService, suborderService, timereportService,
        orderBudgetRepository, orderPricingService, employeeCostService, publicholidayService,
        budgetAuthorization);
  }

  /**
   * The guard of the whole design: sections partition the work, so nothing may be counted twice and
   * nothing may fall out. Exactly what went wrong in #903 before the rules existed.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_every_booking_exactly_once_across_all_sections() {
    givenBudgets(plan("H1", null, FROM, JUN, "1000"), plan("H2 co/01", "co/01", JUL, UNTIL, "500"));

    assertThat(revenueOverAllSections()).isEqualByComparingTo("1600.00");
    assertThat(hoursOverAllSections()).isEqualTo(Duration.ofHours(16));
  }

  /** An order may switch budgeting level over time; nothing must disappear at the boundary. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_split_a_level_switch_into_separate_sections() {
    givenBudgets(plan("H1", null, FROM, JUN, "1000"), plan("H2 co/01", "co/01", JUL, UNTIL, "500"));

    assertThat(compute().sections()).extracting(BudgetControllingSection::kind)
        .containsExactly(SectionKind.ORDER_LEVEL, SectionKind.SUBORDER_LEVEL, SectionKind.UNPLANNED);
  }

  /** Plans live on the first level, bookings live below it — they still have to meet. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_a_booking_on_a_deep_suborder_against_the_plan_of_its_first_level_ancestor() {
    givenBudgets(plan("co/01", "co/01", FROM, UNTIL, "1000"));

    var section = sectionOf(SectionKind.SUBORDER_LEVEL);

    assertThat(section.rows()).extracting(BudgetControllingRow::sign).containsExactly("co/01/D");
    assertThat(section.groups().get(0).subtotal().revenueEuro()).isEqualByComparingTo("800.00");
    assertThat(section.groups().get(0).subtotal().budgetEuro()).isEqualByComparingTo("1000");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_put_plans_of_the_same_period_into_one_section_with_a_subtotal_each() {
    givenBudgets(plan("A", "co/01", FROM, UNTIL, "1000"), plan("B", "co/02", FROM, UNTIL, "500"));

    var section = sectionOf(SectionKind.SUBORDER_LEVEL);

    assertThat(section.groups()).hasSize(2);
    assertThat(section.groups()).allMatch(g -> g.subtotal() != null);
    assertThat(section.total().budgetEuro()).isEqualByComparingTo("1500");
    assertThat(section.total().revenueEuro()).isEqualByComparingTo("1600.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_split_plans_of_different_periods_into_separate_sections() {
    givenBudgets(plan("A", "co/01", FROM, JUN, "1000"), plan("B", "co/02", JUL, UNTIL, "500"));

    assertThat(compute().sections()).filteredOn(s -> s.kind() == SectionKind.SUBORDER_LEVEL).hasSize(2);
  }

  /** An order-wide plan is the whole section, so its budget belongs on the total, not on a subtotal. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_put_an_order_wide_budget_on_the_section_total() {
    givenBudgets(plan("whole year", null, FROM, UNTIL, "2000"));

    var section = sectionOf(SectionKind.ORDER_LEVEL);

    assertThat(section.hasSubtotals()).isFalse();
    assertThat(section.total().budgetEuro()).isEqualByComparingTo("2000");
    assertThat(section.total().revenueEuro()).isEqualByComparingTo("1600.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_the_time_a_plan_does_not_cover_separately() {
    givenBudgets(plan("H1", null, FROM, JUN, "1000"));

    var unplanned = sectionOf(SectionKind.UNPLANNED);

    // The H2 booking on co/02 is the only work outside the plan.
    assertThat(unplanned.rows()).extracting(BudgetControllingRow::sign).containsExactly("co/02");
    assertThat(unplanned.total().revenueEuro()).isEqualByComparingTo("800.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_list_every_gap_of_a_suborder_in_one_row() {
    givenBudgets(plan("mid year", null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 8, 31), "1000"));

    var row = sectionOf(SectionKind.UNPLANNED).rows().stream()
        .filter(r -> "co/02".equals(r.sign())).findFirst().orElseThrow();

    assertThat(row.periodsFormatted()).isEqualTo("01.01.2026 – 28.02.2026, 01.09.2026 – 31.12.2026");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_a_single_unplanned_section_without_any_plan() {
    var sections = compute().sections();

    assertThat(sections).hasSize(1);
    assertThat(sections.get(0).kind()).isEqualTo(SectionKind.UNPLANNED);
    assertThat(sections.get(0).total().revenueEuro()).isEqualByComparingTo("1600.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_leave_out_a_section_without_anything_to_report() {
    // A plan over a period nobody booked in, and with no budget of its own.
    givenBudgets(plan("empty", "co/02", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null));

    assertThat(compute().sections()).noneMatch(s -> s.kind() == SectionKind.SUBORDER_LEVEL);
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_ignore_inactive_plans() {
    var archived = plan("archived", null, FROM, UNTIL, "1000");
    archived.setActive(false);
    givenBudgets(archived);

    assertThat(compute().sections()).extracting(BudgetControllingSection::kind)
        .containsExactly(SectionKind.UNPLANNED);
  }

  /** Legacy data may still overlap; a booking must land in exactly one section regardless. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_not_count_twice_when_legacy_plans_overlap() {
    givenBudgets(plan("A", null, FROM, UNTIL, "1000"), plan("B", null, FROM, UNTIL, "500"));

    assertThat(revenueOverAllSections()).isEqualByComparingTo("1600.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_the_amount_a_budget_was_exceeded_by() {
    givenBudgets(plan("small", null, FROM, UNTIL, "1000"));

    assertThat(sectionOf(SectionKind.ORDER_LEVEL).total().overrunEuro()).isEqualByComparingTo("600.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_earn_no_revenue_on_a_suborder_that_is_not_invoiceable() {
    var unbilled = suborder("co/03", 'N', 30L, null);
    when(suborderService.getSubordersByCustomerorderId(anyLong())).thenReturn(List.of(unbilled));
    givenReports(eightHoursOn(30L, IN_H1));

    var section = sectionOf(SectionKind.UNPLANNED);

    assertThat(section.total().bookedHours()).isEqualTo(Duration.ofHours(8));
    assertThat(section.total().revenueEuro()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // --- helpers ---------------------------------------------------------------------------------

  private BudgetControllingResult compute() {
    return service.compute("co", FROM, UNTIL, false);
  }

  private BudgetControllingSection sectionOf(SectionKind kind) {
    return compute().sections().stream().filter(s -> s.kind() == kind).findFirst().orElseThrow();
  }

  private BigDecimal revenueOverAllSections() {
    return compute().sections().stream().map(s -> s.total().revenueEuro())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Duration hoursOverAllSections() {
    return compute().sections().stream().map(s -> s.total().bookedHours())
        .reduce(Duration.ZERO, Duration::plus);
  }

  private void givenBudgets(OrderBudget... budgets) {
    when(orderBudgetRepository.findByCustomerorderSign("co")).thenReturn(List.of(budgets));
  }

  private void givenReports(TimereportDTO... reports) {
    when(timereportService.getTimereportsByDatesAndCustomerOrderId(any(), any(), anyLong()))
        .thenReturn(List.of(reports));
  }

  private Suborder suborder(String completeSign, char invoice, long id, Suborder parent) {
    var suborder = mock(Suborder.class);
    when(suborder.getId()).thenReturn(id);
    when(suborder.getCustomerorder()).thenReturn(customerorder);
    when(suborder.getCompleteOrderSign()).thenReturn(completeSign);
    when(suborder.getShortdescription()).thenReturn(completeSign);
    when(suborder.isInvoiceable()).thenReturn(invoice == 'Y');
    // Root first: the first entry is the first level ancestor a budget plan may refer to.
    when(suborder.withParents()).thenReturn(parent == null ? List.of(suborder) : List.of(parent, suborder));
    return suborder;
  }

  private static TimereportDTO eightHoursOn(long suborderId, LocalDate day) {
    return TimereportDTO.builder()
        .suborderId(suborderId)
        .employeeSign("emp")
        .referenceday(day)
        .duration(Duration.ofHours(8))
        .build();
  }

  private static OrderBudget plan(String name, String suborderSign, LocalDate from, LocalDate until, String amount) {
    var budget = new OrderBudget();
    budget.setName(name);
    budget.setCustomerorderSign("co");
    budget.setSuborderSign(suborderSign);
    budget.setActive(true);
    budget.setValidFrom(from);
    budget.setValidUntil(until);
    if (amount != null) {
      var adjustment = new OrderBudgetAdjustment();
      adjustment.setOrderBudget(budget);
      adjustment.setAmount(new BigDecimal(amount));
      adjustment.setEffective(from);
      budget.getAdjustments().add(adjustment);
    }
    return budget;
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
