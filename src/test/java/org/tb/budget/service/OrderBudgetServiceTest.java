package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetData;
import org.tb.budget.persistence.OrderBudgetRepository;
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

  @BeforeEach
  public void setUp() {
    orderBudgetRepository = mock(OrderBudgetRepository.class);
    var suborderService = mock(SuborderService.class);
    when(suborderService.existsByCompleteOrderSign(anyString(), anyString())).thenReturn(true);
    when(suborderService.isFirstLevelSuborder(anyString(), anyString())).thenReturn(true);
    service = new OrderBudgetService(orderBudgetRepository, suborderService);
  }

  @Test
  public void should_reject_a_second_order_wide_plan_for_the_same_period() {
    givenExisting(plan(null, JAN, DEC));

    assertThatThrownBy(() -> service.create(data(null, JAN, JUN, true)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_BUDGET_OVERLAP.getCode());
  }

  @Test
  public void should_reject_a_suborder_plan_next_to_an_order_wide_plan_in_the_same_period() {
    givenExisting(plan(null, JAN, DEC));

    assertThatThrownBy(() -> service.create(data("co/01", JAN, JUN, true)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_BUDGET_LEVEL_MIXED.getCode());
  }

  @Test
  public void should_reject_a_second_plan_for_the_same_suborder_in_the_same_period() {
    givenExisting(plan("co/01", JAN, DEC));

    assertThatThrownBy(() -> service.create(data("co/01", JAN, JUN, true)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_BUDGET_OVERLAP.getCode());
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

  @Test
  public void should_reject_activating_a_plan_that_would_then_conflict() {
    var stored = plan(null, JAN, JUN, 7L);
    stored.setActive(false);
    when(orderBudgetRepository.findById(7L)).thenReturn(Optional.of(stored));
    givenExisting(stored, plan(null, JAN, DEC));

    assertThatThrownBy(() -> service.setActive(7L, true))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_BUDGET_OVERLAP.getCode());
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
    service = new OrderBudgetService(orderBudgetRepository, suborderService);
    givenExisting();

    assertThatThrownBy(() -> service.create(data("co/01/02", JAN, DEC, true)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_SUBORDER_NOT_FIRST_LEVEL.getCode());
  }

  /**
   * Stands in for {@code findActiveOverlapping} and mirrors its JPQL: active only, periods
   * intersecting inclusively, own id excluded. Stubbing a fixed list instead would hand the service
   * plans the query would never have returned, and the period rules would go untested.
   */
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
