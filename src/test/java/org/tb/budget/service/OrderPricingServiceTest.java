package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderPricing;
import org.tb.budget.domain.OrderPricingData;
import org.tb.budget.domain.OrderPricingRow;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.OrderPricingRepository;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.test.FixedClock;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * The filters of the customer rate list (#949, #957). Two things can have expired, and they are
 * filtered apart: the rate itself — its validity lies entirely in the past — and the customer order
 * it hangs off. Everything else, including a rate that only starts next month, is shown by default.
 */
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderPricingServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final LocalDate YESTERDAY = TODAY.minusDays(1);
  private static final LocalDate TOMORROW = TODAY.plusDays(1);
  private static final LocalDate OPEN_END = LocalDate.of(2999, 12, 31);

  private OrderPricingRepository orderPricingRepository;
  private OrderBudgetRepository orderBudgetRepository;
  private BudgetAuthorization budgetAuthorization;
  private SuborderService suborderService;
  private CustomerorderService customerorderService;
  private EmployeeService employeeService;
  private OrderPricingService service;

  @BeforeEach
  public void setUp() {
    orderPricingRepository = mock(OrderPricingRepository.class);
    customerorderService = mock(CustomerorderService.class);
    // No orders unless a test says so: a rate whose order is gone behaves as before (#957).
    when(customerorderService.getCustomerordersBySigns(any())).thenReturn(List.of());
    // The order and the employee of a written rate exist unless a test says otherwise (#958).
    when(customerorderService.getCustomerorderBySign(any())).thenReturn(new Customerorder());
    employeeService = mock(EmployeeService.class);
    when(employeeService.getEmployeeBySign(any())).thenReturn(new Employee());
    orderBudgetRepository = mock(OrderBudgetRepository.class);
    when(orderBudgetRepository.findByCustomerorderSign(any())).thenReturn(List.of());
    budgetAuthorization = mock(BudgetAuthorization.class);
    when(budgetAuthorization.isAuthorized(any())).thenReturn(true);
    suborderService = mock(SuborderService.class);
    service = new OrderPricingService(orderPricingRepository, orderBudgetRepository, suborderService,
        customerorderService, employeeService, budgetAuthorization);
  }

  @Test
  public void leaves_out_a_rate_that_ended_yesterday() {
    given(pricing("co", TODAY.minusYears(1), YESTERDAY));

    assertThat(pricingsOf(service.getRows(null, false, true))).isEmpty();
  }

  @Test
  public void keeps_a_rate_that_ends_today() {
    var endingToday = pricing("co", TODAY.minusYears(1), TODAY);
    given(endingToday);

    assertThat(pricingsOf(service.getRows(null, false, true))).containsExactly(endingToday);
  }

  @Test
  public void keeps_a_rate_without_an_end_date() {
    var openEnded = pricing("co", TODAY.minusYears(1), OPEN_END);
    given(openEnded);

    assertThat(pricingsOf(service.getRows(null, false, true))).containsExactly(openEnded);
  }

  /** A rise entered ahead of time must stay visible, or it gets entered a second time. */
  @Test
  public void keeps_a_rate_that_only_starts_tomorrow() {
    var future = pricing("co", TOMORROW, OPEN_END);
    given(future);

    assertThat(pricingsOf(service.getRows(null, false, true))).containsExactly(future);
  }

  @Test
  public void shows_the_expired_rates_as_well_when_asked_to() {
    var expired = pricing("co", TODAY.minusYears(1), YESTERDAY);
    var current = pricing("co", TODAY, OPEN_END);
    given(expired, current);

    assertThat(pricingsOf(service.getRows(null, true, true))).containsExactly(expired, current);
  }

  @Test
  public void narrows_the_list_to_the_chosen_customer_order() {
    var chosen = pricing("co-one", TODAY, OPEN_END);
    when(orderPricingRepository.findByCustomerorderSignOrderByValidFromAsc("co-one"))
        .thenReturn(List.of(chosen));

    assertThat(pricingsOf(service.getRows("co-one", false, true))).containsExactly(chosen);
  }

  /** Both filters apply at once — picking an order does not bring its expired rates back. */
  @Test
  public void leaves_out_the_expired_rates_of_the_chosen_customer_order() {
    var expired = pricing("co-one", TODAY.minusYears(1), YESTERDAY);
    var current = pricing("co-one", TODAY, OPEN_END);
    when(orderPricingRepository.findByCustomerorderSignOrderByValidFromAsc("co-one"))
        .thenReturn(List.of(expired, current));

    assertThat(pricingsOf(service.getRows("co-one", false, true))).containsExactly(current);
  }

  /** The empty option of the select submits an empty string, which means "all orders". */
  @Test
  public void treats_a_blank_customer_order_as_no_choice_at_all() {
    var any = pricing("co", TODAY, OPEN_END);
    given(any);

    assertThat(pricingsOf(service.getRows("  ", false, true))).containsExactly(any);
  }

  // --- the validity of the order behind the rate (#957) ---------------------------------------

  @Test
  public void leaves_out_the_rates_of_an_order_whose_validity_has_expired() {
    given(pricing("co", TODAY.minusYears(1), OPEN_END));
    givenOrder("co", TODAY.minusYears(2), YESTERDAY);

    assertThat(service.getRows(null, false, false)).isEmpty();
  }

  @Test
  public void keeps_the_rates_of_an_order_that_ends_today() {
    var rate = pricing("co", TODAY.minusYears(1), OPEN_END);
    given(rate);
    givenOrder("co", TODAY.minusYears(2), TODAY);

    assertThat(pricingsOf(service.getRows(null, false, false))).containsExactly(rate);
  }

  @Test
  public void keeps_the_rates_of_an_order_without_an_end() {
    var rate = pricing("co", TODAY.minusYears(1), OPEN_END);
    given(rate);
    givenOrder("co", TODAY.minusYears(2), null);

    assertThat(pricingsOf(service.getRows(null, false, false))).containsExactly(rate);
  }

  @Test
  public void shows_the_rates_of_expired_orders_as_well_when_asked_to() {
    var rate = pricing("co", TODAY.minusYears(1), OPEN_END);
    given(rate);
    givenOrder("co", TODAY.minusYears(2), YESTERDAY);

    assertThat(pricingsOf(service.getRows(null, false, true))).containsExactly(rate);
  }

  /**
   * The order is the only way into the rate, so a rate whose order is gone has to stay visible —
   * otherwise it could not be reached through the user interface at all.
   */
  @Test
  public void keeps_a_rate_whose_customer_order_no_longer_exists() {
    var orphan = pricing("gone", TODAY.minusYears(1), OPEN_END);
    given(orphan);

    assertThat(pricingsOf(service.getRows(null, false, false))).containsExactly(orphan);
  }

  /** The two switches are independent: an expired rate of a valid order needs the other one. */
  @Test
  public void applies_the_two_switches_apart_from_each_other() {
    var expiredRate = pricing("co", TODAY.minusYears(1), YESTERDAY);
    given(expiredRate);
    givenOrder("co", TODAY.minusYears(2), null);

    assertThat(service.getRows(null, false, false)).isEmpty();
    assertThat(pricingsOf(service.getRows(null, true, false))).containsExactly(expiredRate);
  }

  /** Coverage is judged against all stored rates, not against the ones the filter leaves over. */
  @Test
  public void judges_the_coverage_against_the_rates_the_filter_leaves_out_as_well() {
    var expired = pricing("co", TODAY.minusYears(2), YESTERDAY);
    var current = pricing("co", TODAY, OPEN_END);
    given(expired, current);
    givenOrder("co", TODAY.minusYears(2), null);

    var rows = service.getRows(null, false, false);

    assertThat(rows).singleElement()
        .extracting(row -> row.deviation().uncoveredOrderPeriod()).isEqualTo(false);
  }

  // --- the signs a rate references (#958) -----------------------------------------------------

  /**
   * The form protects the employee only as long as the input comes from its select. A rate with a
   * sign no person carries never matches during controlling: the work silently falls back to the
   * order-wide rate, which is a wrong number rather than an error.
   */
  @Test
  public void should_reject_a_new_rate_for_an_employee_that_does_not_exist() {
    when(employeeService.getEmployeeBySign("ghost")).thenReturn(null);

    assertThatThrownBy(() -> service.save(data("co", null, "ghost")))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_SIGN_UNKNOWN.getCode());
    verify(orderPricingRepository, never()).save(any());
  }

  @Test
  public void should_accept_a_new_rate_for_an_employee_that_exists() {
    service.save(data("co", null, "emp"));

    verify(orderPricingRepository).save(any());
  }

  /** No employee at all is the normal case: the rate then applies to everyone on the order. */
  @Test
  public void should_not_ask_for_an_employee_when_the_rate_names_none() {
    service.save(data("co", null, null));

    verify(employeeService, never()).getEmployeeBySign(any());
    verify(orderPricingRepository).save(any());
  }

  @Test
  public void should_reject_a_new_rate_for_a_customer_order_that_does_not_exist() {
    when(customerorderService.getCustomerorderBySign("gone")).thenReturn(null);

    assertThatThrownBy(() -> service.save(data("gone", null, null)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.BU_CUSTOMERORDER_SIGN_UNKNOWN.getCode());
    verify(orderPricingRepository, never()).save(any());
  }

  /**
   * A rate outlives its order on purpose (#957). Insisting on the order when editing would leave
   * such a rate only deletable — while editing it is how it gets corrected.
   */
  @Test
  public void should_keep_a_rate_editable_whose_customer_order_no_longer_exists() {
    var orphan = pricing("gone", TODAY.minusYears(1), OPEN_END);
    setId(orphan, 5L);
    when(orderPricingRepository.findById(5L)).thenReturn(Optional.of(orphan));
    when(customerorderService.getCustomerorderBySign("gone")).thenReturn(null);

    service.update(5L, data("gone", null, null));

    verify(orderPricingRepository).save(orphan);
  }

  @Test
  public void should_reject_an_edit_that_moves_a_rate_to_an_unknown_employee() {
    var edited = pricing("co", TODAY.minusYears(1), OPEN_END);
    setId(edited, 6L);
    when(orderPricingRepository.findById(6L)).thenReturn(Optional.of(edited));
    when(employeeService.getEmployeeBySign("ghost")).thenReturn(null);

    assertThatThrownBy(() -> service.update(6L, data("co", null, "ghost")))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_SIGN_UNKNOWN.getCode());
    verify(orderPricingRepository, never()).save(any());
  }

  // --- rates left behind on a sign nobody carries (#966) ---------------------------------------

  /**
   * Since a sign change is followed, a rate on a sign no person carries can only be a leftover from
   * before. It resolves to nothing and lets the work fall back to the order-wide rate, so the list
   * has to say so instead of leaving it to be noticed in a total.
   */
  @Test
  public void marks_a_rate_whose_employee_sign_nobody_carries() {
    given(pricingFor("ghost"));

    assertThat(service.getRows(null, false, true))
        .singleElement().extracting(OrderPricingRow::employeeUnknown).isEqualTo(true);
  }

  @Test
  public void leaves_a_rate_alone_whose_employee_still_exists() {
    givenEmployees("emp");
    given(pricingFor("emp"));

    assertThat(service.getRows(null, false, true))
        .singleElement().extracting(OrderPricingRow::employeeUnknown).isEqualTo(false);
  }

  /** A rate without an employee applies to everyone on the order — there is nothing to be unknown. */
  @Test
  public void marks_no_rate_that_names_no_employee() {
    given(pricingFor(null));

    assertThat(service.getRows(null, false, true))
        .singleElement().extracting(OrderPricingRow::employeeUnknown).isEqualTo(false);
  }

  private void givenEmployees(String... signs) {
    when(employeeService.getAllEmployeeSigns()).thenReturn(Set.of(signs));
  }

  private static OrderPricing pricingFor(String employeeSign) {
    var pricing = pricing("co", TODAY.minusYears(1), OPEN_END);
    pricing.setEmployeeSign(employeeSign);
    return pricing;
  }

  private static OrderPricingData data(String customerorderSign, String suborderSign, String employeeSign) {
    return new OrderPricingData(customerorderSign, suborderSign, employeeSign, null, null, 10000, TODAY, null);
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

  private void givenOrder(String sign, LocalDate fromDate, LocalDate untilDate) {
    var order = new Customerorder();
    order.setSign(sign);
    order.setFromDate(fromDate);
    order.setUntilDate(untilDate);
    when(customerorderService.getCustomerordersBySigns(any())).thenReturn(List.of(order));
  }

  private static List<OrderPricing> pricingsOf(List<OrderPricingRow> rows) {
    return rows.stream().map(OrderPricingRow::pricing).toList();
  }

  private void given(OrderPricing... pricings) {
    when(orderPricingRepository.findAllByOrderByCustomerorderSignAscValidFromAsc())
        .thenReturn(List.of(pricings));
  }

  private static OrderPricing pricing(String customerorderSign, LocalDate validFrom, LocalDate validUntil) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign(customerorderSign);
    pricing.setPriceCentsPerHour(10000);
    pricing.setValidFrom(validFrom);
    pricing.setValidUntil(validUntil);
    return pricing;
  }

  // --- which budget plans a rate may be bound to (#1065) ----------------------------------------

  /**
   * Selection and validation answer the same question, so each of these cases is asserted twice:
   * the plan is not offered, and a post that names it anyway is refused.
   */
  @Test
  public void offers_a_plan_of_the_same_order_whose_scope_and_period_meet_the_rate() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, true));

    assertThat(plansFor("co", null)).extracting(OrderBudget::getId).containsExactly(1L);
  }

  @Test
  public void refuses_a_plan_of_another_customer_order() {
    givenPlans(plan(1L, "other", null, TODAY, OPEN_END, true));

    assertThat(plansFor("co", null)).isEmpty();
    assertThatThrownBy(() -> service.save(dataWithPlan("co", null, 1L)))
        .extracting(e -> errorCodeOf((ErrorCodeException) e))
        .isEqualTo(ErrorCode.BU_BUDGET_SCOPE_DISJOINT);
  }

  @Test
  public void refuses_a_plan_whose_scope_is_disjoint_from_the_pattern() {
    givenSuborders("co/01", "co/02");
    givenPlans(plan(1L, "co", "co/02", TODAY, OPEN_END, true));

    assertThat(plansFor("co", "co/01/")).isEmpty();
    assertThatThrownBy(() -> service.save(dataWithPlan("co", "co/01/", 1L)))
        .extracting(e -> errorCodeOf((ErrorCodeException) e))
        .isEqualTo(ErrorCode.BU_BUDGET_SCOPE_DISJOINT);
  }

  /** The plan narrows the rate — the very purpose of the new level. */
  @Test
  public void offers_a_plan_narrower_than_the_pattern() {
    givenSuborders("co/01", "co/01/A");
    givenPlans(plan(1L, "co", "co/01/A", TODAY, OPEN_END, true));

    assertThat(plansFor("co", "co/01/")).extracting(OrderBudget::getId).containsExactly(1L);
  }

  /** And the other way round: a wide plan over a narrow pattern meets it just as well. */
  @Test
  public void offers_a_plan_wider_than_the_pattern() {
    givenSuborders("co/01", "co/01/A");
    givenPlans(plan(1L, "co", "co/01", TODAY, OPEN_END, true));

    assertThat(plansFor("co", "co/01/A/")).extracting(OrderBudget::getId).containsExactly(1L);
  }

  /** An order-wide rate meets every plan of its order, even before any suborder exists. */
  @Test
  public void offers_a_plan_to_an_order_wide_rate_without_asking_the_suborders() {
    givenPlans(plan(1L, "co", "co/01", TODAY, OPEN_END, true));

    assertThat(plansFor("co", null)).extracting(OrderBudget::getId).containsExactly(1L);
  }

  /**
   * On a new rate the validity is entered below the plan, so a plan select that waits for it is
   * dead at the moment it is operated — which is how it reached the user (empty on create, filled
   * on edit). Each condition applies as soon as its field is filled in; the saving applies all
   * three either way.
   */
  @Test
  public void offers_the_plans_of_the_order_before_a_validity_has_been_entered() {
    givenPlans(plan(1L, "co", null, TODAY.minusYears(3), YESTERDAY, true));

    assertThat(service.getSelectablePlans("co", null, null, null, null).plans())
        .extracting(OrderBudget::getId).containsExactly(1L);
  }

  /** …and narrows again the moment it is. */
  @Test
  public void narrows_the_plans_once_the_validity_is_entered() {
    givenPlans(plan(1L, "co", null, TODAY.minusYears(3), YESTERDAY, true));

    assertThat(service.getSelectablePlans("co", null, TODAY, null, null).plans()).isEmpty();
  }

  /** Without an order there is nothing to narrow at all — a plan belongs to one. */
  @Test
  public void offers_nothing_before_an_order_has_been_chosen() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, true));

    assertThat(service.getSelectablePlans(null, null, null, null, null).plans()).isEmpty();
  }

  @Test
  public void refuses_a_plan_whose_validity_does_not_overlap_the_rate() {
    givenPlans(plan(1L, "co", null, TODAY.minusYears(2), YESTERDAY, true));

    assertThat(plansFor("co", null)).isEmpty();
    assertThatThrownBy(() -> service.save(dataWithPlan("co", null, 1L)))
        .extracting(e -> errorCodeOf((ErrorCodeException) e))
        .isEqualTo(ErrorCode.BU_BUDGET_PERIOD_DISJOINT);
  }

  /** No booking is assigned to an inactive plan, so a rate on one would apply to nobody. */
  @Test
  public void does_not_offer_an_inactive_plan() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, false));

    assertThat(service.getSelectablePlans("co", null, TODAY, null, null).plans()).isEmpty();
  }

  /** …but the one the rate already stores stays, or the next save would silently drop it. */
  @Test
  public void keeps_the_stored_plan_in_the_list_even_once_it_is_inactive() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, false));

    var selectable = service.getSelectablePlans("co", null, TODAY, null, 1L);

    assertThat(selectable.plans()).extracting(OrderBudget::getId).containsExactly(1L);
    assertThat(selectable.notFittingId()).isEqualTo(1L);
  }

  /** An inactive plan is still storable: deactivating one must not make its rate uneditable. */
  @Test
  public void still_stores_a_rate_bound_to_a_plan_that_has_become_inactive() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, false));

    service.save(dataWithPlan("co", null, 1L));

    verify(orderPricingRepository).save(any());
  }

  // --- the plan the form already holds survives the narrowing (#1065) --------------------------

  /**
   * Narrowing the select must never take away what the form holds. A select cannot mark a value
   * that is not among its options, so the browser falls back to the first entry and the next save
   * writes that — here it would quietly unbind a rate whose condition somebody negotiated (#1005).
   */
  @Test
  public void keeps_the_held_plan_when_the_validity_no_longer_fits_it() {
    givenPlans(plan(1L, "co", null, TODAY.minusYears(3), YESTERDAY, true));

    var selectable = service.getSelectablePlans("co", null, TODAY, null, 1L);

    assertThat(selectable.plans()).extracting(OrderBudget::getId).containsExactly(1L);
    assertThat(selectable.notFittingId()).isEqualTo(1L);
  }

  @Test
  public void keeps_the_held_plan_when_the_scope_no_longer_fits_it() {
    givenSuborders("co/01", "co/02");
    givenPlans(plan(1L, "co", "co/02", TODAY, OPEN_END, true));

    var selectable = service.getSelectablePlans("co", "co/01/", TODAY, null, 1L);

    assertThat(selectable.plans()).extracting(OrderBudget::getId).containsExactly(1L);
    assertThat(selectable.notFittingId()).isEqualTo(1L);
  }

  /** A plan that fits is not marked — the mark says "this one cannot be saved as it stands". */
  @Test
  public void marks_nothing_where_the_held_plan_still_fits() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, true));

    assertThat(service.getSelectablePlans("co", null, TODAY, null, 1L).notFittingId()).isNull();
  }

  /** The kept plan is appended, so the ones that can be picked come first. */
  @Test
  public void appends_the_held_plan_behind_the_ones_that_fit() {
    givenPlans(plan(1L, "co", null, TODAY.minusYears(3), YESTERDAY, true),
        plan(2L, "co", null, TODAY, OPEN_END, true));

    assertThat(service.getSelectablePlans("co", null, TODAY, null, 1L).plans())
        .extracting(OrderBudget::getId).containsExactly(2L, 1L);
  }

  /** Switching the customer order drops the plan with it — the way it drops the suborder. */
  @Test
  public void drops_a_held_plan_that_belongs_to_another_order() {
    givenPlans(plan(1L, "other", null, TODAY, OPEN_END, true));

    var selectable = service.getSelectablePlans("co", null, TODAY, null, 1L);

    assertThat(selectable.plans()).isEmpty();
    assertThat(selectable.notFittingId()).isNull();
  }

  /** Authorization is no narrowing condition but a boundary, and a boundary has no exceptions. */
  @Test
  public void does_not_keep_a_held_plan_the_user_may_not_see() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, true));
    when(budgetAuthorization.isAuthorized(any())).thenReturn(false);

    assertThat(service.getSelectablePlans("co", null, TODAY, null, 1L).plans()).isEmpty();
  }

  @Test
  public void does_not_offer_a_plan_the_user_may_not_see() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, true));
    when(budgetAuthorization.isAuthorized(any())).thenReturn(false);

    assertThat(plansFor("co", null)).isEmpty();
  }

  @Test
  public void refuses_a_plan_that_does_not_exist_at_all() {
    when(orderBudgetRepository.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.save(dataWithPlan("co", null, 9L)))
        .isInstanceOf(InvalidDataException.class)
        .extracting(e -> errorCodeOf((ErrorCodeException) e))
        .isEqualTo(ErrorCode.BU_BUDGET_NOT_FOUND);
  }

  /** Two rates differing only in their plan are no conflict — the query carries the plan now. */
  @Test
  public void asks_for_overlaps_with_the_plan_in_the_key() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, true));

    service.save(dataWithPlan("co", null, 1L));

    verify(orderPricingRepository).findOverlapping("co", null, null, 1L, TODAY, OPEN_END, null);
  }

  private List<OrderBudget> plansFor(String customerorderSign, String suborderPattern) {
    return service.getSelectablePlans(customerorderSign, suborderPattern, TODAY, null, null).plans();
  }

  /**
   * The query is by customer order, so a plan of another order is simply not among the answers —
   * stubbing it per sign keeps the test from claiming a reach the repository does not have.
   */
  private void givenPlans(OrderBudget... plans) {
    when(orderBudgetRepository.findByCustomerorderSign(any())).thenAnswer(invocation ->
        List.of(plans).stream()
            .filter(plan -> plan.getCustomerorderSign().equals(invocation.getArgument(0)))
            .toList());
    for (var plan : plans) {
      when(orderBudgetRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
    }
  }

  private void givenSuborders(String... completeOrderSigns) {
    var order = new Customerorder();
    setId(order, 1L);
    when(customerorderService.getCustomerorderBySign(any())).thenReturn(order);
    // The pattern check of #958 runs first and is not what these tests are about.
    when(suborderService.existsSuborderMatching(any(), any())).thenReturn(true);
    when(suborderService.getSubordersByCustomerorderId(1L))
        .thenReturn(List.of(completeOrderSigns).stream().map(OrderPricingServiceTest::suborder).toList());
  }

  /** {@code getCompleteOrderSign()} walks the parent chain, so the sign is built from real records. */
  private static Suborder suborder(String completeOrderSign) {
    var parts = completeOrderSign.split("/");
    var customerorder = new Customerorder();
    customerorder.setSign(parts[0]);
    Suborder suborder = null;
    for (int i = 1; i < parts.length; i++) {
      var next = new Suborder();
      next.setCustomerorder(customerorder);
      next.setParentorder(suborder);
      next.setSign(parts[i]);
      suborder = next;
    }
    return suborder;
  }

  private static OrderBudget plan(long id, String customerorderSign, String suborderSign,
                                  LocalDate validFrom, LocalDate validUntil, boolean active) {
    var plan = new OrderBudget();
    setId(plan, id);
    plan.setName("plan " + id);
    plan.setCustomerorderSign(customerorderSign);
    plan.setSuborderSign(suborderSign);
    plan.setValidFrom(validFrom);
    plan.setValidUntil(validUntil);
    plan.setActive(active);
    return plan;
  }

  private static OrderPricingData dataWithPlan(String customerorderSign, String suborderSign, Long planId) {
    return new OrderPricingData(customerorderSign, suborderSign, null, planId, null, 10000, TODAY, null);
  }

  private static ErrorCode errorCodeOf(ErrorCodeException ex) {
    return ex.getMessages().get(0).getErrorCode();
  }

}
