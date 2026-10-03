package de.hbt.salat.budget.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.budget.domain.EmployeeCostAssignment;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderFlatRate;
import de.hbt.salat.budget.persistence.EmployeeCostAssignmentRepository;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.OrderFlatRateRepository;
import de.hbt.salat.budget.service.OrderReferenceService;
import de.hbt.salat.budget.persistence.OrderPricingRepository;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.event.CustomerorderUpdateEvent;
import de.hbt.salat.order.event.SuborderUpdateEvent;
import de.hbt.salat.order.service.SuborderService;

/**
 * The sign columns of plans, flat rates and cost assignments follow the order tree (#1205). The
 * application resolves by id; these columns are what reports, views and ETL definitions still read.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderSignMirrorListenerTest {

  private final Customerorder order = new Customerorder();
  private final Suborder first = new Suborder();
  private final Suborder below = new Suborder();

  private final OrderBudget plan = new OrderBudget();
  private final OrderFlatRate flatRate = new OrderFlatRate();
  private final EmployeeCostAssignment assignment = new EmployeeCostAssignment();

  private OrderPricingRepository orderPricingRepository;
  private OrderSignMirrorListener listener;

  @BeforeEach
  void setUp() {
    setId(order, 1L);
    order.setSign("CO");
    setId(first, 11L);
    first.setCustomerorder(order);
    first.setSign("01");
    setId(below, 12L);
    below.setCustomerorder(order);
    below.setParentorder(first);
    below.setSign("A");

    plan.setCustomerorderId(1L);
    plan.setCustomerorderSign("CO");
    plan.setSuborderId(11L);
    plan.setSuborderSign("CO/01");
    flatRate.setCustomerorderId(1L);
    flatRate.setCustomerorderSign("CO");
    flatRate.setSuborderId(12L);
    flatRate.setSuborderSign("CO/01/A");
    assignment.setSuborderId(12L);
    assignment.setSuborderSign("CO/01/A");

    var orderBudgetRepository = mock(OrderBudgetRepository.class);
    when(orderBudgetRepository.findByCustomerorderId(1L)).thenReturn(List.of(plan));
    var orderFlatRateRepository = mock(OrderFlatRateRepository.class);
    when(orderFlatRateRepository.findByCustomerorderIdOrderByValidFromAsc(1L)).thenReturn(List.of(flatRate));
    var assignmentRepository = mock(EmployeeCostAssignmentRepository.class);
    when(assignmentRepository.findBySuborderIdIn(any())).thenReturn(List.of(assignment));
    var suborderService = mock(SuborderService.class);
    when(suborderService.getSubordersByCustomerorderId(1L)).thenReturn(List.of(first, below));
    when(suborderService.getSuborderById(anyLong())).thenAnswer(i -> i.<Long>getArgument(0) == 11L ? first : below);
    orderPricingRepository = mock(OrderPricingRepository.class);
    listener = new OrderSignMirrorListener(new OrderReferenceService(orderBudgetRepository, orderFlatRateRepository,
        assignmentRepository, orderPricingRepository, suborderService));
  }

  @Test
  void a_renamed_order_is_written_into_every_sign_column_of_its_budget_data() {
    order.setSign("NEW");

    listener.onCustomerorderUpdate(new CustomerorderUpdateEvent(order));

    assertThat(plan.getCustomerorderSign()).isEqualTo("NEW");
    assertThat(plan.getSuborderSign()).isEqualTo("NEW/01");
    assertThat(flatRate.getCustomerorderSign()).isEqualTo("NEW");
    assertThat(flatRate.getSuborderSign()).isEqualTo("NEW/01/A");
    assertThat(assignment.getSuborderSign()).isEqualTo("NEW/01/A");
  }

  /**
   * The customer rates refer to their order by id (#1212); their sign column is written from the order
   * by that id, so a rename needs no previous sign to find them.
   */
  @Test
  void a_renamed_order_takes_its_customer_rates_along() {
    order.setSign("NEW");

    listener.onCustomerorderUpdate(new CustomerorderUpdateEvent(order));

    verify(orderPricingRepository).updateCustomerorderSign(1L, "NEW");
  }

  /** A renamed suborder changes the complete sign of its whole subtree. */
  @Test
  void a_renamed_suborder_is_written_into_its_subtree() {
    first.setSign("X1");

    listener.onSuborderUpdate(new SuborderUpdateEvent(first));

    assertThat(plan.getSuborderSign()).isEqualTo("CO/X1");
    assertThat(flatRate.getSuborderSign()).isEqualTo("CO/X1/A");
    assertThat(assignment.getSuborderSign()).isEqualTo("CO/X1/A");
  }

  /** A suborder moved to another parent changes its complete sign the same way. */
  @Test
  void a_moved_suborder_is_written_with_its_new_parent() {
    below.setParentorder(null);

    listener.onSuborderUpdate(new SuborderUpdateEvent(below));

    assertThat(flatRate.getSuborderSign()).isEqualTo("CO/A");
    assertThat(assignment.getSuborderSign()).isEqualTo("CO/A");
    assertThat(plan.getSuborderSign()).isEqualTo("CO/01");
  }

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
