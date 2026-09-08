package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.BudgetMode;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetData;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.common.test.FixedClock;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.ErrorCode;
import org.tb.order.service.SuborderService;

/**
 * At any point in time a customer order is budgeted either as a whole — by exactly one plan — or per
 * first level suborder, by at most one plan each, never both (#905). Only active plans conflict.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderBudgetServiceTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);

  private OrderBudgetRepository orderBudgetRepository;
  private OrderBudgetService service;
  private BudgetAuthorization budgetAuthorization;

  @BeforeEach
  public void setUp() {
    orderBudgetRepository = mock(OrderBudgetRepository.class);
    var suborderService = mock(SuborderService.class);
    when(suborderService.existsByCompleteOrderSign(anyString(), anyString())).thenReturn(true);
    when(suborderService.isFirstLevelSuborder(anyString(), anyString())).thenReturn(true);
    budgetAuthorization = permissiveAuthorization();
    service = new OrderBudgetService(orderBudgetRepository, suborderService, budgetAuthorization);
  }

  /** These tests are about the budget rules, so authorization lets everything through. */
  private static BudgetAuthorization permissiveAuthorization() {
    var authorization = mock(BudgetAuthorization.class);
    when(authorization.seesAllCustomerorders()).thenReturn(true);
    when(authorization.isAuthorized(any(OrderBudget.class))).thenReturn(true);
    when(authorization.isAuthorizedForCustomerorder(anyString())).thenReturn(true);
    return authorization;
  }

  /**
   * Overlapping plans of the same mode are allowed since #914 — a follow-up order that starts
   * before the running budget ends is the normal case. The ban only ever existed because the plan
   * of a booking was derived from (suborder, date); the explicit assignment decides it now.
   */
  @Test
  public void should_accept_a_second_order_wide_plan_for_an_overlapping_period() {
    givenExisting(plan(null, JAN, DEC));

    assertThatCode(() -> service.create(data(null, JAN, JUN, true))).doesNotThrowAnyException();
  }

  /** The one rule left: never both modes at the same time. */
  @Test
  public void should_reject_a_suborder_plan_next_to_an_order_wide_plan_in_the_same_period() {
    givenExisting(plan(null, JAN, DEC));

    assertThatThrownBy(() -> service.create(data("co/01", JAN, JUN, true)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_BUDGET_LEVEL_MIXED.getCode());
  }

  /** And the other way round: an order-wide plan next to an existing suborder plan. */
  @Test
  public void should_reject_an_order_wide_plan_next_to_a_suborder_plan_in_the_same_period() {
    givenExisting(plan("co/01", JAN, DEC));

    assertThatThrownBy(() -> service.create(data(null, JAN, JUN, true)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_BUDGET_LEVEL_MIXED.getCode());
  }

  /**
   * The rejection has to say which plan stands in the way and over which period — that is the plan
   * whose dates have to move. The arguments travel on the feedback message, which is what
   * {@code ErrorCodeViewHelper} formats into the text the user reads.
   */
  @Test
  public void should_name_the_plan_that_blocks_a_mode_change() {
    var blocking = plan(null, JAN, DEC);
    blocking.setName("Jahresbudget");
    givenExisting(blocking);

    var thrown = catchThrowableOfType(BusinessRuleException.class,
        () -> service.create(data("co/01", JAN, JUN, true)));

    assertThat(thrown.getMessages()).singleElement()
        .satisfies(message -> {
          assertThat(message.getErrorCode()).isEqualTo(ErrorCode.BU_BUDGET_LEVEL_MIXED);
          assertThat(message.getArguments()).containsExactly("Jahresbudget", JAN, DEC);
        });
  }

  /** Several plans on the same suborder are allowed too — splitting a budget is a decision. */
  @Test
  public void should_accept_a_second_plan_for_the_same_suborder_in_an_overlapping_period() {
    givenExisting(plan("co/01", JAN, DEC));

    assertThatCode(() -> service.create(data("co/01", JAN, JUN, true))).doesNotThrowAnyException();
  }

  @Test
  public void should_accept_plans_for_different_suborders_in_the_same_period() {
    givenExisting(plan("co/01", JAN, DEC));

    assertThatCode(() -> service.create(data("co/02", JAN, DEC, true))).doesNotThrowAnyException();
  }

  /** Switching the budgeting level at the turn of the year stays possible. */
  @Test
  public void should_accept_a_suborder_plan_in_a_period_the_order_wide_plan_does_not_cover() {
    givenExisting(plan(null, JAN, JUN));

    assertThatCode(() -> service.create(data("co/01", JUL, DEC, true))).doesNotThrowAnyException();
  }

  @Test
  public void should_ignore_inactive_plans() {
    var archived = plan(null, JAN, DEC);
    archived.setActive(false);
    givenExisting(archived);

    assertThatCode(() -> service.create(data(null, JAN, DEC, true))).doesNotThrowAnyException();
  }

  @Test
  public void should_not_check_a_plan_that_is_saved_as_inactive() {
    givenExisting(plan(null, JAN, DEC));

    assertThatCode(() -> service.create(data(null, JAN, JUN, false))).doesNotThrowAnyException();
  }

  /**
   * An archived plan can be activated again, and only then does its mode start to count — so the
   * rule has to be checked at that moment as well.
   */
  @Test
  public void should_reject_activating_a_plan_that_would_mix_the_modes() {
    var stored = plan("co/01", JAN, JUN, 7L);
    stored.setActive(false);
    when(orderBudgetRepository.findById(7L)).thenReturn(Optional.of(stored));
    givenExisting(stored, plan(null, JAN, DEC));

    assertThatThrownBy(() -> service.setActive(7L, true))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_BUDGET_LEVEL_MIXED.getCode());
  }

  /** Activating a plan that only overlaps plans of its own mode is fine. */
  @Test
  public void should_accept_activating_a_plan_that_overlaps_its_own_mode() {
    var stored = plan(null, JAN, JUN, 7L);
    stored.setActive(false);
    when(orderBudgetRepository.findById(7L)).thenReturn(Optional.of(stored));
    givenExisting(stored, plan(null, JAN, DEC));

    assertThatCode(() -> service.setActive(7L, true)).doesNotThrowAnyException();
    assertThat(stored.getActive()).isTrue();
  }

  @Test
  public void should_let_a_plan_be_deactivated_regardless_of_conflicts() {
    var stored = plan(null, JAN, JUN);
    when(orderBudgetRepository.findById(7L)).thenReturn(Optional.of(stored));

    assertThatCode(() -> service.setActive(7L, false)).doesNotThrowAnyException();
    assertThat(stored.getActive()).isFalse();
  }

  /** On update the plan must not conflict with itself; the repository excludes it by id. */
  @Test
  public void should_not_let_a_plan_conflict_with_itself_on_update() {
    var stored = plan(null, JAN, DEC, 7L);
    when(orderBudgetRepository.findById(7L)).thenReturn(Optional.of(stored));
    givenExisting(stored); // the only overlapping plan is the one being edited

    assertThatCode(() -> service.update(7L, data(null, JAN, DEC, true))).doesNotThrowAnyException();
  }

  @Test
  public void should_reject_a_plan_on_a_deeper_suborder() {
    var suborderService = mock(SuborderService.class);
    when(suborderService.existsByCompleteOrderSign(anyString(), anyString())).thenReturn(true);
    when(suborderService.isFirstLevelSuborder("co", "co/01/02")).thenReturn(false);
    service = new OrderBudgetService(orderBudgetRepository, suborderService, budgetAuthorization);
    givenExisting();

    assertThatThrownBy(() -> service.create(data("co/01/02", JAN, DEC, true)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_SUBORDER_NOT_FIRST_LEVEL.getCode());
  }

  /**
   * Only managers and backoffice see every order; an order responsible sees the plans of their own
   * orders (#919). The service is the layer that enforces it, so that the guard also holds for the
   * write paths that all load their plan through {@code getById}.
   */
  @Test
  public void should_reject_reading_a_plan_of_an_unauthorized_order() {
    var plan = plan(null, JAN, DEC, 7L);
    when(orderBudgetRepository.findById(7L)).thenReturn(Optional.of(plan));
    when(budgetAuthorization.isAuthorized(plan)).thenReturn(false);
    doThrow(new AuthorizationException(ErrorCode.BU_ORDER_NOT_AUTHORIZED, "co"))
        .when(budgetAuthorization).checkAuthorized(plan);

    assertThatThrownBy(() -> service.getById(7L))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(ErrorCode.BU_ORDER_NOT_AUTHORIZED.getCode());
  }

  @Test
  public void should_list_only_the_plans_of_authorized_orders() {
    var own = plan(null, JAN, DEC, 1L);
    var foreign = plan(null, JAN, DEC, 2L);
    foreign.setCustomerorderSign("other-co");
    when(orderBudgetRepository.findAllActiveWithAdjustments()).thenReturn(List.of(own, foreign));
    when(budgetAuthorization.seesAllCustomerorders()).thenReturn(false);
    when(budgetAuthorization.isAuthorized(own)).thenReturn(true);
    when(budgetAuthorization.isAuthorized(foreign)).thenReturn(false);

    assertThat(service.getAllActiveVisible()).containsExactly(own);
  }

  /**
   * Stands in for {@code findActiveOverlapping} and mirrors its JPQL: active only, periods
   * intersecting inclusively, own id excluded. Stubbing a fixed list instead would hand the service
   * plans the query would never have returned, and the period rules would go untested.
   */
  /** The mode in force today, for the badge in the list and the hint in the form (#914). */
  @Test
  @FixedClock("2026-03-15T10:00:00")
  public void should_report_the_mode_in_force_today() {
    when(orderBudgetRepository.findByCustomerorderSignAndActive("co", Boolean.TRUE))
        .thenReturn(List.of(plan(null, JAN, DEC)));

    assertThat(service.currentMode("co")).isEqualTo(BudgetMode.ORDER_WIDE);
  }

  @Test
  @FixedClock("2026-03-15T10:00:00")
  public void should_report_the_suborder_mode_when_that_is_what_applies() {
    when(orderBudgetRepository.findByCustomerorderSignAndActive("co", Boolean.TRUE))
        .thenReturn(List.of(plan("co/01", JAN, DEC)));

    assertThat(service.currentMode("co")).isEqualTo(BudgetMode.PER_SUBORDER);
  }

  /** A plan whose period has passed says nothing about today. */
  @Test
  @FixedClock("2026-09-15T10:00:00")
  public void should_report_no_mode_when_no_active_plan_covers_today() {
    when(orderBudgetRepository.findByCustomerorderSignAndActive("co", Boolean.TRUE))
        .thenReturn(List.of(plan(null, JAN, JUN)));

    assertThat(service.currentMode("co")).isEqualTo(BudgetMode.NONE);
  }

  private void givenExisting(OrderBudget... plans) {
    when(orderBudgetRepository.findActiveOverlapping(anyString(), any(), any(), any()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(1);
          LocalDate until = invocation.getArgument(2);
          Long excludeId = invocation.getArgument(3);
          return Arrays.stream(plans)
              .filter(p -> Boolean.TRUE.equals(p.getActive()))
              .filter(p -> !p.getValidFrom().isAfter(until) && !p.getValidUntil().isBefore(from))
              .filter(p -> excludeId == null || !excludeId.equals(p.getId()))
              .toList();
        });
  }

  private static OrderBudgetData data(String suborderSign, LocalDate from, LocalDate until, boolean active) {
    return new OrderBudgetData("plan", "co", suborderSign, from, until, active, null, null);
  }

  private static OrderBudget plan(String suborderSign, LocalDate from, LocalDate until) {
    return plan(suborderSign, from, until, null);
  }

  /**
   * The dashboard hands its filter result down as a restriction. An empty one means "no order
   * matched" and must not reach the repository: {@code IN ()} is not valid SQL, and asking without
   * the restriction would list everything instead of nothing (#920).
   */
  @Test
  public void an_empty_restriction_yields_no_plans_and_no_query() {
    assertThat(service.getAllActiveVisible(List.of())).isEmpty();

    verify(orderBudgetRepository, never()).findAllActiveWithAdjustmentsBySigns(any());
    verify(orderBudgetRepository, never()).findAllActiveWithAdjustments();
  }

  @Test
  public void a_missing_restriction_asks_for_every_active_plan() {
    service.getAllActiveVisible(null);

    verify(orderBudgetRepository).findAllActiveWithAdjustments();
    verify(orderBudgetRepository, never()).findAllActiveWithAdjustmentsBySigns(any());
  }

  @Test
  public void a_restriction_is_passed_on_to_the_query() {
    service.getAllActiveVisible(List.of("co", "other"));

    verify(orderBudgetRepository).findAllActiveWithAdjustmentsBySigns(List.of("co", "other"));
    verify(orderBudgetRepository, never()).findAllActiveWithAdjustments();
  }

  private static OrderBudget plan(String suborderSign, LocalDate from, LocalDate until, Long id) {
    var budget = new OrderBudget();
    budget.setCustomerorderSign("co");
    budget.setSuborderSign(suborderSign);
    budget.setActive(true);
    budget.setValidFrom(from);
    budget.setValidUntil(until);
    if (id != null) {
      setId(budget, id);
    }
    return budget;
  }

  /** The id is generated, so there is no setter; a stored plan always has one. */
  private static void setId(OrderBudget budget, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(budget, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test plan", e);
    }
  }

}
