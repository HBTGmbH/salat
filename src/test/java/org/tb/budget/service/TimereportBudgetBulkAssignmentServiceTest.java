package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.BulkAssignmentData;
import org.tb.budget.domain.BulkAssignmentEmployee;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.TimereportBudgetAssignment;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * The bulk assignment is where a person overrides what could not be decided automatically (#911).
 * It must act on exactly the selection — too wide and it silently moves bookings nobody looked at.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class TimereportBudgetBulkAssignmentServiceTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final LocalDate MAR = LocalDate.of(2026, 3, 15);
  private static final LocalDate SEP = LocalDate.of(2026, 9, 15);
  private static final Duration HOUR = Duration.ofHours(1);

  private final List<OrderBudget> plans = new ArrayList<>();
  private final List<TimereportBudgetAssignment> stored = new ArrayList<>();
  private final List<TimereportDTO> reports = new ArrayList<>();

  private TimereportBudgetAssignmentRepository assignmentRepository;
  private OrderBudgetService orderBudgetService;
  private AuthorizedUser authorizedUser;
  private TimereportBudgetBulkAssignmentService service;

  @BeforeEach
  public void setUp() {
    var orderBudgetRepository = mock(OrderBudgetRepository.class);
    assignmentRepository = mock(TimereportBudgetAssignmentRepository.class);
    orderBudgetService = mock(OrderBudgetService.class);
    var timereportService = mock(TimereportService.class);
    var customerorderService = mock(CustomerorderService.class);
    var suborderService = mock(SuborderService.class);
    authorizedUser = mock(AuthorizedUser.class);
    when(authorizedUser.isManager()).thenReturn(true);

    when(orderBudgetRepository.findByCustomerorderSignAndActive(any(), any())).thenAnswer(invocation ->
        plans.stream()
            .filter(plan -> plan.getCustomerorderSign().equals(invocation.getArgument(0)))
            .filter(plan -> plan.getActive().equals(invocation.getArgument(1)))
            .toList());

    when(assignmentRepository.findByTimereportIdIn(anyCollection())).thenAnswer(invocation -> {
      Collection<Long> ids = invocation.getArgument(0);
      return stored.stream().filter(a -> ids.contains(a.getTimereportId())).toList();
    });
    when(assignmentRepository.saveAll(any())).thenAnswer(invocation -> {
      Iterable<TimereportBudgetAssignment> saved = invocation.getArgument(0);
      saved.forEach(a -> {
        if (!stored.contains(a)) {
          stored.add(a);
        }
      });
      return saved;
    });

    when(customerorderService.getCustomerorderBySign("CO")).thenReturn(customerorder("CO", 1L));
    when(timereportService.getTimereportsByDatesAndCustomerOrderId(any(), any(), anyLong()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(0);
          LocalDate until = invocation.getArgument(1);
          return reports.stream()
              .filter(r -> !r.getReferenceday().isBefore(from) && !r.getReferenceday().isAfter(until))
              .toList();
        });

    // CO/01 with CO/01/02 below it, plus the sibling CO/02.
    when(suborderService.getSuborderById(1L)).thenReturn(firstLevel("CO", "01"));
    when(suborderService.getSuborderById(2L)).thenReturn(below(firstLevel("CO", "01"), "02"));
    when(suborderService.getSuborderById(3L)).thenReturn(firstLevel("CO", "02"));

    // The real resolver: scope and validity must be judged by the production rule.
    var budgetResolver = new BudgetResolver(orderBudgetRepository, suborderService);
    service = new TimereportBudgetBulkAssignmentService(assignmentRepository, orderBudgetService,
        budgetResolver, timereportService, customerorderService, authorizedUser);
  }

  // --- preview --------------------------------------------------------------------------------

  @Test
  public void should_preview_the_bookings_and_hours_of_the_selection() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenReport(101L, "CO", "CO/01", 1L, JUN, Duration.ofMinutes(30));

    var preview = service.preview(data(null, JAN, DEC, 7L, false));

    assertThat(preview.unassigned().bookings()).isEqualTo(2);
    assertThat(preview.unassigned().hours()).isEqualTo(Duration.ofMinutes(90));
    assertThat(preview.selected().bookings()).isEqualTo(2);
    assertThat(preview.affected(false).bookings()).isEqualTo(2);
  }

  @Test
  public void should_report_how_many_bookings_already_belong_to_another_plan() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenPlan(8L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenReport(101L, "CO", "CO/01", 1L, JUN, HOUR);
    givenAssignment(101L, 8L);

    var preview = service.preview(data(null, JAN, DEC, 7L, false));

    assertThat(preview.unassigned().bookings()).isEqualTo(1);
    assertThat(preview.assignedElsewhere().bookings()).isEqualTo(1);
    // Without the retarget option only the unassigned booking is written.
    assertThat(preview.affected(false).bookings()).isEqualTo(1);
    assertThat(preview.affected(true).bookings()).isEqualTo(2);
  }

  @Test
  public void should_report_bookings_that_already_sit_on_the_target_plan() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenAssignment(100L, 7L);

    var preview = service.preview(data(null, JAN, DEC, 7L, true));

    assertThat(preview.alreadyOnTarget().bookings()).isEqualTo(1);
    assertThat(preview.affected(true).bookings()).isZero();
    assertThat(preview.isEmpty()).isFalse();
  }

  /** The same rule as the single assignment (#908): out of scope stays out, however it was selected. */
  @Test
  public void should_report_bookings_outside_the_scope_of_the_target_plan_as_not_assignable() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenReport(101L, "CO", "CO/02", 3L, MAR, HOUR);

    var preview = service.preview(data(null, JAN, DEC, 7L, false));

    assertThat(preview.unassigned().bookings()).isEqualTo(1);
    assertThat(preview.notAssignable().bookings()).isEqualTo(1);
  }

  @Test
  public void should_report_bookings_outside_the_validity_of_the_target_plan_as_not_assignable() {
    givenPlan(7L, "CO", null, JAN, JUN, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenReport(101L, "CO", "CO/01", 1L, SEP, HOUR);

    var preview = service.preview(data(null, JAN, DEC, 7L, false));

    assertThat(preview.unassigned().bookings()).isEqualTo(1);
    assertThat(preview.notAssignable().bookings()).isEqualTo(1);
  }

  // --- scope of the selection -----------------------------------------------------------------

  @Test
  public void should_act_only_on_the_selected_period() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenReport(101L, "CO", "CO/01", 1L, SEP, HOUR);

    service.assign(data(null, JAN, JUN, 7L, false));

    assertThat(stored).singleElement()
        .extracting(TimereportBudgetAssignment::getTimereportId).isEqualTo(100L);
  }

  @Test
  public void should_act_only_on_the_selected_suborder() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenReport(101L, "CO", "CO/02", 3L, MAR, HOUR);

    service.assign(data("CO/01", JAN, DEC, 7L, false));

    assertThat(stored).singleElement()
        .extracting(TimereportBudgetAssignment::getTimereportId).isEqualTo(100L);
  }

  /** Plans live on the first level, bookings happen below it — a selected suborder includes those. */
  @Test
  public void should_include_the_levels_below_the_selected_suborder() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenReport(101L, "CO", "CO/01/02", 2L, MAR, HOUR);
    givenReport(102L, "CO", "CO/02", 3L, MAR, HOUR);

    service.assign(data("CO/01", JAN, DEC, 7L, false));

    assertThat(stored).extracting(TimereportBudgetAssignment::getTimereportId)
        .containsExactlyInAnyOrder(100L, 101L);
  }

  // --- applying -------------------------------------------------------------------------------

  @Test
  public void should_assign_the_unassigned_bookings_of_the_selection() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenReport(101L, "CO", "CO/01", 1L, JUN, HOUR);

    var written = service.assign(data(null, JAN, DEC, 7L, false));

    assertThat(stored).hasSize(2);
    assertThat(stored).allSatisfy(a -> assertThat(a.getOrderBudget().getId()).isEqualTo(7L));
    assertThat(written.bookings()).isEqualTo(2);
    assertThat(written.hours()).isEqualTo(Duration.ofHours(2));
  }

  @Test
  public void should_leave_bookings_of_another_plan_alone_by_default() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenPlan(8L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenAssignment(100L, 8L);

    var written = service.assign(data(null, JAN, DEC, 7L, false));

    assertThat(stored).singleElement()
        .extracting(a -> a.getOrderBudget().getId()).isEqualTo(8L);
    assertThat(written.bookings()).isZero();
  }

  @Test
  public void should_retarget_bookings_of_another_plan_when_asked_to() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenPlan(8L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);
    givenAssignment(100L, 8L);
    var existing = stored.get(0);

    var written = service.assign(data(null, JAN, DEC, 7L, true));

    // The row is retargeted, not replaced — otherwise the assignment loses its audit trail.
    assertThat(stored).singleElement().isSameAs(existing);
    assertThat(existing.getOrderBudget().getId()).isEqualTo(7L);
    assertThat(written.bookings()).isEqualTo(1);
  }

  @Test
  public void should_never_assign_a_booking_outside_scope_or_validity() {
    givenPlan(7L, "CO", "CO/01", JAN, JUN, true);
    givenReport(100L, "CO", "CO/02", 3L, MAR, HOUR);
    givenReport(101L, "CO", "CO/01", 1L, SEP, HOUR);

    var written = service.assign(data(null, JAN, DEC, 7L, true));

    assertThat(stored).isEmpty();
    assertThat(written.bookings()).isZero();
  }

  @Test
  public void should_reject_an_inactive_target_plan() {
    givenPlan(7L, "CO", null, JAN, DEC, false);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);

    assertThatThrownBy(() -> service.assign(data(null, JAN, DEC, 7L, false)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_BUDGET_INACTIVE.getCode());
    assertThat(stored).isEmpty();
  }

  /** An empty {@code IN ()} is not valid SQL, so an empty selection must not query at all. */
  @Test
  public void should_not_query_the_assignments_of_an_empty_selection() {
    givenPlan(7L, "CO", null, JAN, DEC, true);

    var preview = service.preview(data(null, JAN, DEC, 7L, false));

    assertThat(preview.isEmpty()).isTrue();
    verify(assignmentRepository, never()).findByTimereportIdIn(anyCollection());
  }

  // --- selection of people (#953) -------------------------------------------------------------

  @Test
  public void should_act_only_on_the_selected_person() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR, 1L);
    givenReport(101L, "CO", "CO/01", 1L, MAR, HOUR, 2L);

    var written = service.assign(data(null, JAN, DEC, 7L, List.of(2L), false));

    assertThat(stored).singleElement()
        .extracting(TimereportBudgetAssignment::getTimereportId).isEqualTo(101L);
    assertThat(written.bookings()).isEqualTo(1);
  }

  @Test
  public void should_act_on_all_of_several_selected_people() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR, 1L);
    givenReport(101L, "CO", "CO/01", 1L, MAR, HOUR, 2L);
    givenReport(102L, "CO", "CO/01", 1L, MAR, HOUR, 3L);

    service.assign(data(null, JAN, DEC, 7L, List.of(1L, 3L), false));

    assertThat(stored).extracting(TimereportBudgetAssignment::getTimereportId)
        .containsExactlyInAnyOrder(100L, 102L);
  }

  /** Optional field: no choice must behave exactly as before the field existed. */
  @Test
  public void should_act_on_everyone_without_a_choice_of_people() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR, 1L);
    givenReport(101L, "CO", "CO/01", 1L, MAR, HOUR, 2L);

    service.assign(data(null, JAN, DEC, 7L, List.of(), false));

    assertThat(stored).extracting(TimereportBudgetAssignment::getTimereportId)
        .containsExactlyInAnyOrder(100L, 101L);
  }

  @Test
  public void should_count_only_the_bookings_of_the_selected_people_in_the_preview() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR, 1L);
    givenReport(101L, "CO", "CO/01", 1L, MAR, Duration.ofMinutes(30), 2L);

    var preview = service.preview(data(null, JAN, DEC, 7L, List.of(1L), false));

    assertThat(preview.selected().bookings()).isEqualTo(1);
    assertThat(preview.unassigned().bookings()).isEqualTo(1);
    assertThat(preview.unassigned().hours()).isEqualTo(HOUR);
  }

  // --- the people to choose from (#953) -------------------------------------------------------

  @Test
  public void should_offer_every_person_of_the_selection_once_sorted_by_sign() {
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR, 2L);
    givenReport(101L, "CO", "CO/01", 1L, JUN, HOUR, 2L);
    givenReport(102L, "CO", "CO/02", 3L, MAR, HOUR, 1L);

    var employees = service.selectableEmployees(data(null, JAN, DEC, null, false));

    assertThat(employees).extracting(BulkAssignmentEmployee::sign).containsExactly("e1", "e2");
    assertThat(employees).extracting(BulkAssignmentEmployee::label)
        .containsExactly("e1 - Person 1", "e2 - Person 2");
  }

  @Test
  public void should_offer_only_the_people_who_booked_in_the_selected_suborder_and_period() {
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR, 1L);
    givenReport(101L, "CO", "CO/02", 3L, MAR, HOUR, 2L);
    givenReport(102L, "CO", "CO/01", 1L, SEP, HOUR, 3L);

    var employees = service.selectableEmployees(data("CO/01", JAN, JUN, null, false));

    assertThat(employees).extracting(BulkAssignmentEmployee::sign).containsExactly("e1");
  }

  /** The choice of people is made before the plan, so the list must not wait for it. */
  @Test
  public void should_offer_the_people_without_a_chosen_target_plan() {
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR, 1L);

    assertThat(service.selectableEmployees(data(null, JAN, DEC, null, false))).hasSize(1);
    verify(orderBudgetService, never()).getById(anyLong());
  }

  @Test
  public void should_offer_nobody_while_the_period_is_incomplete() {
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR, 1L);

    assertThat(service.selectableEmployees(data(null, JAN, null, null, false))).isEmpty();
    assertThat(service.selectableEmployees(data(null, DEC, JAN, null, false))).isEmpty();
    assertThat(service.selectableEmployees(
        new BulkAssignmentData(null, null, JAN, DEC, null, List.of(), false))).isEmpty();
  }

  @Test
  public void should_reject_reading_the_people_without_manager_rights() {
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> service.selectableEmployees(data(null, JAN, DEC, null, false)))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(ErrorCode.AA_NEEDS_MANAGER.getCode());
  }

  // --- authorization --------------------------------------------------------------------------

  @Test
  public void should_reject_the_preview_without_manager_rights() {
    when(authorizedUser.isManager()).thenReturn(false);
    givenPlan(7L, "CO", null, JAN, DEC, true);

    assertThatThrownBy(() -> service.preview(data(null, JAN, DEC, 7L, false)))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(ErrorCode.AA_NEEDS_MANAGER.getCode());
  }

  @Test
  public void should_reject_applying_without_manager_rights() {
    when(authorizedUser.isManager()).thenReturn(false);
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", "CO/01", 1L, MAR, HOUR);

    assertThatThrownBy(() -> service.assign(data(null, JAN, DEC, 7L, false)))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(ErrorCode.AA_NEEDS_MANAGER.getCode());
    assertThat(stored).isEmpty();
  }

  // --- test fixture ---------------------------------------------------------------------------

  private static BulkAssignmentData data(String suborderSign, LocalDate from, LocalDate until,
                                         Long targetBudgetId, boolean includeAssigned) {
    return data(suborderSign, from, until, targetBudgetId, List.of(), includeAssigned);
  }

  private static BulkAssignmentData data(String suborderSign, LocalDate from, LocalDate until,
                                         Long targetBudgetId, List<Long> employeeIds,
                                         boolean includeAssigned) {
    return new BulkAssignmentData("CO", suborderSign, from, until, targetBudgetId, employeeIds,
        includeAssigned);
  }

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
    plans.add(plan);
    when(orderBudgetService.getById(id)).thenReturn(plan);
  }

  private void givenReport(long id, String customerorderSign, String completeOrderSign,
                           long suborderId, LocalDate day, Duration duration) {
    givenReport(id, customerorderSign, completeOrderSign, suborderId, day, duration, 1L);
  }

  private void givenReport(long id, String customerorderSign, String completeOrderSign,
                           long suborderId, LocalDate day, Duration duration, long employeeId) {
    reports.add(TimereportDTO.builder()
        .id(id)
        .customerorderSign(customerorderSign)
        .completeOrderSign(completeOrderSign)
        .suborderId(suborderId)
        .referenceday(day)
        .duration(duration)
        .employeeId(employeeId)
        .employeeSign("e" + employeeId)
        .employeeName("Person " + employeeId)
        .build());
  }

  private void givenAssignment(long timereportId, long budgetId) {
    var assignment = new TimereportBudgetAssignment();
    assignment.setTimereportId(timereportId);
    assignment.setOrderBudget(plans.stream()
        .filter(p -> p.getId() == budgetId).findFirst().orElseThrow());
    setId(assignment, 900L + timereportId);
    stored.add(assignment);
  }

  private static Customerorder customerorder(String sign, long id) {
    var order = new Customerorder();
    order.setSign(sign);
    order.setShortdescription(sign + " description");
    setId(order, id);
    return order;
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

}
