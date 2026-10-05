package de.hbt.salat.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderPricing;
import de.hbt.salat.budget.domain.OrderPricingData;
import de.hbt.salat.budget.domain.OrderPricingRow;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.OrderPricingRepository;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

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
  private static final long EMP = 1L;
  private static final long GHOST = 99L;

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
    suborderService = mock(SuborderService.class);
    // The orders of the rates are the ones of the tree, read by id (#1212).
    TREE.stub(customerorderService, suborderService);
    employeeService = mock(EmployeeService.class);
    when(employeeService.getEmployeeById(EMP)).thenReturn(employee(EMP, "emp"));
    orderBudgetRepository = mock(OrderBudgetRepository.class);
    when(orderBudgetRepository.findByCustomerorderId(any())).thenReturn(List.of());
    budgetAuthorization = mock(BudgetAuthorization.class);
    when(budgetAuthorization.isAuthorized(any())).thenReturn(true);
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
    givenChosen("co-one", chosen);

    assertThat(pricingsOf(service.getRows(TREE.orderId("co-one"), false, true))).containsExactly(chosen);
  }

  /** By the sign the order has today, read by id (#1212). */
  @Test
  public void lists_the_rates_by_the_sign_their_order_has_today() {
    var onOther = pricing("other", TODAY, OPEN_END);
    var onCo = pricing("co", TODAY, OPEN_END);
    given(onOther, onCo);

    assertThat(pricingsOf(service.getRows(null, true, true))).containsExactly(onCo, onOther);
  }

  /** Both filters apply at once — picking an order does not bring its expired rates back. */
  @Test
  public void leaves_out_the_expired_rates_of_the_chosen_customer_order() {
    var expired = pricing("co-one", TODAY.minusYears(1), YESTERDAY);
    var current = pricing("co-one", TODAY, OPEN_END);
    givenChosen("co-one", expired, current);

    assertThat(pricingsOf(service.getRows(TREE.orderId("co-one"), false, true))).containsExactly(current);
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

  // --- the person a rate references (#958, #968) ---------------------------------------------

  /**
   * The form protects the person only as long as the input comes from its select. A post with
   * another id reaches the same endpoint; the foreign key would refuse it too, but only as a failed
   * statement.
   */
  @Test
  public void should_reject_a_new_rate_for_an_employee_that_does_not_exist() {
    assertThatThrownBy(() -> service.save(data("co", null, GHOST)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.EM_NOT_FOUND.getCode());
    verify(orderPricingRepository, never()).save(any());
  }

  /** The person is stored by id (#968). */
  @Test
  public void should_store_the_person_by_id() {
    service.save(data("co", null, EMP));

    var saved = ArgumentCaptor.forClass(OrderPricing.class);
    verify(orderPricingRepository).save(saved.capture());
    assertThat(saved.getValue().getEmployeeId()).isEqualTo(EMP);
  }

  /** No employee at all is the normal case: the rate then applies to everyone on the order. */
  @Test
  public void should_not_ask_for_an_employee_when_the_rate_names_none() {
    service.save(data("co", null, null));

    verify(employeeService, never()).getEmployeeById(anyLong());
    var saved = ArgumentCaptor.forClass(OrderPricing.class);
    verify(orderPricingRepository).save(saved.capture());
    assertThat(saved.getValue().isForEveryone()).isTrue();
  }

  @Test
  public void should_reject_a_new_rate_for_a_customer_order_that_does_not_exist() {
    assertThatThrownBy(() -> service.save(new OrderPricingData(OrderTree.UNKNOWN_ID, null, null, null, null, 10000,
        TODAY, null)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.CO_NOT_FOUND.getCode());
    verify(orderPricingRepository, never()).save(any());
  }

  /** The order is referenced by id (#1212). */
  @Test
  public void should_store_the_order_by_id() {
    service.save(data("co", null, null));

    var saved = ArgumentCaptor.forClass(OrderPricing.class);
    verify(orderPricingRepository).save(saved.capture());
    assertThat(saved.getValue().getCustomerorderId()).isEqualTo(TREE.orderId("co"));
  }

  /** An edit names its order like a new rate does — the form cannot submit a rate without one. */
  @Test
  public void should_reject_an_edit_without_an_order() {
    var edited = pricing("co", TODAY.minusYears(1), OPEN_END);
    setId(edited, 5L);
    when(orderPricingRepository.findById(5L)).thenReturn(Optional.of(edited));

    assertThatThrownBy(() -> service.update(5L, new OrderPricingData(null, null, null, null, null, 10000, TODAY, null)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.CO_NOT_FOUND.getCode());
    verify(orderPricingRepository, never()).save(any());
  }

  @Test
  public void should_reject_an_edit_that_moves_a_rate_to_an_unknown_employee() {
    var edited = pricing("co", TODAY.minusYears(1), OPEN_END);
    setId(edited, 6L);
    when(orderPricingRepository.findById(6L)).thenReturn(Optional.of(edited));

    assertThatThrownBy(() -> service.update(6L, data("co", null, GHOST)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.EM_NOT_FOUND.getCode());
    verify(orderPricingRepository, never()).save(any());
  }

  /** The list shows the person's current sign. */
  @Test
  public void shows_the_current_sign_of_the_person() {
    when(employeeService.getSignsByIds(Set.of(EMP))).thenReturn(Map.of(EMP, "emp"));
    var pricing = pricingFor(EMP);
    given(pricing);

    assertThat(service.getRows(null, false, true)).singleElement().satisfies(row -> {
      assertThat(row.employeeSign()).isEqualTo("emp");
    });
  }

  /** A rate without an employee applies to everyone on the order and shows no sign. */
  @Test
  public void shows_no_sign_for_a_rate_that_names_no_employee() {
    given(pricingFor(null));

    assertThat(service.getRows(null, false, true)).singleElement().satisfies(row -> {
      assertThat(row.employeeSign()).isNull();
    });
  }

  private static OrderPricing pricingFor(Long employeeId) {
    var pricing = pricing("co", TODAY.minusYears(1), OPEN_END);
    pricing.setEmployeeId(employeeId);
    return pricing;
  }

  private static Employee employee(long id, String sign) {
    var employee = new Employee();
    setId(employee, id);
    employee.setSign(sign);
    return employee;
  }

  private static OrderPricingData data(String customerorderSign, String suborderSign, Long employeeId) {
    return new OrderPricingData(TREE.orderId(customerorderSign), suborderSign, employeeId, null, null, 10000, TODAY,
        null);
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

  /** The order of the rates with its validity — a copy, so that the shared tree stays as it is. */
  private void givenOrder(String sign, LocalDate fromDate, LocalDate untilDate) {
    var order = new Customerorder();
    setId(order, TREE.orderId(sign));
    order.setSign(sign);
    order.setFromDate(fromDate);
    order.setUntilDate(untilDate);
    // doReturn: when(...) would call the tree's answer with a null argument first.
    doReturn(List.of(order)).when(customerorderService).getCustomerordersByIds(any());
  }

  private static List<OrderPricing> pricingsOf(List<OrderPricingRow> rows) {
    return rows.stream().map(OrderPricingRow::pricing).toList();
  }

  private void given(OrderPricing... pricings) {
    when(orderPricingRepository.findAll()).thenReturn(List.of(pricings));
  }

  /** The filter names the order by sign; the rates are read by the id behind it (#1212). */
  private void givenChosen(String customerorderSign, OrderPricing... pricings) {
    when(orderPricingRepository.findByCustomerorderIdOrderByValidFromAsc(TREE.orderId(customerorderSign)))
        .thenReturn(List.of(pricings));
  }

  private static OrderPricing pricing(String customerorderSign, LocalDate validFrom, LocalDate validUntil) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderId(TREE.orderId(customerorderSign));
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

    assertThat(service.getSelectablePlans(TREE.orderId("co"), null, null, null, null).plans())
        .extracting(OrderBudget::getId).containsExactly(1L);
  }

  /** …and narrows again the moment it is. */
  @Test
  public void narrows_the_plans_once_the_validity_is_entered() {
    givenPlans(plan(1L, "co", null, TODAY.minusYears(3), YESTERDAY, true));

    assertThat(service.getSelectablePlans(TREE.orderId("co"), null, TODAY, null, null).plans()).isEmpty();
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

    assertThat(service.getSelectablePlans(TREE.orderId("co"), null, TODAY, null, null).plans()).isEmpty();
  }

  /** …but the one the rate already stores stays, or the next save would silently drop it. */
  @Test
  public void keeps_the_stored_plan_in_the_list_even_once_it_is_inactive() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, false));

    var selectable = service.getSelectablePlans(TREE.orderId("co"), null, TODAY, null, 1L);

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

    var selectable = service.getSelectablePlans(TREE.orderId("co"), null, TODAY, null, 1L);

    assertThat(selectable.plans()).extracting(OrderBudget::getId).containsExactly(1L);
    assertThat(selectable.notFittingId()).isEqualTo(1L);
  }

  @Test
  public void keeps_the_held_plan_when_the_scope_no_longer_fits_it() {
    givenSuborders("co/01", "co/02");
    givenPlans(plan(1L, "co", "co/02", TODAY, OPEN_END, true));

    var selectable = service.getSelectablePlans(TREE.orderId("co"), "co/01/", TODAY, null, 1L);

    assertThat(selectable.plans()).extracting(OrderBudget::getId).containsExactly(1L);
    assertThat(selectable.notFittingId()).isEqualTo(1L);
  }

  /** A plan that fits is not marked — the mark says "this one cannot be saved as it stands". */
  @Test
  public void marks_nothing_where_the_held_plan_still_fits() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, true));

    assertThat(service.getSelectablePlans(TREE.orderId("co"), null, TODAY, null, 1L).notFittingId()).isNull();
  }

  /** The kept plan is appended, so the ones that can be picked come first. */
  @Test
  public void appends_the_held_plan_behind_the_ones_that_fit() {
    givenPlans(plan(1L, "co", null, TODAY.minusYears(3), YESTERDAY, true),
        plan(2L, "co", null, TODAY, OPEN_END, true));

    assertThat(service.getSelectablePlans(TREE.orderId("co"), null, TODAY, null, 1L).plans())
        .extracting(OrderBudget::getId).containsExactly(2L, 1L);
  }

  /** Switching the customer order drops the plan with it — the way it drops the suborder. */
  @Test
  public void drops_a_held_plan_that_belongs_to_another_order() {
    givenPlans(plan(1L, "other", null, TODAY, OPEN_END, true));

    var selectable = service.getSelectablePlans(TREE.orderId("co"), null, TODAY, null, 1L);

    assertThat(selectable.plans()).isEmpty();
    assertThat(selectable.notFittingId()).isNull();
  }

  /** Authorization is no narrowing condition but a boundary, and a boundary has no exceptions. */
  @Test
  public void does_not_keep_a_held_plan_the_user_may_not_see() {
    givenPlans(plan(1L, "co", null, TODAY, OPEN_END, true));
    when(budgetAuthorization.isAuthorized(any())).thenReturn(false);

    assertThat(service.getSelectablePlans(TREE.orderId("co"), null, TODAY, null, 1L).plans()).isEmpty();
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

    verify(orderPricingRepository).findOverlapping(TREE.orderId("co"), null, null, 1L, TODAY, OPEN_END, null);
  }

  private List<OrderBudget> plansFor(String customerorderSign, String suborderPattern) {
    return service.getSelectablePlans(TREE.orderId(customerorderSign), suborderPattern, TODAY, null, null).plans();
  }

  /**
   * The query is by customer order, so a plan of another order is simply not among the answers —
   * stubbing it per sign keeps the test from claiming a reach the repository does not have.
   */
  private void givenPlans(OrderBudget... plans) {
    when(orderBudgetRepository.findByCustomerorderId(any())).thenAnswer(invocation ->
        List.of(plans).stream()
            .filter(plan -> plan.getCustomerorderId().equals(invocation.getArgument(0)))
            .toList());
    for (var plan : plans) {
      when(orderBudgetRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
    }
  }

  private void givenSuborders(String... completeOrderSigns) {
    // The pattern check of #958 runs first and is not what these tests are about.
    when(suborderService.existsSuborderMatching(any(), any())).thenReturn(true);
    when(suborderService.getSubordersByCustomerorderId(TREE.orderId("co")))
        .thenReturn(List.of(completeOrderSigns).stream().map(TREE::suborder).toList());
  }

  /** The order of the rates and its suborders, with the ids the plans refer to (#1205). */
  private static final OrderTree TREE = new OrderTree().with("co/01/A").with("co/02").with("other/01");

  private static OrderBudget plan(long id, String customerorderSign, String suborderSign,
                                  LocalDate validFrom, LocalDate validUntil, boolean active) {
    var plan = new OrderBudget();
    setId(plan, id);
    plan.setName("plan " + id);
    plan.setCustomerorderId(TREE.orderId(customerorderSign));
    plan.setSuborderId(TREE.suborderId(suborderSign));
    plan.setValidFrom(validFrom);
    plan.setValidUntil(validUntil);
    plan.setActive(active);
    return plan;
  }

  private static OrderPricingData dataWithPlan(String customerorderSign, String suborderSign, Long planId) {
    return new OrderPricingData(TREE.orderId(customerorderSign), suborderSign, null, planId, null, 10000, TODAY, null);
  }

  private static ErrorCode errorCodeOf(ErrorCodeException ex) {
    return ex.getMessages().get(0).getErrorCode();
  }

}
