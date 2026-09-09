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
import org.tb.budget.domain.BudgetControllingResult;
import org.tb.budget.domain.BudgetControllingRow;
import org.tb.budget.domain.BudgetControllingSection;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustment;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.domain.SectionKind;
import org.tb.budget.domain.TimereportBudgetLink;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
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

    // These tests are about the evaluation, so authorization lets every order through.
    var budgetAuthorization = mock(BudgetAuthorization.class);
    when(budgetAuthorization.isAuthorizedForCustomerorder(anyString())).thenReturn(true);

    service = new BudgetControllingService(customerorderService, suborderService, timereportService,
        orderBudgetRepository, assignmentRepository, orderPricingService, employeeCostService,
        publicholidayService, budgetAuthorization);
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

  // --- budgets that began before the window (#916) ---------------------------------------------

  /**
   * A window of one month with a plan that started a quarter earlier. Clipping the adjustments at
   * the window start reported 0 EUR for such a plan, while the bookings inside the window counted
   * against it — so the plan looked instantly overbooked.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_the_budget_left_when_the_window_opens() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    // The fixture books 8 h at 100 EUR in March, before the April window.
    var section = sectionOf(compute(APR, JUN), SectionKind.ORDER_LEVEL);

    assertThat(section.total().budgetEuro()).isEqualByComparingTo("1200.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_count_an_adjustment_from_before_the_window_and_one_inside_it() {
    var year = plan("year", null, FROM, UNTIL, "1000");
    addAdjustment(year, "500", LocalDate.of(2026, 5, 1));
    givenBudgets(year);

    var section = sectionOf(compute(APR, JUN), SectionKind.ORDER_LEVEL);

    // 1000 granted in January + 500 in May, minus 800 used up in March.
    assertThat(section.total().budgetEuro()).isEqualByComparingTo("700.00");
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_not_count_an_adjustment_that_takes_effect_after_the_window() {
    var year = plan("year", null, FROM, UNTIL, "1000");
    addAdjustment(year, "500", LocalDate.of(2026, 9, 1));
    givenBudgets(year);

    var section = sectionOf(compute(APR, JUN), SectionKind.ORDER_LEVEL);

    assertThat(section.total().budgetEuro()).isEqualByComparingTo("200.00");
  }

  /** Overbooked before the window even opened: reported as it is, not flattered to zero. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_report_a_negative_remainder_for_a_plan_already_overbooked() {
    givenBudgets(plan("small", null, FROM, UNTIL, "500"));

    var section = sectionOf(compute(APR, JUN), SectionKind.ORDER_LEVEL);

    assertThat(section.total().budgetEuro()).isEqualByComparingTo("-300.00");
  }

  /** The remainder is what the utilization and the traffic light are measured against. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_measure_the_utilization_against_the_remainder() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));
    // 8 h at 100 EUR in September, inside a window that starts in July; 800 used up before it.
    var section = sectionOf(compute(JUL, UNTIL), SectionKind.ORDER_LEVEL);

    assertThat(section.total().budgetEuro()).isEqualByComparingTo("1200.00");
    assertThat(section.total().revenueEuro()).isEqualByComparingTo("800.00");
    // 800 of the 1200 remaining is two thirds — measured against the full 2000 it would be 40 %.
    assertThat(section.total().budgetUsedPercent()).isCloseTo(66.67, within(0.01));
  }

  /** Only bookings from inside the window are listed, however far back the plan reaches. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_still_report_only_the_bookings_inside_the_window() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    var section = sectionOf(compute(JUL, UNTIL), SectionKind.ORDER_LEVEL);

    assertThat(section.total().bookedHours()).isEqualTo(Duration.ofHours(8));
  }

  /** The pre-window consumption costs one read for the order, not one per plan. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_read_the_bookings_before_the_window_only_once() {
    givenBudgets(plan("a", "co/01", FROM, UNTIL, "1000"), plan("b", "co/02", FROM, UNTIL, "1000"));

    compute(APR, JUN);

    // One read for the window itself, one for everything before it.
    verify(timereportService, times(2))
        .getTimereportsByDatesAndCustomerOrderId(any(), any(), anyLong());
  }

  /** A window that starts with the plan has nothing before it, so no read is issued at all. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_not_look_before_a_window_that_starts_with_the_plan() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    compute(FROM, UNTIL);

    verify(timereportService, times(1))
        .getTimereportsByDatesAndCustomerOrderId(any(), any(), anyLong());
  }

  /**
   * The section carries the two inputs its available budget was derived from, so the info box can
   * show the derivation rather than assert the result (#917). They have to agree with the table.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_carry_the_derivation_of_the_available_budget() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    var section = sectionOf(compute(APR, JUN), SectionKind.ORDER_LEVEL);
    var history = section.history();

    assertThat(history.cumulativeEuro()).isEqualByComparingTo("2000");
    assertThat(history.consumedBeforeEuro()).isEqualByComparingTo("800.00");
    assertThat(history.availableAtWindowStartEuro()).isEqualByComparingTo("1200.00");
    // The same figure the section total reports, so box and table cannot disagree.
    assertThat(history.availableAtWindowStartEuro()).isEqualByComparingTo(section.total().budgetEuro());
    assertThat(history.planFrom()).isEqualTo(FROM);
    assertThat(history.planUntil()).isEqualTo(UNTIL);
    assertThat(history.startedBeforeWindow()).isTrue();
    assertThat(history.isWorthShowing()).isTrue();
  }

  /** A window containing the whole plan has nothing to explain. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_mark_the_derivation_as_not_worth_showing_without_a_history() {
    givenBudgets(plan("year", null, FROM, UNTIL, "2000"));

    var history = sectionOf(compute(FROM, UNTIL), SectionKind.ORDER_LEVEL).history();

    assertThat(history.consumedBeforeEuro()).isEqualByComparingTo("0");
    assertThat(history.startedBeforeWindow()).isFalse();
    assertThat(history.isWorthShowing()).isFalse();
  }

  @Test
  @FixedClock("2026-06-15T10:00:00")
  public void should_carry_no_derivation_for_bookings_without_a_budget() {
    assertThat(sectionOf(compute(FROM, UNTIL), SectionKind.UNPLANNED).history()).isNull();
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
    assertThat(info.coveredRevenueEuro()).isEqualByComparingTo("1600.00");
    assertThat(info.percent()).isEqualTo(80.0);
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

  private static OrderPricing orderWideRate() {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign("co");
    pricing.setPriceCentsPerHour(10000);
    pricing.setValidFrom(LocalDate.of(2026, 1, 1));
    pricing.setValidUntil(LocalDate.of(2026, 12, 31));
    return pricing;
  }
}
