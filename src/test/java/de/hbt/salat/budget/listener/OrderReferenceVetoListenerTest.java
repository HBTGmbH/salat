package de.hbt.salat.budget.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.budget.persistence.EmployeeCostAssignmentRepository;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.OrderFlatRateRepository;
import de.hbt.salat.budget.persistence.OrderPricingRepository;
import de.hbt.salat.budget.service.OrderReferenceService;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.exception.VetoedException;
import de.hbt.salat.order.event.CustomerorderDeleteEvent;
import de.hbt.salat.order.event.SuborderDeleteEvent;
import de.hbt.salat.order.service.SuborderService;

/**
 * Budget data refers to its order and suborder by id with a foreign key (#1205). Deleting what it
 * refers to is refused with a message that names what is in the way, before the key would refuse it
 * as a failed statement.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderReferenceVetoListenerTest {

  private OrderBudgetRepository orderBudgetRepository;
  private OrderFlatRateRepository orderFlatRateRepository;
  private EmployeeCostAssignmentRepository assignmentRepository;
  private OrderPricingRepository orderPricingRepository;
  private OrderReferenceVetoListener listener;

  @BeforeEach
  void setUp() {
    orderBudgetRepository = mock(OrderBudgetRepository.class);
    orderFlatRateRepository = mock(OrderFlatRateRepository.class);
    assignmentRepository = mock(EmployeeCostAssignmentRepository.class);
    orderPricingRepository = mock(OrderPricingRepository.class);
    listener = new OrderReferenceVetoListener(new OrderReferenceService(orderBudgetRepository, orderFlatRateRepository,
        assignmentRepository, orderPricingRepository, mock(SuborderService.class)));
  }

  @Test
  void an_order_with_a_plan_is_not_deleted() {
    when(orderBudgetRepository.countByCustomerorderId(1L)).thenReturn(1L);

    assertThatThrownBy(() -> listener.onCustomerorderDelete(new CustomerorderDeleteEvent(1L)))
        .isInstanceOfSatisfying(VetoedException.class, e -> assertThat(e.getMessages())
            .extracting(ServiceFeedbackMessage::getErrorCode)
            .containsExactly(ErrorCode.BU_ORDER_HAS_BUDGET_REFERENCES));
  }

  @Test
  void an_order_with_a_flat_rate_is_not_deleted() {
    when(orderFlatRateRepository.countByCustomerorderId(1L)).thenReturn(2L);

    assertThatThrownBy(() -> listener.onCustomerorderDelete(new CustomerorderDeleteEvent(1L)))
        .isInstanceOf(VetoedException.class);
  }

  /** Customer rates refer to their order by id as well (#1212), and the message counts them. */
  @Test
  void an_order_with_a_customer_rate_is_not_deleted() {
    when(orderPricingRepository.countByCustomerorderId(1L)).thenReturn(3L);

    assertThatThrownBy(() -> listener.onCustomerorderDelete(new CustomerorderDeleteEvent(1L)))
        .isInstanceOfSatisfying(VetoedException.class, e -> assertThat(e.getMessages()).singleElement()
            .satisfies(message -> {
              assertThat(message.getErrorCode()).isEqualTo(ErrorCode.BU_ORDER_HAS_BUDGET_REFERENCES);
              assertThat(message.getArguments()).containsExactly(0L, 0L, 3L, 0L);
            }));
  }

  /** A cost assignment may refer to the whole order (#1343), and the message counts it. */
  @Test
  void an_order_a_cost_assignment_refers_to_is_not_deleted() {
    when(assignmentRepository.countByCustomerorderId(1L)).thenReturn(2L);

    assertThatThrownBy(() -> listener.onCustomerorderDelete(new CustomerorderDeleteEvent(1L)))
        .isInstanceOfSatisfying(VetoedException.class, e -> assertThat(e.getMessages()).singleElement()
            .satisfies(message -> {
              assertThat(message.getErrorCode()).isEqualTo(ErrorCode.BU_ORDER_HAS_BUDGET_REFERENCES);
              assertThat(message.getArguments()).containsExactly(0L, 0L, 0L, 2L);
            }));
  }

  @Test
  void a_suborder_a_cost_assignment_refers_to_is_not_deleted() {
    when(assignmentRepository.countBySuborderId(11L)).thenReturn(1L);

    assertThatThrownBy(() -> listener.onSuborderDelete(new SuborderDeleteEvent(11L)))
        .isInstanceOfSatisfying(VetoedException.class, e -> assertThat(e.getMessages())
            .extracting(ServiceFeedbackMessage::getErrorCode)
            .containsExactly(ErrorCode.BU_SUBORDER_HAS_BUDGET_REFERENCES));
  }

  @Test
  void what_no_budget_data_refers_to_is_deleted() {
    assertThatCode(() -> listener.onCustomerorderDelete(new CustomerorderDeleteEvent(1L))).doesNotThrowAnyException();
    assertThatCode(() -> listener.onSuborderDelete(new SuborderDeleteEvent(11L))).doesNotThrowAnyException();
  }

}
