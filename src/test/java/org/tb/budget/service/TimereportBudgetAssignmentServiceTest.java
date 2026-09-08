package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.TimereportBudgetAssignment;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.SuborderService;

/**
 * Only the stored assignment counts — a booking without one belongs to no budget (#908). The
 * rules that guard it are therefore the only thing standing between a deliberate assignment and a
 * booking counted against a plan it does not belong to.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class TimereportBudgetAssignmentServiceTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final LocalDate MAR = LocalDate.of(2026, 3, 15);

  private final List<TimereportBudgetAssignment> stored = new ArrayList<>();

  private TimereportBudgetAssignmentRepository assignmentRepository;
  private OrderBudgetService orderBudgetService;
  private TimereportService timereportService;
  private AuthorizedUser authorizedUser;
  private TimereportBudgetAssignmentService service;

  @BeforeEach
  public void setUp() {
    assignmentRepository = mock(TimereportBudgetAssignmentRepository.class);
    orderBudgetService = mock(OrderBudgetService.class);
    timereportService = mock(TimereportService.class);
    var suborderService = mock(SuborderService.class);
    authorizedUser = mock(AuthorizedUser.class);
    when(authorizedUser.isManager()).thenReturn(true);

    when(assignmentRepository.findByTimereportId(anyLong())).thenAnswer(invocation ->
        stored.stream().filter(a -> a.getTimereportId().equals(invocation.<Long>getArgument(0))).findFirst());
    when(assignmentRepository.save(any())).thenAnswer(invocation -> {
      TimereportBudgetAssignment saved = invocation.getArgument(0);
      if (!stored.contains(saved)) {
        stored.add(saved);
      }
      return saved;
    });
    // The suborder tree the reports live in: CO/01 with CO/01/02 below it, plus CO/02.
    when(suborderService.getSuborderById(1L)).thenReturn(firstLevel("CO", "01"));
    when(suborderService.getSuborderById(2L)).thenReturn(below(firstLevel("CO", "01"), "02"));
    when(suborderService.getSuborderById(3L)).thenReturn(firstLevel("CO", "02"));

    service = new TimereportBudgetAssignmentService(
        assignmentRepository, orderBudgetService, timereportService, suborderService, authorizedUser);
  }

  // --- assigning ------------------------------------------------------------------------------

  @Test
  public void should_assign_a_booking_to_an_order_wide_plan_of_its_order() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR);

    service.assign(100L, 7L);

    assertThat(stored).hasSize(1);
    assertThat(stored.get(0).getTimereportId()).isEqualTo(100L);
    assertThat(stored.get(0).getOrderBudget().getId()).isEqualTo(7L);
  }

  @Test
  public void should_assign_a_booking_to_a_plan_on_its_own_first_level_suborder() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR);

    assertThatCode(() -> service.assign(100L, 7L)).doesNotThrowAnyException();
  }

  /** Plans only live on the first level, bookings happen further down — those must still fit. */
  @Test
  public void should_assign_a_booking_below_the_plans_first_level_suborder() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);
    givenReport(100L, "CO", 2L, MAR);

    assertThatCode(() -> service.assign(100L, 7L)).doesNotThrowAnyException();
  }

  @Test
  public void should_reject_a_booking_under_a_different_suborder() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);
    givenReport(100L, "CO", 3L, MAR);

    assertThatThrownBy(() -> service.assign(100L, 7L))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_TIMEREPORT_NOT_IN_BUDGET_SCOPE.getCode());
    assertThat(stored).isEmpty();
  }

  @Test
  public void should_reject_a_booking_of_a_different_customer_order() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "OTHER", 1L, MAR);

    assertThatThrownBy(() -> service.assign(100L, 7L))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_TIMEREPORT_NOT_IN_BUDGET_SCOPE.getCode());
  }

  @Test
  public void should_reject_a_booking_dated_after_the_plans_validity() {
    givenPlan(7L, "CO", null, JAN, JUN, true);
    givenReport(100L, "CO", 1L, DEC);

    assertThatThrownBy(() -> service.assign(100L, 7L))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_TIMEREPORT_OUTSIDE_BUDGET_PERIOD.getCode());
  }

  @Test
  public void should_reject_a_booking_dated_before_the_plans_validity() {
    givenPlan(7L, "CO", null, JUN, DEC, true);
    givenReport(100L, "CO", 1L, MAR);

    assertThatThrownBy(() -> service.assign(100L, 7L))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_TIMEREPORT_OUTSIDE_BUDGET_PERIOD.getCode());
  }

  /** The boundaries belong to the period. */
  @Test
  public void should_accept_a_booking_on_the_first_and_last_day_of_the_validity() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, JAN);
    givenReport(101L, "CO", 1L, DEC);

    assertThatCode(() -> service.assign(100L, 7L)).doesNotThrowAnyException();
    assertThatCode(() -> service.assign(101L, 7L)).doesNotThrowAnyException();
  }

  @Test
  public void should_reject_assigning_to_an_inactive_plan() {
    givenPlan(7L, "CO", null, JAN, DEC, false);
    givenReport(100L, "CO", 1L, MAR);

    assertThatThrownBy(() -> service.assign(100L, 7L))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_BUDGET_INACTIVE.getCode());
  }

  @Test
  public void should_reject_assigning_a_booking_that_does_not_exist() {
    givenPlan(7L, "CO", null, JAN, DEC, true);

    assertThatThrownBy(() -> service.assign(999L, 7L))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.TR_TIME_REPORT_NOT_FOUND.getCode());
  }

  /** Correcting an assignment keeps the row, so its audit trail survives. */
  @Test
  public void should_retarget_an_existing_assignment_instead_of_replacing_it() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenPlan(8L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR);

    service.assign(100L, 7L);
    var first = stored.get(0);
    service.assign(100L, 8L);

    assertThat(stored).hasSize(1);
    assertThat(stored.get(0)).isSameAs(first);
    assertThat(stored.get(0).getOrderBudget().getId()).isEqualTo(8L);
    verify(assignmentRepository, never()).delete(any());
  }

  @Test
  public void should_leave_the_assignment_untouched_when_the_new_plan_is_rejected() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenPlan(8L, "CO", null, JAN, JUN, true);
    givenReport(100L, "CO", 1L, DEC);

    service.assign(100L, 7L);
    assertThatThrownBy(() -> service.assign(100L, 8L)).isInstanceOf(BusinessRuleException.class);

    assertThat(stored.get(0).getOrderBudget().getId()).isEqualTo(7L);
  }

  // --- unassigning and reading ----------------------------------------------------------------

  @Test
  public void should_unassign_a_booking() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR);
    service.assign(100L, 7L);

    service.unassign(100L);

    verify(assignmentRepository).delete(stored.get(0));
  }

  @Test
  public void unassigning_a_booking_without_an_assignment_is_not_an_error() {
    assertThatCode(() -> service.unassign(100L)).doesNotThrowAnyException();
    verify(assignmentRepository, never()).delete(any());
  }

  @Test
  public void should_report_the_plan_a_booking_belongs_to() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR);
    service.assign(100L, 7L);

    assertThat(service.getAssignedBudgetId(100L)).contains(7L);
  }

  @Test
  public void should_report_no_plan_for_an_unassigned_booking() {
    assertThat(service.getAssignedBudgetId(100L)).isEmpty();
  }

  /** Reading a plan's bookings goes through the plan, so its authorization check runs. */
  @Test
  public void reading_the_bookings_of_a_plan_checks_access_to_the_plan() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    when(assignmentRepository.findTimereportIdsByOrderBudgetId(7L)).thenReturn(List.of(100L, 101L));

    assertThat(service.getAssignedTimereportIds(7L)).containsExactly(100L, 101L);
    verify(orderBudgetService).getById(7L);
  }

  // --- authorization --------------------------------------------------------------------------

  @Test
  public void should_reject_assigning_without_manager_rights() {
    when(authorizedUser.isManager()).thenReturn(false);
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR);

    assertThatThrownBy(() -> service.assign(100L, 7L))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(ErrorCode.AA_NEEDS_MANAGER.getCode());
    assertThat(stored).isEmpty();
  }

  @Test
  public void should_reject_unassigning_without_manager_rights() {
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> service.unassign(100L))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(ErrorCode.AA_NEEDS_MANAGER.getCode());
  }

  // --- cleanup after a delete -----------------------------------------------------------------

  /**
   * The cleanup runs for whoever deleted the booking, not only for managers — the booking's own
   * owner may delete it, and no assignment may outlive its booking.
   */
  @Test
  public void should_remove_assignments_of_deleted_bookings_regardless_of_the_deleters_rights() {
    when(authorizedUser.isManager()).thenReturn(false);

    service.removeAssignmentsOfDeletedTimereports(List.of(100L, 101L));

    verify(assignmentRepository).deleteByTimereportIdIn(List.of(100L, 101L));
  }

  /** An empty {@code IN ()} is not valid SQL, so the query must not be issued at all. */
  @Test
  public void should_not_query_when_no_booking_was_deleted() {
    service.removeAssignmentsOfDeletedTimereports(List.of());

    verify(assignmentRepository, never()).deleteByTimereportIdIn(anyCollection());
  }

  // --- test fixture ---------------------------------------------------------------------------

  private void givenPlan(long id, String customerorderSign, String suborderSign,
                         LocalDate validFrom, LocalDate validUntil, boolean active) {
    var plan = new OrderBudget();
    plan.setName("plan-" + id);
    plan.setCustomerorderSign(customerorderSign);
    plan.setSuborderSign(suborderSign);
    plan.setValidFrom(validFrom);
    plan.setValidUntil(validUntil);
    plan.setActive(active);
    setId(plan, id);
    when(orderBudgetService.getById(id)).thenReturn(plan);
  }

  private void givenReport(long id, String customerorderSign, long suborderId, LocalDate day) {
    when(timereportService.getTimereportById(id)).thenReturn(TimereportDTO.builder()
        .id(id)
        .customerorderSign(customerorderSign)
        .suborderId(suborderId)
        .completeOrderSign(customerorderSign + "/xx")
        .referenceday(day)
        .build());
  }

  private static Suborder firstLevel(String orderSign, String sign) {
    var order = new Customerorder();
    order.setSign(orderSign);
    var suborder = new Suborder();
    suborder.setSign(sign);
    suborder.setCustomerorder(order);
    return suborder;
  }

  private static Suborder below(Suborder parent, String sign) {
    var suborder = new Suborder();
    suborder.setSign(sign);
    suborder.setCustomerorder(parent.getCustomerorder());
    suborder.setParentorder(parent);
    return suborder;
  }

  /** The id is generated, so there is no setter; a stored plan always has one. */
  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test plan", e);
    }
  }

}
