package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.common.domain.AuditedEntity;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.SuborderService;

/**
 * The resolver is the single place that decides which plan a booking belongs to (#909). Every
 * automatic path — booking, initial assignment (#910), bulk assignment (#911) — asks it, so a wrong
 * answer here is written into the data rather than merely displayed.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetResolverTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final LocalDate MAR = LocalDate.of(2026, 3, 15);

  private final List<OrderBudget> plans = new ArrayList<>();

  private OrderBudgetRepository orderBudgetRepository;
  private BudgetResolver resolver;

  @BeforeEach
  public void setUp() {
    orderBudgetRepository = mock(OrderBudgetRepository.class);
    var suborderService = mock(SuborderService.class);

    // Deliberately returns inactive plans too: the resolver must not rely on the query alone.
    when(orderBudgetRepository.findByCustomerorderSignAndActive(any(), any())).thenAnswer(invocation ->
        plans.stream()
            .filter(plan -> plan.getCustomerorderSign().equals(invocation.getArgument(0)))
            .toList());

    // CO/01 with CO/01/02 and CO/01/02/03 below it, plus the siblings CO/02 and CO/01/04.
    when(suborderService.getSuborderById(1L)).thenReturn(firstLevel("CO", "01"));
    when(suborderService.getSuborderById(2L)).thenReturn(below(firstLevel("CO", "01"), "02"));
    when(suborderService.getSuborderById(3L)).thenReturn(firstLevel("CO", "02"));
    when(suborderService.getSuborderById(4L)).thenReturn(below(below(firstLevel("CO", "01"), "02"), "03"));
    when(suborderService.getSuborderById(5L)).thenReturn(below(firstLevel("CO", "01"), "04"));

    resolver = new BudgetResolver(orderBudgetRepository, suborderService);
  }

  @Test
  public void should_resolve_a_booking_to_the_single_order_wide_plan_of_its_order() {
    givenPlan(7L, "CO", null, JAN, DEC, true);

    var resolution = resolver.resolve(report(100L, "CO", 1L, MAR));

    assertThat(resolution.isUnique()).isTrue();
    assertThat(resolution.unique()).map(AuditedEntity::getId).contains(7L);
  }

  @Test
  public void should_resolve_a_booking_to_the_plan_on_its_own_suborder() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);

    assertThat(resolver.resolve(report(100L, "CO", 1L, MAR)).unique())
        .map(AuditedEntity::getId).contains(7L);
  }

  /**
   * A plan covers its suborder and everything below it, so a booking further down still belongs to
   * it. Comparing the booking's own sign for equality would leave every deeper booking unassigned —
   * the defect #931 fixed.
   */
  @Test
  public void should_resolve_a_booking_on_the_second_suborder_level_to_the_plan_above_it() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);

    assertThat(resolver.resolve(report(100L, "CO", 2L, MAR)).unique())
        .map(AuditedEntity::getId).contains(7L);
  }

  @Test
  public void should_resolve_a_booking_on_the_third_suborder_level_to_the_plan_above_it() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);

    assertThat(resolver.resolve(report(100L, "CO", 4L, MAR)).unique())
        .map(AuditedEntity::getId).contains(7L);
  }

  @Test
  public void should_not_resolve_a_booking_under_a_sibling_suborder() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);

    var resolution = resolver.resolve(report(100L, "CO", 3L, MAR));

    assertThat(resolution.isEmpty()).isTrue();
    assertThat(resolution.unique()).isEmpty();
  }

  /** A plan may now live on any level, and it then covers exactly its own subtree (#1004). */
  @Test
  public void should_resolve_a_booking_to_a_plan_on_a_deeper_suborder() {
    givenPlan(7L, "CO", "CO/01/02", JAN, DEC, true);

    assertThat(resolver.resolve(report(100L, "CO", 2L, MAR)).unique())
        .map(AuditedEntity::getId).contains(7L);
    assertThat(resolver.resolve(report(101L, "CO", 4L, MAR)).unique())
        .map(AuditedEntity::getId).contains(7L);
  }

  @Test
  public void should_not_resolve_a_booking_of_a_sibling_branch_to_a_deeper_plan() {
    givenPlan(7L, "CO", "CO/01/02", JAN, DEC, true);

    assertThat(resolver.resolve(report(100L, "CO", 5L, MAR)).isEmpty()).isTrue();
  }

  /** Coverage runs downwards: the suborder above the plan is outside it. */
  @Test
  public void should_not_resolve_a_booking_above_a_deeper_plan() {
    givenPlan(7L, "CO", "CO/01/02", JAN, DEC, true);

    assertThat(resolver.resolve(report(100L, "CO", 1L, MAR)).isEmpty()).isTrue();
  }

  @Test
  public void should_not_resolve_a_booking_of_another_customer_order() {
    givenPlan(7L, "CO", null, JAN, DEC, true);

    assertThat(resolver.resolve(report(100L, "OTHER", 1L, MAR)).isEmpty()).isTrue();
  }

  /**
   * Overlapping plans of the same scope cannot be created today ({@code checkNoConflict}), but #914
   * will allow them. The resolver must never guess, whatever the plan landscape looks like.
   */
  @Test
  public void should_report_several_matching_plans_as_ambiguous_and_pick_none() {
    givenPlan(7L, "CO", null, JAN, DEC, true);
    givenPlan(8L, "CO", null, JAN, JUN, true);

    var resolution = resolver.resolve(report(100L, "CO", 1L, MAR));

    assertThat(resolution.isAmbiguous()).isTrue();
    assertThat(resolution.unique()).isEmpty();
    assertThat(resolution.candidates()).hasSize(2);
  }

  @Test
  public void should_resolve_nothing_when_no_plan_exists_for_the_order() {
    assertThat(resolver.resolve(report(100L, "CO", 1L, MAR)).isEmpty()).isTrue();
  }

  @Test
  public void should_not_resolve_a_booking_to_an_inactive_plan() {
    givenPlan(7L, "CO", null, JAN, DEC, false);

    assertThat(resolver.resolve(report(100L, "CO", 1L, MAR)).isEmpty()).isTrue();
  }

  @Test
  public void should_not_resolve_a_booking_dated_outside_the_validity() {
    givenPlan(7L, "CO", null, JAN, JUN, true);

    assertThat(resolver.resolve(report(100L, "CO", 1L, DEC)).isEmpty()).isTrue();
  }

  /** The boundaries belong to the period. */
  @Test
  public void should_resolve_bookings_on_the_first_and_last_day_of_the_validity() {
    givenPlan(7L, "CO", null, JAN, DEC, true);

    assertThat(resolver.resolve(report(100L, "CO", 1L, JAN)).isUnique()).isTrue();
    assertThat(resolver.resolve(report(101L, "CO", 1L, DEC)).isUnique()).isTrue();
  }

  /** A booking whose suborder cannot be read belongs to no suborder plan rather than to a wrong one. */
  @Test
  public void should_not_resolve_a_booking_whose_suborder_is_gone() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);

    assertThat(resolver.resolve(report(100L, "CO", 99L, MAR)).isEmpty()).isTrue();
  }

  // --- batch resolution -----------------------------------------------------------------------

  @Test
  public void should_resolve_a_batch_of_bookings_keyed_by_time_report_id() {
    givenPlan(7L, "CO", "CO/01", JAN, DEC, true);
    givenPlan(8L, "CO", "CO/02", JAN, DEC, true);

    var resolutions = resolver.resolveAll(List.of(
        report(100L, "CO", 1L, MAR),
        report(101L, "CO", 3L, MAR),
        report(102L, "OTHER", 1L, MAR)));

    assertThat(resolutions.get(100L).unique()).map(AuditedEntity::getId).contains(7L);
    assertThat(resolutions.get(101L).unique()).map(AuditedEntity::getId).contains(8L);
    assertThat(resolutions.get(102L).isEmpty()).isTrue();
  }

  /** One query per distinct customer order, not one per booking — a batch books the same order. */
  @Test
  public void should_read_the_plans_of_an_order_only_once_per_batch() {
    givenPlan(7L, "CO", null, JAN, DEC, true);

    resolver.resolveAll(List.of(
        report(100L, "CO", 1L, JAN),
        report(101L, "CO", 1L, MAR),
        report(102L, "CO", 2L, DEC)));

    verify(orderBudgetRepository, times(1)).findByCustomerorderSignAndActive(any(), any());
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
    plans.add(plan);
  }

  private static TimereportDTO report(long id, String customerorderSign, long suborderId, LocalDate day) {
    return TimereportDTO.builder()
        .id(id)
        .customerorderSign(customerorderSign)
        .suborderId(suborderId)
        .completeOrderSign(customerorderSign + "/xx")
        .referenceday(day)
        .build();
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
