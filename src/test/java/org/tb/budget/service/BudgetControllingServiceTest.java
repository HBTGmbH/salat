package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.BudgetControllingGroup;
import org.tb.budget.domain.BudgetControllingResult;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.BudgetControllingSection;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.domain.OrderBudgetScopeEntry;
import org.tb.budget.domain.FlatRateRhythm;
import org.tb.budget.domain.OrderFlatRate;
import org.tb.budget.domain.OrderFlatRateInstalment;
import org.tb.budget.domain.OrderFlatRateLookup;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.domain.ProgressMode;
import org.tb.budget.domain.ProgressStatus;
import org.tb.budget.domain.SectionKind;
import org.tb.budget.domain.TimereportBudgetLink;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
import org.tb.common.LocalDateRange;
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
 * The controlling counts a booking against the plan it is <em>assigned</em> to (#913) — nothing is
 * derived from (suborder, date) any more. Bookings without an assignment land in their own section
 * instead of quietly disappearing from every number.
 *
 * <p>Most tests here assign the bookings through the real {@link BudgetResolver}, i.e. exactly as
 * the automatic assignment (#909) and the backfill (#910) do. That is what makes them the
 * equivalence proof the switch needed: for an order without overlapping plans they assert the same
 * hours, revenue and costs as before the switch.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetControllingServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);

  private static final LocalDate APR = LocalDate.of(2026, 4, 1);

  private static final LocalDate IN_H1 = LocalDate.of(2026, 3, 10);
  private static final LocalDate IN_H2 = LocalDate.of(2026, 9, 10);

  private final List<OrderBudget> plans = new ArrayList<>();
  private final List<OrderFlatRate> flatRates = new ArrayList<>();
  private final List<TimereportDTO> reports = new ArrayList<>();
  private final List<TimereportBudgetLink> links = new ArrayList<>();
  private final List<Suborder> suborders = new ArrayList<>();

  private OrderBudgetRepository orderBudgetRepository;
  private TimereportBudgetAssignmentRepository assignmentRepository;
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
    assignmentRepository = mock(TimereportBudgetAssignmentRepository.class);
    var orderPricingService = mock(OrderPricingService.class);
    var orderFlatRateService = mock(OrderFlatRateService.class);
    var employeeCostService = mock(EmployeeCostService.class);
    var publicholidayService = mock(PublicholidayService.class);

    customerorder = mock(Customerorder.class);
    when(customerorder.getId()).thenReturn(1L);
    when(customerorder.getSign()).thenReturn("co");
    when(customerorder.getShortdescription()).thenReturn("order");

    // co/01 and co/02 are first level; co/01/D hangs below co/01 and is where the work is booked.
    var first = suborder("01", 'Y', 10L, null);
    var deep = suborder("D", 'Y', 11L, first);
    var second = suborder("02", 'Y', 20L, null);

    when(customerorderService.getCustomerorderBySign("co")).thenReturn(customerorder);
    when(publicholidayService.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());

    // Plans, bookings and assignments all come out of the mutable fixture lists, so a test can set
    // them up in any order and the last word wins.
    when(orderBudgetRepository.findByCustomerorderSign("co")).thenAnswer(i -> List.copyOf(plans));
    when(orderBudgetRepository.findByCustomerorderSignAndActive(any(), any())).thenAnswer(i ->
        plans.stream().filter(p -> p.getActive().equals(i.getArgument(1))).toList());
    when(assignmentRepository.findLinksByCustomerorderSign("co")).thenAnswer(i -> List.copyOf(links));
    // Narrowed by the requested period, as the real query does — #916 reads before the window.
    when(timereportService.getTimereportsByDatesAndCustomerOrderId(any(), any(), anyLong()))
        .thenAnswer(i -> {
          LocalDate periodFrom = i.getArgument(0);
          LocalDate periodUntil = i.getArgument(1);
          return reports.stream()
              .filter(r -> !r.getReferenceday().isBefore(periodFrom)
                  && !r.getReferenceday().isAfter(periodUntil))
              .toList();
        });
    when(suborderService.getSubordersByCustomerorderId(anyLong())).thenAnswer(i -> List.copyOf(suborders));
    when(suborderService.getSuborderById(anyLong())).thenAnswer(i ->
        suborders.stream().filter(so -> so.getId().equals(i.<Long>getArgument(0))).findFirst().orElse(null));

    givenSuborders(first, deep, second);
    givenReports(eightHoursOn(11L, IN_H1), eightHoursOn(20L, IN_H2));
    // One order-wide rate of 100 EUR/h — 8 h are worth 800 EUR wherever they are booked.
    when(orderPricingService.lookupFor(any())).thenReturn(OrderPricingLookup.of(List.of(orderWideRate())));
    // No flat rates unless a test sets some up; the list is mutable so the last word wins.
    when(orderFlatRateService.lookupFor(any())).thenAnswer(i -> OrderFlatRateLookup.of(List.copyOf(flatRates)));

    // These tests are about the evaluation, so authorization lets every order through.
    var budgetAuthorization = mock(BudgetAuthorization.class);
    when(budgetAuthorization.isAuthorizedForCustomerorder(anyString())).thenReturn(true);

    service = new BudgetControllingService(customerorderService, suborderService, timereportService,
        orderBudgetRepository, assignmentRepository, orderPricingService, orderFlatRateService,
        employeeCostService, publicholidayService, budgetAuthorization);
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

  /** Plans only live on the first level, so the depth of the booking below it must not matter. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_a_booking_three_levels_down_against_the_first_level_plan() {
    var first = suborder("01", 'Y', 10L, null);
    var second = suborder("D", 'Y', 11L, first);
    var third = suborder("E", 'Y', 12L, second);
    givenSuborders(first, second, third);
    givenReports(eightHoursOn(12L, IN_H1));
    givenBudgets(plan("co/01", "co/01", FROM, UNTIL, "1000"));

    assertThat(third.getCompleteOrderSign()).isEqualTo("co/01/D/E");

    var section = sectionOf(SectionKind.SUBORDER_LEVEL);
    assertThat(section.rows()).extracting(BudgetControllingRow::sign).containsExactly("co/01/D/E");
    assertThat(section.groups().get(0).subtotal().revenueEuro()).isEqualByComparingTo("800.00");
  }

  /** A plan on one first level suborder must not absorb work booked under another one. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_not_count_a_booking_under_another_first_level_suborder() {
    givenBudgets(plan("co/01", "co/01", FROM, UNTIL, "1000"));

    // The fixture books 8 h on co/01/D, inside the plan, and 8 h on co/02, outside it.
    assertThat(sectionOf(SectionKind.SUBORDER_LEVEL).rows())
        .extracting(BudgetControllingRow::sign).containsExactly("co/01/D");
    assertThat(sectionOf(SectionKind.UNPLANNED).rows())
        .extracting(BudgetControllingRow::sign).containsExactly("co/02");
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
  public void should_report_the_bookings_no_plan_covers_separately() {
    givenBudgets(plan("H1", null, FROM, JUN, "1000"));

    var unplanned = sectionOf(SectionKind.UNPLANNED);

    // The H2 booking on co/02 is the only work outside the plan.
    assertThat(unplanned.rows()).extracting(BudgetControllingRow::sign).containsExactly("co/02");
    assertThat(unplanned.total().revenueEuro()).isEqualByComparingTo("800.00");
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

  /**
   * Overlapping plans no longer need to be cut against each other: a booking counts against the one
   * plan it is assigned to. Unresolvable on its own, it stays unassigned rather than being guessed
   * into one of them — and is still reported exactly once.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_every_booking_once_when_plans_overlap() {
    givenBudgets(plan("A", null, FROM, UNTIL, "1000"), plan("B", null, FROM, UNTIL, "500"));

    assertThat(revenueOverAllSections()).isEqualByComparingTo("1600.00");
    assertThat(sectionOf(SectionKind.UNPLANNED).total().revenueEuro()).isEqualByComparingTo("1600.00");
  }

  /** With the plan chosen by hand, an overlap is no obstacle at all — the point of #911 and #914. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_follow_a_manual_assignment_when_plans_overlap() {
    var a = plan("A", null, FROM, UNTIL, "1000");
    var b = plan("B", null, FROM, UNTIL, "500");
    givenBudgets(a, b);
    givenAssignment(reports.get(0), a);
    givenAssignment(reports.get(1), b);

    var sections = compute().sections();

    assertThat(sections).noneMatch(section -> section.kind() == SectionKind.UNPLANNED);
    assertThat(revenueOverAllSections()).isEqualByComparingTo("1600.00");
  }

  /**
   * The semantic change of #913: being inside the period and scope of a plan is no longer enough.
   * An unassigned booking counts against no budget and has to show up as such — that is what the
   * automatic assignment (#909) and the backfill (#910) exist to prevent.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_not_count_an_unassigned_booking_against_a_plan_that_would_cover_it() {
    givenBudgets(plan("whole year", null, FROM, UNTIL, "2000"));
    links.clear();

    assertThat(compute().sections()).extracting(BudgetControllingSection::kind)
        .contains(SectionKind.UNPLANNED);
    assertThat(sectionOf(SectionKind.ORDER_LEVEL).total().revenueEuro()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sectionOf(SectionKind.UNPLANNED).total().revenueEuro()).isEqualByComparingTo("1600.00");
  }

  /**
   * A plan can be deactivated after its bookings were assigned. Its hours must not vanish from every
   * number — they belong under "without budget", where the bulk assignment can pick them up.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_bookings_of_a_deactivated_plan_as_without_budget() {
    var archived = plan("archived", null, FROM, UNTIL, "1000");
    givenBudgets(archived);
    // Assigned while the plan was still active, then archived.
    givenAssignment(reports.get(0), archived);
    givenAssignment(reports.get(1), archived);
    archived.setActive(false);

    var sections = compute().sections();

    assertThat(sections).extracting(BudgetControllingSection::kind).containsExactly(SectionKind.UNPLANNED);
    assertThat(sections.get(0).total().revenueEuro()).isEqualByComparingTo("1600.00");
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
    var unbilled = suborder("03", 'N', 30L, null);
    givenSuborders(unbilled);
    givenReports(eightHoursOn(30L, IN_H1));

    var section = sectionOf(SectionKind.UNPLANNED);

    assertThat(section.total().bookedHours()).isEqualTo(Duration.ofHours(8));
    assertThat(section.total().revenueEuro()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // --- budget in full, hours split at the window start (#917) ----------------------------------

  /**
   * The budget is shown in full — every adjustment effective by the end of the window, with nothing
   * deducted. #916 showed the remainder at the window start instead, which nobody could place
   * without knowing the total; the pre-window consumption is a column now.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_the_full_budget_for_a_plan_that_began_before_the_window() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    var section = sectionOf(compute(APR, JUN), SectionKind.ORDER_LEVEL);

    assertThat(section.total().budgetEuro()).isEqualByComparingTo("2000");
  }

  /** The hours split at the window start: what was booked before it, and what inside it. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_split_the_hours_at_the_window_start() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    // The fixture books 8 h in March and 8 h in September.
    var section = sectionOf(compute(JUL, UNTIL), SectionKind.ORDER_LEVEL);

    assertThat(section.total().bookedHoursBeforeWindow()).isEqualTo(Duration.ofHours(8));
    assertThat(section.total().bookedHours()).isEqualTo(Duration.ofHours(8));
  }

  /** The amounts cover both: they are the full figures up to the end of the window. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_the_revenue_of_the_whole_span_up_to_the_window_end() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    var section = sectionOf(compute(JUL, UNTIL), SectionKind.ORDER_LEVEL);

    // 8 h before the window and 8 h inside it, at 100 EUR — both count towards the budget.
    assertThat(section.total().revenueEuro()).isEqualByComparingTo("1600.00");
  }

  /** Nothing beyond the window end, however far the plan runs. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_not_report_anything_booked_after_the_window_end() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    var section = sectionOf(compute(APR, JUN), SectionKind.ORDER_LEVEL);

    // Only the March booking lies before the end of June; September is out of scope.
    assertThat(section.total().bookedHoursBeforeWindow()).isEqualTo(Duration.ofHours(8));
    assertThat(section.total().bookedHours()).isEqualTo(Duration.ZERO);
    assertThat(section.total().revenueEuro()).isEqualByComparingTo("800.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_an_adjustment_from_before_the_window_and_one_inside_it() {
    var year = plan("year", null, FROM, UNTIL, "1000");
    addAdjustment(year, "500", LocalDate.of(2026, 5, 1));
    givenBudgets(year);

    var section = sectionOf(compute(APR, JUN), SectionKind.ORDER_LEVEL);

    assertThat(section.total().budgetEuro()).isEqualByComparingTo("1500");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_not_count_an_adjustment_that_takes_effect_after_the_window() {
    var year = plan("year", null, FROM, UNTIL, "1000");
    addAdjustment(year, "500", LocalDate.of(2026, 9, 1));
    givenBudgets(year);

    var section = sectionOf(compute(APR, JUN), SectionKind.ORDER_LEVEL);

    assertThat(section.total().budgetEuro()).isEqualByComparingTo("1000");
  }

  /**
   * The point of the change: the utilization is measured against the whole budget. Against the
   * remainder at the window start it read two thirds for the same data, which invited the reader to
   * think the plan was in worse shape than it is.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_measure_the_utilization_against_the_full_budget() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    var section = sectionOf(compute(JUL, UNTIL), SectionKind.ORDER_LEVEL);

    assertThat(section.total().budgetEuro()).isEqualByComparingTo("2000");
    assertThat(section.total().revenueEuro()).isEqualByComparingTo("1600.00");
    assertThat(section.total().budgetUsedPercent()).isCloseTo(80.0, within(0.01));
  }

  /** Over the budget is still over the budget — measured against the full amount. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_an_overrun_against_the_full_budget() {
    givenBudgets(plan("small", null, FROM, UNTIL, "500"));

    var section = sectionOf(compute(APR, JUN), SectionKind.ORDER_LEVEL);

    // 800 EUR booked before the window against a budget of 500.
    assertThat(section.total().budgetEuro()).isEqualByComparingTo("500");
    assertThat(section.total().overrunEuro()).isEqualByComparingTo("300.00");
  }

  /** Both parts come from one read, not one for the window and another for what precedes it. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_read_the_bookings_of_the_whole_span_in_one_query() {
    givenBudgets(plan("a", "co/01", FROM, UNTIL, "1000"), plan("b", "co/02", FROM, UNTIL, "1000"));

    compute(APR, JUN);

    verify(timereportService, times(1))
        .getTimereportsByDatesAndCustomerOrderId(any(), any(), anyLong());
  }

  /** The read starts at the plan, not at the window — otherwise the earlier hours are invisible. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_read_from_the_earliest_plan_start() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    compute(APR, JUN);

    verify(timereportService).getTimereportsByDatesAndCustomerOrderId(FROM, JUN, 1L);
  }

  /** A window that starts with the plan has nothing before it, so the read starts at the window. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_read_from_the_window_when_the_plan_starts_with_it() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    compute(FROM, UNTIL);

    verify(timereportService).getTimereportsByDatesAndCustomerOrderId(FROM, UNTIL, 1L);
  }

  // --- utilization: dashboard (#778) and alerts -----------------------------------------------

  /**
   * Dashboard and alerts read the same assignment as the sections do, so their number cannot drift
   * away from the evaluation — which it could while both derived the scope on their own.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_base_the_utilization_on_the_assignment() {
    var whole = plan("whole year", null, FROM, UNTIL, "2000");
    givenBudgets(whole);

    var info = service.computeUtilizationInfo(whole);

    assertThat(info.budgetEuro()).isEqualByComparingTo("2000");
    // Only the March booking; the one in September has not happened yet (#972).
    assertThat(info.coveredRevenueEuro()).isEqualByComparingTo("800.00");
    assertThat(info.percent()).isEqualTo(40.0);
  }

  // --- the utilization window ends today (#972) --------------------------------------------------

  /**
   * "Where does this plan stand" is a question about the present, so the window ends today rather
   * than at the plan's own end. Reading it to the end counted what has not happened yet.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_measure_the_utilization_up_to_today_rather_than_to_the_plan_end() {
    var whole = plan("whole year", null, FROM, UNTIL, "2000");
    givenBudgets(whole);

    // The fixture books 8 h in March and 8 h in September; today is the 15th of June.
    assertThat(service.computeUtilizationInfo(whole).coveredRevenueEuro()).isEqualByComparingTo("800.00");
  }

  /** A plan that has already ended is read to its own end, not to today. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_measure_a_finished_plan_up_to_its_own_end() {
    var firstQuarter = plan("Q1", null, FROM, LocalDate.of(2026, 3, 31), "2000");
    givenBudgets(firstQuarter);

    assertThat(service.computeUtilizationInfo(firstQuarter).coveredRevenueEuro())
        .isEqualByComparingTo("800.00");
  }

  /**
   * The case that made the old window plain: a monthly retainer running to December contributed all
   * twelve months in June, and the plan looked used up while it was on track.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_only_the_flat_rate_amounts_due_by_today() {
    var whole = plan("whole year", null, FROM, UNTIL, "2000");
    givenBudgets(whole);
    givenFlatRates(monthly("retainer", null, FROM, UNTIL, "100"));

    // Six monthly amounts are due by the 15th of June, not twelve — plus the March booking.
    assertThat(service.computeUtilizationInfo(whole).coveredRevenueEuro()).isEqualByComparingTo("1400.00");
  }

  /**
   * The cut applies to the budget as well. An adjustment taking effect in November has not been
   * granted yet, and counting it today would understate the utilization.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_only_the_budget_granted_by_today() {
    var whole = plan("whole year", null, FROM, UNTIL, "1000");
    addAdjustment(whole, "500", LocalDate.of(2026, 11, 1));
    givenBudgets(whole);

    assertThat(service.computeUtilizationInfo(whole).budgetEuro()).isEqualByComparingTo("1000");
  }

  /** A plan that only starts next month has nothing behind it yet — neither budget nor revenue. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_nothing_for_a_plan_that_only_starts_in_the_future() {
    var later = plan("H2", null, JUL, UNTIL, "2000");
    givenBudgets(later);
    givenFlatRates(once("initial fee", null, LocalDate.of(2026, 8, 1), "500"));

    var info = service.computeUtilizationInfo(later);

    assertThat(info.budgetEuro()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(info.coveredRevenueEuro()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_leave_unassigned_bookings_out_of_the_utilization() {
    var whole = plan("whole year", null, FROM, UNTIL, "2000");
    givenBudgets(whole);
    links.clear();

    assertThat(service.computeUtilizationInfo(whole).coveredRevenueEuro())
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  /** A plan on a first level suborder counts what is booked below it, because the assignment says so. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_deep_bookings_in_the_utilization_of_a_first_level_plan() {
    var firstLevel = plan("co/01", "co/01", FROM, UNTIL, "1000");
    givenBudgets(firstLevel);

    // The fixture books 8 h on co/01/D, below the plan, and 8 h on co/02, outside it.
    assertThat(service.computeUtilizationInfo(firstLevel).coveredRevenueEuro())
        .isEqualByComparingTo("800.00");
  }

  // --- flat rates (#972) -----------------------------------------------------------------------

  /**
   * The reason the feature exists: an order can earn without anybody booking. A plan with no
   * bookings at all still has to report the flat rate falling due inside it.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_a_flat_rate_against_the_plan_covering_its_due_date() {
    givenBudgets(plan("year", null, FROM, UNTIL, "5000"));
    givenFlatRates(once("initial fee", null, IN_H1, "1000"));

    var section = sectionOf(SectionKind.ORDER_LEVEL);

    assertThat(section.total().flatRateRevenueEuro()).isEqualByComparingTo("1000");
    // 16 h at 100 EUR plus the flat rate.
    assertThat(section.total().totalRevenueEuro()).isEqualByComparingTo("2600.00");
  }

  /** A flat rate and hourly work on the same order add up; neither replaces the other. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_measure_the_budget_against_hourly_and_flat_rate_revenue_together() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2600"));
    givenFlatRates(once("initial fee", null, IN_H1, "1000"));

    assertThat(sectionOf(SectionKind.ORDER_LEVEL).total().budgetUsedPercent()).isCloseTo(100.0, within(0.01));
  }

  /** Several definitions on one order are the normal case and must not replace each other. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_add_up_several_flat_rates_of_one_order() {
    givenBudgets(plan("year", null, FROM, UNTIL, "5000"));
    givenFlatRates(once("initial fee", null, IN_H1, "1000"),
        monthly("retainer", null, FROM, LocalDate.of(2026, 3, 31), "100"));

    // Three monthly amounts on 01.01, 01.02 and 01.03 plus the one-off fee.
    assertThat(sectionOf(SectionKind.ORDER_LEVEL).total().flatRateRevenueEuro()).isEqualByComparingTo("1300");
  }

  /** A monthly flat rate is one record; every month of its validity is due on its own. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_one_line_per_flat_rate_rather_than_per_due_date() {
    givenBudgets(plan("year", null, FROM, UNTIL, "5000"));
    givenFlatRates(monthly("retainer", null, FROM, UNTIL, "100"));

    var flatRateRows = sectionOf(SectionKind.ORDER_LEVEL).rows().stream()
        .filter(BudgetControllingRow::flatRate).toList();

    assertThat(flatRateRows).hasSize(1);
    assertThat(flatRateRows.get(0).label()).isEqualTo("retainer");
    assertThat(flatRateRows.get(0).flatRateRevenueEuro()).isEqualByComparingTo("1200");
  }

  /**
   * The point of allocating a due amount rather than a definition: a monthly rate spanning two
   * plans has each of its months counted against the plan it falls into.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_split_a_monthly_flat_rate_across_the_plans_its_months_fall_into() {
    givenBudgets(plan("H1", null, FROM, JUN, "1000"), plan("H2", null, JUL, UNTIL, "1000"));
    givenFlatRates(monthly("retainer", null, FROM, UNTIL, "100"));

    // Six months in each half of the year.
    assertThat(sectionOf(SectionKind.ORDER_LEVEL).total().flatRateRevenueEuro()).isEqualByComparingTo("600");
    assertThat(compute().sections()).filteredOn(s -> s.kind() == SectionKind.ORDER_LEVEL).hasSize(2);
    // 16 h at 100 EUR plus twelve monthly amounts of 100 EUR, each counted exactly once.
    assertThat(totalRevenueOverAllSections()).isEqualByComparingTo("2800.00");
  }

  /** A line carrying nothing but a flat rate has something to report and must not be filtered out. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_a_flat_rate_on_an_order_nobody_booked_on() {
    reports.clear();
    givenBudgets(plan("year", null, FROM, UNTIL, "5000"));
    givenFlatRates(once("initial fee", null, IN_H1, "1000"));

    var section = sectionOf(SectionKind.ORDER_LEVEL);

    assertThat(section.total().bookedHours()).isEqualTo(Duration.ZERO);
    assertThat(section.rows()).extracting(BudgetControllingRow::label).contains("initial fee");
    assertThat(section.total().totalRevenueEuro()).isEqualByComparingTo("1000");
  }

  /** A flat rate on a suborder belongs to the plan of that suborder, not to another one. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_a_suborder_flat_rate_against_the_plan_of_that_suborder() {
    givenBudgets(plan("A", "co/01", FROM, UNTIL, "1000"), plan("B", "co/02", FROM, UNTIL, "1000"));
    givenFlatRates(once("milestone", "co/01", IN_H1, "500"));

    var groups = sectionOf(SectionKind.SUBORDER_LEVEL).groups();

    assertThat(groups).filteredOn(g -> "co/01".equals(g.sign()))
        .allSatisfy(g -> assertThat(g.subtotal().flatRateRevenueEuro()).isEqualByComparingTo("500"));
    assertThat(groups).filteredOn(g -> "co/02".equals(g.sign()))
        .allSatisfy(g -> assertThat(g.subtotal().flatRateRevenueEuro()).isEqualByComparingTo(BigDecimal.ZERO));
  }

  /** A flat rate deep below a first level suborder still meets the plan living on that level. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_a_flat_rate_on_a_deep_suborder_against_its_first_level_plan() {
    givenBudgets(plan("co/01", "co/01", FROM, UNTIL, "1000"));
    givenFlatRates(once("milestone", "co/01/D", IN_H1, "500"));

    assertThat(sectionOf(SectionKind.SUBORDER_LEVEL).groups().get(0).subtotal().flatRateRevenueEuro())
        .isEqualByComparingTo("500");
  }

  /** An order-wide flat rate is not the business of a plan that only covers one suborder. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_not_count_an_order_wide_flat_rate_against_a_suborder_plan() {
    givenBudgets(plan("co/01", "co/01", FROM, UNTIL, "1000"));
    givenFlatRates(once("initial fee", null, IN_H1, "500"));

    assertThat(sectionOf(SectionKind.SUBORDER_LEVEL).total().flatRateRevenueEuro())
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(sectionOf(SectionKind.UNPLANNED).total().flatRateRevenueEuro()).isEqualByComparingTo("500");
  }

  /**
   * Where two plans could hold the amount, neither does: counting it twice would report revenue
   * that does not exist, and picking one would count it against a plan nobody chose.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_a_flat_rate_once_when_plans_overlap() {
    givenBudgets(plan("A", null, FROM, UNTIL, "1000"), plan("B", null, FROM, UNTIL, "1000"));
    givenFlatRates(once("initial fee", null, IN_H1, "500"));

    var flatRateOverAllSections = compute().sections().stream()
        .map(s -> s.total().flatRateRevenueEuro()).reduce(BigDecimal.ZERO, BigDecimal::add);

    assertThat(flatRateOverAllSections).isEqualByComparingTo("500");
    assertThat(sectionOf(SectionKind.UNPLANNED).total().flatRateRevenueEuro()).isEqualByComparingTo("500");
  }

  /** No plan at all is the same case as an ambiguous one: the amount is due and has to be visible. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_a_flat_rate_no_plan_covers_as_without_budget() {
    givenBudgets(plan("H1", null, FROM, JUN, "1000"));
    givenFlatRates(once("late fee", null, IN_H2, "500"));

    assertThat(sectionOf(SectionKind.UNPLANNED).total().flatRateRevenueEuro()).isEqualByComparingTo("500");
  }

  /** An inactive plan holds nothing, so its flat rates surface instead of disappearing. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_the_flat_rates_of_a_deactivated_plan_as_without_budget() {
    var archived = plan("archived", null, FROM, UNTIL, "1000");
    archived.setActive(false);
    givenBudgets(archived);
    givenFlatRates(once("initial fee", null, IN_H1, "500"));

    assertThat(sectionOf(SectionKind.UNPLANNED).total().flatRateRevenueEuro()).isEqualByComparingTo("500");
  }

  /** Nothing outside the window, whatever the definition runs to. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_not_report_a_flat_rate_falling_due_after_the_window() {
    givenBudgets(plan("year", null, FROM, UNTIL, "5000"));
    givenFlatRates(once("late fee", null, IN_H2, "500"));

    assertThat(sectionOf(compute(FROM, JUN), SectionKind.ORDER_LEVEL).total().flatRateRevenueEuro())
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  /**
   * The amounts are the full figures over the span the evaluation talks about (#917), so a flat rate
   * from before the window counts towards the budget just as the earlier hours do.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_a_flat_rate_from_before_the_window() {
    givenBudgets(plan("year", null, FROM, UNTIL, "5000"));
    givenFlatRates(once("initial fee", null, IN_H1, "500"));

    assertThat(sectionOf(compute(JUL, UNTIL), SectionKind.ORDER_LEVEL).total().flatRateRevenueEuro())
        .isEqualByComparingTo("500");
  }

  /** Instalments are entered per date and are the case a fixed price order is paid in. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_the_instalments_of_a_fixed_price_flat_rate() {
    givenBudgets(plan("year", null, FROM, UNTIL, "5000"));
    givenFlatRates(instalments("fixed price", null, FROM, UNTIL,
        instalment(IN_H1, "1000"), instalment(IN_H2, "2000")));

    assertThat(sectionOf(SectionKind.ORDER_LEVEL).total().flatRateRevenueEuro()).isEqualByComparingTo("3000");
  }

  /** Dashboard and alerts have to see the flat rates, or they report a lower utilization than the evaluation. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_include_flat_rates_in_the_utilization() {
    var whole = plan("whole year", null, FROM, UNTIL, "4000");
    givenBudgets(whole);
    givenFlatRates(once("initial fee", null, IN_H1, "3200"));

    var info = service.computeUtilizationInfo(whole);

    // 800 EUR from the 8 h booked by today plus the flat rate of 3200 against a budget of 4000.
    assertThat(info.coveredRevenueEuro()).isEqualByComparingTo("4000.00");
    assertThat(info.percent()).isEqualTo(100.0);
  }

  /** The utilization follows the same allocation as the sections, so an ambiguous amount counts nowhere. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_leave_an_ambiguous_flat_rate_out_of_the_utilization() {
    var a = plan("A", null, FROM, UNTIL, "1000");
    var b = plan("B", null, FROM, UNTIL, "1000");
    givenBudgets(a, b);
    givenFlatRates(once("initial fee", null, IN_H1, "500"));

    assertThat(service.computeUtilizationInfo(a).coveredRevenueEuro()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(service.computeUtilizationInfo(b).coveredRevenueEuro()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // --- helpers ---------------------------------------------------------------------------------

  private BudgetControllingResult compute() {
    return compute(FROM, UNTIL);
  }

  private BudgetControllingResult compute(LocalDate from, LocalDate until) {
    return service.compute("co", from, until, false);
  }

  private static BudgetControllingSection sectionOf(BudgetControllingResult result, SectionKind kind) {
    return result.sections().stream().filter(s -> s.kind() == kind).findFirst().orElseThrow();
  }

  /** A further grant on an existing plan, taking effect on the given day. */
  private static void addAdjustment(OrderBudget budget, String amount, LocalDate effective) {
    var adjustment = new OrderBudgetAdjustment();
    adjustment.setOrderBudget(budget);
    adjustment.setAmount(new BigDecimal(amount));
    adjustment.setEffective(effective);
    budget.getAdjustments().add(adjustment);
  }

  private BudgetControllingSection sectionOf(SectionKind kind) {
    return compute().sections().stream().filter(s -> s.kind() == kind).findFirst().orElseThrow();
  }

  private BigDecimal revenueOverAllSections() {
    return compute().sections().stream().map(s -> s.total().revenueEuro())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** Hours and flat rates together — what the budget is actually measured against (#972). */
  private BigDecimal totalRevenueOverAllSections() {
    return compute().sections().stream().map(s -> s.total().totalRevenueEuro())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Duration hoursOverAllSections() {
    return compute().sections().stream().map(s -> s.total().bookedHours())
        .reduce(Duration.ZERO, Duration::plus);
  }

  /** Sets the plans and assigns every booking the way #909 and #910 would. */
  private void givenBudgets(OrderBudget... budgets) {
    plans.clear();
    plans.addAll(List.of(budgets));
    assignAsResolved();
  }

  private void givenReports(TimereportDTO... timereports) {
    reports.clear();
    reports.addAll(List.of(timereports));
    assignAsResolved();
  }

  private void givenSuborders(Suborder... subordersOfOrder) {
    suborders.clear();
    suborders.addAll(List.of(subordersOfOrder));
  }

  /**
   * The assignment as the automatic path produces it: every booking that exactly one active plan
   * covers is assigned to it, the rest stays unassigned. Uses the production resolver, so these
   * tests cannot drift away from what the application actually stores.
   */
  private void assignAsResolved() {
    var resolver = new BudgetResolver(orderBudgetRepository, suborderService);
    links.clear();
    for (var report : reports) {
      resolver.resolve(report).unique().ifPresent(plan ->
          links.add(new TimereportBudgetLink(report.getId(), plan.getId())));
    }
  }

  /** An assignment somebody made by hand, overriding what the resolver would have produced. */
  private void givenAssignment(TimereportDTO report, OrderBudget plan) {
    links.removeIf(link -> link.timereportId() == report.getId());
    links.add(new TimereportBudgetLink(report.getId(), plan.getId()));
  }

  /**
   * A real {@code Suborder}, not a mock. Mocking it used to include {@code withParents()}, and that
   * mock returned the parent first while the real method returns the suborder itself first — so the
   * test asserted the intended scope resolution while production did the opposite, and #931 stayed
   * invisible. The sign is the leaf part; the complete order sign follows from the parent chain.
   */
  private Suborder suborder(String sign, char invoice, long id, Suborder parent) {
    var suborder = new Suborder();
    setId(suborder, id);
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setShortdescription(sign);
    suborder.setInvoice(invoice);
    suborder.setParentorder(parent);
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

  /** Bookings and plans carry ids now: the assignment is keyed by them. */
  private static long nextId = 1;

  private TimereportDTO eightHoursOn(long suborderId, LocalDate day) {
    var suborder = suborders.stream().filter(so -> so.getId() == suborderId).findFirst();
    return TimereportDTO.builder()
        .id(nextId++)
        .customerorderSign("co")
        .completeOrderSign(suborder.map(Suborder::getCompleteOrderSign).orElse("co/?"))
        .suborderId(suborderId)
        .employeeSign("emp")
        .referenceday(day)
        .duration(Duration.ofHours(8))
        .build();
  }

  private static OrderBudget plan(String name, String suborderSign, LocalDate from, LocalDate until, String amount) {
    var budget = new OrderBudget();
    setId(budget, nextId++);
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

  /**
   * The progress belongs to the plan, so it rides on the group rather than on a row (#989). For a
   * plan on suborder level the verdict is read from its own subtotal — not from the section total,
   * which sums every plan of the section and would judge this one by its neighbours' spending.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_judge_a_suborder_level_plan_against_its_own_subtotal() {
    var first = plan("co/01", "co/01", FROM, UNTIL, "1000");
    withScopeProgress(first, 85);
    givenBudgets(first, plan("co/02", "co/02", FROM, UNTIL, "4000"));

    var group = groupOf(sectionOf(SectionKind.SUBORDER_LEVEL), "co/01");

    // 800 EUR against 1000 EUR is 80 %, and 85 % progress sits inside the ten point band.
    // Against the section total (1600 of 5000, i.e. 32 %) the same plan would read as AHEAD.
    assertThat(group.progressPercent()).isEqualTo(85.0);
    assertThat(group.progressStatus()).isEqualTo(ProgressStatus.ON_TRACK);
  }

  /** An order-wide plan has no subtotal — it is the whole section, so the total is its line. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_judge_an_order_wide_plan_against_the_section_total() {
    var plan = plan("H1", null, FROM, UNTIL, "1000");
    withScopeProgress(plan, 20);
    givenBudgets(plan);

    var group = sectionOf(SectionKind.ORDER_LEVEL).groups().get(0);

    // 1600 EUR against 1000 EUR is 160 %, far beyond the 20 % the plan has come.
    assertThat(group.progressPercent()).isEqualTo(20.0);
    assertThat(group.progressStatus()).isEqualTo(ProgressStatus.BEHIND);
  }

  /** Without a progress mode there is nothing to show, and the header says nothing about the plan. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_no_progress_on_a_group_whose_plan_has_no_progress_mode() {
    givenBudgets(plan("H1", null, FROM, UNTIL, "1000"));

    var group = sectionOf(SectionKind.ORDER_LEVEL).groups().get(0);

    assertThat(group.hasProgress()).isFalse();
    assertThat(group.progressFormatted()).isEqualTo("—");
  }

  /** The header links to the plan of the group, so the group has to carry its id. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_carry_the_plan_id_on_the_group() {
    var plan = plan("H1", null, FROM, UNTIL, "1000");
    givenBudgets(plan);

    assertThat(sectionOf(SectionKind.ORDER_LEVEL).groups().get(0).budgetId()).isEqualTo(plan.getId());
  }

  /** Bookings that answer to no plan cannot be behind one. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_no_progress_on_the_section_without_budget() {
    givenBudgets();

    var group = sectionOf(SectionKind.UNPLANNED).groups().get(0);

    assertThat(group.hasProgress()).isFalse();
    assertThat(group.hasBudgetPlan()).isFalse();
  }

  private static void withScopeProgress(OrderBudget budget, int percent) {
    budget.setProgressMode(ProgressMode.SCOPE);
    var entry = new OrderBudgetScopeEntry();
    entry.setOrderBudget(budget);
    entry.setRefdate(budget.getValidFrom());
    entry.setPercent(percent);
    budget.getScopeEntries().add(entry);
  }

  private static BudgetControllingGroup groupOf(BudgetControllingSection section, String sign) {
    return section.groups().stream().filter(g -> sign.equals(g.sign())).findFirst().orElseThrow();
  }

  /**
   * The dashboard asks for the progress of every active plan at once (behind-plan warning). A plan
   * in TIME mode has come as far as the share of its working days that have passed.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_compute_the_time_progress_of_several_plans_at_once() {
    var running = plan("H1", null, FROM, UNTIL, "1000");
    running.setProgressMode(ProgressMode.TIME);
    var finished = plan("done", null, FROM, APR, "500");
    finished.setProgressMode(ProgressMode.TIME);

    var progress = service.computeProgressPercents(List.of(running, finished));

    assertThat(progress.get(running.getId())).isBetween(40.0, 50.0);
    // Its end is behind us, so the plan has run its course: 100%, not more.
    assertThat(progress.get(finished.getId())).isEqualTo(100.0);
  }

  /** Without a progress mode there is nothing to be behind of — the plan is simply absent. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_no_progress_for_a_plan_without_a_progress_mode() {
    var plan = plan("H1", null, FROM, UNTIL, "1000");

    assertThat(service.computeProgressPercents(List.of(plan))).isEmpty();
  }

  /**
   * An open-ended plan has no time progress: 31.12.2999 says "no end", and measuring elapsed time
   * against it would both be meaningless and walk a thousand years of days.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_no_time_progress_for_an_open_ended_plan() {
    var plan = plan("open", null, FROM, LocalDateRange.FINIT_UNTIL_BOUNDARY, "1000");
    plan.setProgressMode(ProgressMode.TIME);

    assertThat(service.computeProgressPercents(List.of(plan))).isEmpty();
  }

  /**
   * BEHIND is the dashboard's warning: more of the budget spent than of the plan achieved. The ten
   * point band around equality keeps a plan that is a few days off from being flagged.
   */
  @Test
  public void should_judge_a_plan_that_spent_more_budget_than_it_made_progress_as_behind() {
    assertThat(BudgetControllingService.computeProgressStatus(20.0, 45.0)).isEqualTo(ProgressStatus.BEHIND);
    assertThat(BudgetControllingService.computeProgressStatus(50.0, 45.0)).isEqualTo(ProgressStatus.ON_TRACK);
    assertThat(BudgetControllingService.computeProgressStatus(60.0, 45.0)).isEqualTo(ProgressStatus.AHEAD);
    assertThat(BudgetControllingService.computeProgressStatus(null, 45.0)).isEqualTo(ProgressStatus.UNKNOWN);
    assertThat(BudgetControllingService.computeProgressStatus(50.0, null)).isEqualTo(ProgressStatus.UNKNOWN);
  }

  private void givenFlatRates(OrderFlatRate... rates) {
    flatRates.clear();
    flatRates.addAll(List.of(rates));
  }

  private static OrderFlatRate once(String description, String suborderSign, LocalDate due, String amount) {
    return flatRate(description, suborderSign, FlatRateRhythm.ONCE, due, due, amount);
  }

  private static OrderFlatRate monthly(String description, String suborderSign, LocalDate from,
                                       LocalDate until, String amount) {
    return flatRate(description, suborderSign, FlatRateRhythm.MONTHLY, from, until, amount);
  }

  private static OrderFlatRate instalments(String description, String suborderSign, LocalDate from,
                                           LocalDate until, OrderFlatRateInstalment... payments) {
    var rate = flatRate(description, suborderSign, FlatRateRhythm.INSTALMENTS, from, until, null);
    for (var payment : payments) {
      payment.setOrderFlatRate(rate);
      rate.getInstalments().add(payment);
    }
    return rate;
  }

  private static OrderFlatRateInstalment instalment(LocalDate due, String amount) {
    var instalment = new OrderFlatRateInstalment();
    instalment.setDue(due);
    instalment.setAmount(new BigDecimal(amount));
    return instalment;
  }

  private static OrderFlatRate flatRate(String description, String suborderSign, FlatRateRhythm rhythm,
                                        LocalDate from, LocalDate until, String amount) {
    var rate = new OrderFlatRate();
    setId(rate, nextId++);
    rate.setCustomerorderSign("co");
    rate.setSuborderSign(suborderSign);
    rate.setDescription(description);
    rate.setRhythm(rhythm);
    rate.setAmount(amount == null ? null : new BigDecimal(amount));
    rate.setValidFrom(from);
    rate.setValidUntil(until);
    return rate;
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
