package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.BudgetBackfillOrderResult;
import org.tb.budget.domain.BudgetBackfillResult;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.TimereportBudgetAssignment;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.budget.persistence.TimereportBudgetAssignmentRepository;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * The backfill writes its answer into the database once (#910). A wrong resolution here does not
 * show up as a miscalculation that can be corrected by fixing a formula — it becomes the data, so
 * the outcome of every case has to be pinned down.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class TimereportBudgetBackfillServiceTest {

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
  private AuthorizedUser authorizedUser;
  private TimereportBudgetBackfillService service;

  @BeforeEach
  public void setUp() {
    var orderBudgetRepository = mock(OrderBudgetRepository.class);
    assignmentRepository = mock(TimereportBudgetAssignmentRepository.class);
    var timereportService = mock(TimereportService.class);
    var customerorderService = mock(CustomerorderService.class);
    var suborderService = mock(SuborderService.class);
    authorizedUser = mock(AuthorizedUser.class);
    when(authorizedUser.isManager()).thenReturn(true);

    when(orderBudgetRepository.findActiveCustomerorderSigns()).thenAnswer(invocation ->
        plans.stream().filter(OrderBudget::getActive)
            .map(OrderBudget::getCustomerorderSign).distinct().sorted().toList());
    when(orderBudgetRepository.findByCustomerorderSignAndActive(any(), any())).thenAnswer(invocation ->
        plans.stream()
            .filter(plan -> plan.getCustomerorderSign().equals(invocation.getArgument(0)))
            .filter(plan -> plan.getActive().equals(invocation.getArgument(1)))
            .toList());

    when(assignmentRepository.findTimereportIdsByCustomerorderSign(any())).thenAnswer(invocation ->
        stored.stream()
            .filter(a -> a.getOrderBudget().getCustomerorderSign().equals(invocation.getArgument(0)))
            .map(TimereportBudgetAssignment::getTimereportId)
            .toList());
    when(assignmentRepository.saveAll(any())).thenAnswer(invocation -> {
      Iterable<TimereportBudgetAssignment> saved = invocation.getArgument(0);
      saved.forEach(stored::add);
      return saved;
    });

    // Both known orders; the sign carries the id, so the report fixture can stay simple.
    when(customerorderService.getCustomerorderBySign("CO")).thenReturn(customerorder("CO", 1L));
    when(customerorderService.getCustomerorderBySign("OTHER")).thenReturn(customerorder("OTHER", 2L));
    when(timereportService.getTimereportsByDatesAndCustomerOrderId(any(), any(), anyLong()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(0);
          LocalDate until = invocation.getArgument(1);
          long orderId = invocation.getArgument(2);
          var sign = orderId == 1L ? "CO" : "OTHER";
          return reports.stream()
              .filter(r -> r.getCustomerorderSign().equals(sign))
              .filter(r -> !r.getReferenceday().isBefore(from) && !r.getReferenceday().isAfter(until))
              .toList();
        });

    // CO/01 with CO/01/02 and CO/01/02/03 below it, plus the sibling CO/02.
    when(suborderService.getSuborderById(1L)).thenReturn(firstLevel("CO", "01"));
    when(suborderService.getSuborderById(2L)).thenReturn(below(firstLevel("CO", "01"), "02"));
    when(suborderService.getSuborderById(3L)).thenReturn(firstLevel("CO", "02"));
    when(suborderService.getSuborderById(4L)).thenReturn(below(below(firstLevel("CO", "01"), "02"), "03"));
    when(suborderService.getSuborderById(5L)).thenReturn(firstLevel("OTHER", "01"));

    // The real resolver: the rule that decides the scope must be the production one (#931).
    var budgetResolver = new BudgetResolver(orderBudgetRepository, suborderService);
    service = new TimereportBudgetBackfillService(orderBudgetRepository, assignmentRepository,
        budgetResolver, timereportService, customerorderService, authorizedUser);
  }

  @Test
  public void should_assign_the_existing_bookings_of_an_order() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR, HOUR);
    givenReport(101L, "CO", 1L, JUN, HOUR);
    givenReport(102L, "CO", 2L, SEP, HOUR);

    var result = service.backfill("CO");

    assertThat(stored).hasSize(3);
    assertThat(stored).allSatisfy(a -> assertThat(a.getOrderBudget().getId()).isEqualTo(7L));
    assertThat(order(result, "CO").assigned().bookings()).isEqualTo(3);
    assertThat(order(result, "CO").assigned().hours()).isEqualTo(Duration.ofHours(3));
  }

  /**
   * Plans live on the first suborder level only. Resolving the scope from the booking's own suborder
   * — the defect #931 fixed — would leave every one of these as "no plan" and write that gap into
   * the data for good.
   */
  @Test
  public void should_assign_bookings_below_the_first_suborder_level_to_their_first_level_plan() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR, HOUR);
    givenReport(101L, "CO", 2L, MAR, HOUR);
    givenReport(102L, "CO", 4L, MAR, HOUR);

    var result = service.backfill("CO");

    assertThat(stored).hasSize(3);
    assertThat(order(result, "CO").assigned().bookings()).isEqualTo(3);
    assertThat(order(result, "CO").withoutPlan().bookings()).isZero();
  }

  @Test
  public void should_report_ambiguous_bookings_without_assigning_them() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenPlan(8L, "CO", null, JAN, JUN, true);
    givenReport(100L, "CO", 1L, MAR, HOUR);

    var result = service.backfill("CO");

    assertThat(stored).isEmpty();
    assertThat(order(result, "CO").ambiguous().bookings()).isEqualTo(1);
    assertThat(order(result, "CO").assigned().bookings()).isZero();
  }

  @Test
  public void should_report_bookings_without_a_matching_plan() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);
    givenReport(100L, "CO", 3L, MAR, Duration.ofMinutes(90));

    var result = service.backfill("CO");

    assertThat(stored).isEmpty();
    assertThat(order(result, "CO").withoutPlan().bookings()).isEqualTo(1);
    assertThat(order(result, "CO").withoutPlan().hours()).isEqualTo(Duration.ofMinutes(90));
  }

  /** Idempotence: what the first run assigned is what the second run leaves alone. */
  @Test
  public void should_change_nothing_on_a_second_run() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR, HOUR);
    givenReport(101L, "CO", 1L, JUN, HOUR);
    service.backfill("CO");
    var afterFirstRun = List.copyOf(stored);

    var result = service.backfill("CO");

    assertThat(stored).containsExactlyElementsOf(afterFirstRun);
    assertThat(order(result, "CO").assigned().bookings()).isZero();
    assertThat(order(result, "CO").alreadyAssigned().bookings()).isEqualTo(2);
  }

  @Test
  public void should_restrict_the_run_to_one_customer_order() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenPlan(8L, "OTHER", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR, HOUR);
    givenReport(200L, "OTHER", 5L, MAR, HOUR);

    var result = service.backfill("CO");

    assertThat(result.orders()).singleElement()
        .extracting(BudgetBackfillOrderResult::customerorderSign).isEqualTo("CO");
    assertThat(stored).singleElement()
        .extracting(TimereportBudgetAssignment::getTimereportId).isEqualTo(100L);
  }

  @Test
  public void should_run_over_every_order_with_an_active_plan_when_unrestricted() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenPlan(8L, "OTHER", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR, HOUR);
    givenReport(200L, "OTHER", 5L, MAR, HOUR);

    var result = service.backfill(null);

    assertThat(result.orders()).extracting(BudgetBackfillOrderResult::customerorderSign)
        .containsExactly("CO", "OTHER");
    assertThat(stored).hasSize(2);
    assertThat(result.totalAssigned().bookings()).isEqualTo(2);
  }

  @Test
  public void should_skip_an_order_whose_only_plan_is_inactive() {
    givenPlan(7L, "CO", null, JAN, DEC, false);
    givenReport(100L, "CO", 1L, MAR, HOUR);

    var result = service.backfill(null);

    assertThat(result.isEmpty()).isTrue();
    assertThat(stored).isEmpty();
    verify(assignmentRepository, never()).saveAll(any());
  }

  /**
   * Only the span of the active plans is examined. A booking outside it could not be assigned by any
   * plan, so listing it would bury the orders that need attention — but the examined period is part
   * of the protocol, so the limit is visible.
   */
  @Test
  public void should_examine_only_the_period_the_active_plans_cover() {
    givenPlan(7L, "CO", null, JAN, JUN, true);
    givenReport(100L, "CO", 1L, MAR, HOUR);
    givenReport(101L, "CO", 1L, SEP, HOUR);

    var result = service.backfill("CO");

    assertThat(stored).hasSize(1);
    assertThat(order(result, "CO").assigned().bookings()).isEqualTo(1);
    assertThat(order(result, "CO").withoutPlan().bookings()).isZero();
    assertThat(order(result, "CO").examinedFrom()).isEqualTo(JAN);
    assertThat(order(result, "CO").examinedUntil()).isEqualTo(JUN);
  }

  /** The span is the union over the plans, so a later plan widens what is looked at. */
  @Test
  public void should_examine_the_span_of_all_active_plans_of_the_order() {
    givenPlan(7L, "CO", "CO/01", JAN, JUN, true);
    givenPlan(8L, "CO", "CO/02", JUN, DEC, true);
    givenReport(100L, "CO", 3L, SEP, HOUR);

    var result = service.backfill("CO");

    assertThat(order(result, "CO").assigned().bookings()).isEqualTo(1);

    assertThat(order(result, "CO").examinedFrom()).isEqualTo(JAN);
    assertThat(order(result, "CO").examinedUntil()).isEqualTo(DEC);
  }

  @Test
  public void should_reject_the_run_without_manager_rights() {
    when(authorizedUser.isManager()).thenReturn(false);
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenReport(100L, "CO", 1L, MAR, HOUR);

    assertThatThrownBy(() -> service.backfill("CO"))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(ErrorCode.AA_NEEDS_MANAGER.getCode());
    assertThat(stored).isEmpty();
  }

  // --- test fixture ---------------------------------------------------------------------------

  private static BudgetBackfillOrderResult order(
      BudgetBackfillResult result, String customerorderSign) {
    return result.orders().stream()
        .filter(o -> o.customerorderSign().equals(customerorderSign))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no protocol entry for " + customerorderSign));
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
  }

  private void givenReport(long id, String customerorderSign, long suborderId,
                           LocalDate day, Duration duration) {
    reports.add(TimereportDTO.builder()
        .id(id)
        .customerorderSign(customerorderSign)
        .suborderId(suborderId)
        .completeOrderSign(customerorderSign + "/xx")
        .referenceday(day)
        .duration(duration)
        .build());
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
