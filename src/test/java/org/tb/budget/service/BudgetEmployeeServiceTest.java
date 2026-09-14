package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.AssignedBooking;
import org.tb.budget.domain.AssignedEmployeeDay;
import org.tb.budget.domain.BudgetEmployee;
import org.tb.budget.domain.BudgetEmployeeMinutes;
import org.tb.budget.domain.CostCategoryRate;
import org.tb.budget.domain.EmployeeCost;
import org.tb.budget.domain.EmployeeCostAssignment;
import org.tb.budget.domain.EmployeeCostLookup;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingLookup;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
import org.tb.common.GlobalConstants;
import org.tb.common.domain.AuditedEntity;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.service.SuborderService;

/**
 * The "Mitarbeitende" card of a budget plan (#964).
 *
 * <p>Both lookups are built for real from records rather than mocked: what the card is about is
 * which rate the resolution finds, and a stubbed resolution would assert nothing about that. Only
 * the repository, the suborders and the two services that load the lookups are mocks.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetEmployeeServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 15);
  private static final LocalDate JUL = LocalDate.of(2026, 7, 15);

  private static final long BILLED = 1L;
  private static final long NOT_INVOICEABLE = 2L;

  private final List<AssignedEmployeeDay> days = new ArrayList<>();
  private final List<EmployeeCostAssignment> costAssignments = new ArrayList<>();
  private final List<EmployeeCost> costs = new ArrayList<>();
  private final List<OrderPricing> pricings = new ArrayList<>();

  private TimereportBudgetAssignmentRepository assignmentRepository;
  private BudgetAuthorization budgetAuthorization;
  private SuborderService suborderService;
  private EmployeeCostService employeeCostService;
  private OrderPricingService orderPricingService;
  private AuthorizedUser authorizedUser;
  private BudgetEmployeeService service;

  private OrderBudget plan;

  @BeforeEach
  public void setUp() {
    assignmentRepository = mock(TimereportBudgetAssignmentRepository.class);
    budgetAuthorization = mock(BudgetAuthorization.class);
    suborderService = mock(SuborderService.class);
    employeeCostService = mock(EmployeeCostService.class);
    orderPricingService = mock(OrderPricingService.class);
    authorizedUser = mock(AuthorizedUser.class);
    service = new BudgetEmployeeService(assignmentRepository, budgetAuthorization, suborderService,
        employeeCostService, orderPricingService, authorizedUser);

    plan = plan(42L);
    when(authorizedUser.isManager()).thenReturn(true);
    when(assignmentRepository.findAssignedEmployeeDays(anyLong(), any(), any())).thenReturn(days);
    when(suborderService.getSuborderById(BILLED)).thenReturn(suborder(BILLED, "01", true));
    when(suborderService.getSuborderById(NOT_INVOICEABLE))
        .thenReturn(suborder(NOT_INVOICEABLE, "02", false));
    when(employeeCostService.lookup())
        .thenAnswer(invocation -> EmployeeCostLookup.of(costAssignments, costs));
    when(orderPricingService.lookupFor(any()))
        .thenAnswer(invocation -> OrderPricingLookup.of(pricings));
  }

  @Test
  public void condenses_the_days_of_one_person_into_one_row() {
    costAssignment("abc", null, "Senior");
    cost("Senior", 9500, FROM, UNTIL);
    pricing(null, null, 14000, FROM, UNTIL);
    day("abc", BILLED, JUN, 2, Duration.ofHours(6));
    day("abc", BILLED, JUL, 1, Duration.ofHours(2));

    assertThat(rows()).singleElement().satisfies(row -> {
      assertThat(row.employeeSign()).isEqualTo("abc");
      assertThat(row.bookings()).isEqualTo(3);
      assertThat(row.duration()).isEqualTo(Duration.ofHours(8));
      assertThat(row.costs()).containsExactly(new CostCategoryRate("Senior", 9500));
      assertThat(row.priceCentsPerHour()).containsExactly(14000);
      assertThat(row.missingCost()).isFalse();
      assertThat(row.missingPrice()).isFalse();
    });
  }

  @Test
  public void puts_the_person_with_the_most_hours_on_top() {
    day("abc", BILLED, JUN, 1, Duration.ofHours(3));
    day("def", BILLED, JUN, 1, Duration.ofHours(9));
    day("ghi", BILLED, JUN, 1, Duration.ofHours(6));

    assertThat(rows()).extracting(BudgetEmployee::employeeSign).containsExactly("def", "ghi", "abc");
  }

  /**
   * Several rates over the bookings of one person are the rule, not an edge case — a rate that
   * changed, or a suborder with one of its own. All of them are named; choosing one would be a
   * silent claim about the others.
   */
  @Test
  public void names_every_cost_category_that_applies_rather_than_choosing_one() {
    costAssignment("abc", null, "Senior");
    costAssignment("abc", "co/02", "Standby");
    cost("Senior", 9500, FROM, UNTIL);
    cost("Standby", 3000, FROM, UNTIL);
    day("abc", BILLED, JUN, 1, Duration.ofHours(4));
    day("abc", NOT_INVOICEABLE, JUN, 1, Duration.ofHours(2));

    assertThat(rows()).singleElement().satisfies(row ->
        assertThat(row.costs()).containsExactly(
            new CostCategoryRate("Senior", 9500), new CostCategoryRate("Standby", 3000)));
  }

  /** A rate that changed within the period yields two entries under the same name. */
  @Test
  public void names_both_rates_of_a_category_that_changed_within_the_period() {
    costAssignment("abc", null, "Senior");
    cost("Senior", 9500, FROM, JUN);
    cost("Senior", 10000, JUN.plusDays(1), UNTIL);
    day("abc", BILLED, JUN, 1, Duration.ofHours(4));
    day("abc", BILLED, JUL, 1, Duration.ofHours(4));

    assertThat(rows()).singleElement().satisfies(row ->
        assertThat(row.costs()).containsExactly(
            new CostCategoryRate("Senior", 9500), new CostCategoryRate("Senior", 10000)));
  }

  @Test
  public void names_every_condition_that_applies() {
    pricing(null, null, 14000, FROM, JUN);
    pricing(null, null, 15000, JUN.plusDays(1), UNTIL);
    day("abc", BILLED, JUN, 1, Duration.ofHours(4));
    day("abc", BILLED, JUL, 1, Duration.ofHours(4));

    assertThat(rows()).singleElement().satisfies(row ->
        assertThat(row.priceCentsPerHour()).containsExactly(14000, 15000));
  }

  /** 0,00 EUR/h is a stored, deliberate statement and must not look like a missing condition. */
  @Test
  public void shows_a_condition_of_zero_euro_and_does_not_fault_it() {
    pricing(null, null, 0, FROM, UNTIL);
    day("abc", BILLED, JUN, 1, Duration.ofHours(4));

    assertThat(rows()).singleElement().satisfies(row -> {
      assertThat(row.priceCentsPerHour()).containsExactly(0);
      assertThat(row.missingPrice()).isFalse();
    });
  }

  @Test
  public void adds_up_the_hours_without_a_rate_on_either_side() {
    // Both rates exist for "abc" alone, so "def" and "ghi" have neither.
    costAssignment("abc", null, "Senior");
    cost("Senior", 9500, FROM, UNTIL);
    pricing(null, "abc", 14000, FROM, UNTIL);
    day("abc", BILLED, JUN, 1, Duration.ofHours(4));
    day("def", BILLED, JUN, 1, Duration.ofHours(3));
    day("ghi", BILLED, JUN, 1, Duration.ofHours(2));

    var employees = service.resolve(plan, FROM, UNTIL, List.of()).employees();

    assertThat(employees.durationWithoutCost()).isEqualTo(Duration.ofHours(5));
    assertThat(employees.durationWithoutPrice()).isEqualTo(Duration.ofHours(5));
    assertThat(employees.hasFindings()).isTrue();
  }

  /** 0 EUR revenue is right there, whatever rate matches — named apart, never faulted. */
  @Test
  public void counts_hours_on_a_suborder_that_is_not_invoiceable_apart_from_a_missing_condition() {
    day("abc", NOT_INVOICEABLE, JUN, 1, Duration.ofHours(4));

    var employees = service.resolve(plan, FROM, UNTIL, List.of()).employees();

    assertThat(employees.durationNotInvoiceable()).isEqualTo(Duration.ofHours(4));
    assertThat(employees.durationWithoutPrice()).isZero();
    assertThat(employees.rows()).singleElement()
        .satisfies(row -> assertThat(row.hasNotInvoiceable()).isTrue());
  }

  /** A rate that cannot take effect is not named either — the row says "not invoiceable" instead. */
  @Test
  public void does_not_name_a_condition_resolved_on_a_suborder_that_is_not_invoiceable() {
    pricing(null, null, 14000, FROM, UNTIL);
    day("abc", NOT_INVOICEABLE, JUN, 1, Duration.ofHours(4));

    assertThat(rows()).singleElement()
        .satisfies(row -> assertThat(row.priceCentsPerHour()).isEmpty());
  }

  /**
   * The cost side is managers only. Loading it for anybody else would not merely leak a figure —
   * {@code EmployeeCostService} requires a manager on its class, so the call would throw and the
   * whole detail page would break for the order responsible the card is meant for.
   */
  @Test
  public void never_loads_the_cost_lookup_for_somebody_who_is_not_a_manager() {
    when(authorizedUser.isManager()).thenReturn(false);
    day("abc", BILLED, JUN, 1, Duration.ofHours(4));
    pricing(null, null, 14000, FROM, UNTIL);

    var employees = service.resolve(plan, FROM, UNTIL, List.of()).employees();

    verify(employeeCostService, never()).lookup();
    assertThat(employees.costsIncluded()).isFalse();
    assertThat(employees.durationWithoutCost()).isZero();
    assertThat(employees.rows()).singleElement().satisfies(row -> {
      assertThat(row.costs()).isEmpty();
      assertThat(row.missingCost()).isFalse();
      assertThat(row.priceCentsPerHour()).containsExactly(14000);
    });
  }

  /** Card and rows are two views of one resolution; if they disagreed, one of them would be wrong. */
  @Test
  public void gives_a_rendered_booking_the_same_rates_the_card_shows_for_that_person() {
    costAssignment("abc", null, "Senior");
    cost("Senior", 9500, FROM, UNTIL);
    pricing(null, null, 14000, FROM, UNTIL);
    day("abc", BILLED, JUN, 1, Duration.ofHours(4));

    var rates = service.resolve(plan, FROM, UNTIL, List.of(booking(11L, "abc", BILLED, JUN)));

    var rate = rates.of(11L);
    assertThat(rate.costName()).isEqualTo("Senior");
    assertThat(rate.costCentsPerHour()).isEqualTo(9500);
    assertThat(rate.priceCentsPerHour()).isEqualTo(14000);
    assertThat(rates.employees().rows()).singleElement().satisfies(row -> {
      assertThat(row.costs()).containsExactly(new CostCategoryRate("Senior", 9500));
      assertThat(row.priceCentsPerHour()).containsExactly(14000);
    });
  }

  @Test
  public void checks_the_access_to_the_plan_before_reading_anything() {
    service.resolve(plan, FROM, UNTIL, List.of());

    verify(budgetAuthorization).checkAuthorized(plan);
  }

  @Test
  public void reads_nothing_for_a_plan_without_bookings() {
    var rates = service.resolve(plan, FROM, UNTIL, List.of());

    assertThat(rates.employees().isEmpty()).isTrue();
    assertThat(rates.byBookingId()).isEmpty();
    verifyNoInteractions(orderPricingService);
    verify(employeeCostService, never()).lookup();
  }

  // --- the overview column ----------------------------------------------------------------------

  @Test
  public void groups_the_people_of_the_overview_by_plan() {
    var second = plan(43L);
    when(budgetAuthorization.isAuthorized(any())).thenReturn(true);
    when(assignmentRepository.findEmployeeMinutesByBudgetIds(List.of(42L, 43L))).thenReturn(List.of(
        new BudgetEmployeeMinutes(42L, "abc", "Abc Person", 480L),
        new BudgetEmployeeMinutes(42L, "def", "Def Person", 120L),
        new BudgetEmployeeMinutes(43L, "abc", "Abc Person", 60L)));

    var byPlan = service.employeesOf(List.of(plan, second));

    assertThat(byPlan.get(42L)).extracting(BudgetEmployeeMinutes::employeeSign)
        .containsExactly("abc", "def");
    assertThat(byPlan.get(43L)).extracting(BudgetEmployeeMinutes::employeeSign).containsExactly("abc");
  }

  /** The query establishes nothing about who may see a plan, so the check is repeated here. */
  @Test
  public void leaves_out_plans_the_user_may_not_see() {
    var forbidden = plan(43L);
    when(budgetAuthorization.isAuthorized(plan)).thenReturn(true);
    when(budgetAuthorization.isAuthorized(forbidden)).thenReturn(false);
    when(assignmentRepository.findEmployeeMinutesByBudgetIds(List.of(42L))).thenReturn(List.of());

    service.employeesOf(List.of(plan, forbidden));

    verify(assignmentRepository).findEmployeeMinutesByBudgetIds(List.of(42L));
  }

  /** {@code IN ()} is not valid SQL, and there is nothing to group anyway. */
  @Test
  public void asks_nothing_when_no_plan_is_visible() {
    when(budgetAuthorization.isAuthorized(any())).thenReturn(false);

    assertThat(service.employeesOf(List.of(plan))).isEmpty();

    verify(assignmentRepository, never()).findEmployeeMinutesByBudgetIds(any());
  }

  // --- fixtures ---------------------------------------------------------------------------------

  private List<BudgetEmployee> rows() {
    return service.resolve(plan, FROM, UNTIL, List.of()).employees().rows();
  }

  private void day(String employeeSign, long suborderId, LocalDate day, long bookings, Duration duration) {
    days.add(new AssignedEmployeeDay(employeeSign, employeeSign + " Person", suborderId, day,
        bookings, duration.toMinutes()));
  }

  private static AssignedBooking booking(long id, String employeeSign, long suborderId, LocalDate day) {
    return new AssignedBooking(id, day, suborderId, "co/01", employeeSign, employeeSign + " Person",
        Duration.ofHours(4), "task");
  }

  private void costAssignment(String employeeSign, String suborderSign, String costName) {
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployeeSign(employeeSign);
    assignment.setSuborderSign(suborderSign);
    assignment.setEmployeeCostName(costName);
    assignment.setValidFrom(FROM);
    assignment.setValidUntil(UNTIL);
    costAssignments.add(assignment);
  }

  private void cost(String name, int centsPerHour, LocalDate from, LocalDate until) {
    var cost = new EmployeeCost();
    cost.setName(name);
    cost.setCostCentsPerHour(centsPerHour);
    cost.setValidFrom(from);
    cost.setValidUntil(until);
    costs.add(cost);
  }

  private void pricing(String suborderSign, String employeeSign, int centsPerHour,
                       LocalDate from, LocalDate until) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign("co");
    pricing.setSuborderSign(suborderSign);
    pricing.setEmployeeSign(employeeSign);
    pricing.setPriceCentsPerHour(centsPerHour);
    pricing.setValidFrom(from);
    pricing.setValidUntil(until);
    pricings.add(pricing);
  }

  private static OrderBudget plan(long id) {
    var budget = new OrderBudget();
    budget.setName("plan " + id);
    budget.setCustomerorderSign("co");
    budget.setValidFrom(FROM);
    budget.setValidUntil(UNTIL);
    budget.setActive(true);
    setId(budget, id);
    return budget;
  }

  private static Suborder suborder(long id, String sign, boolean invoiceable) {
    var customerorder = new Customerorder();
    customerorder.setSign("co");
    customerorder.setOrderType(OrderType.STANDARD);

    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setInvoice(invoiceable ? GlobalConstants.INVOICE_YES : 'N');
    setId(suborder, id);
    return suborder;
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
