package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderDTO;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.TicketReferenceMode;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderDTO;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.persistence.SuborderRepository;

/**
 * A special order keeps the sign the configuration names it by (#1341, ADR-0035): renaming it, or
 * anything above a configured suborder, moving a configured suborder and deleting either are refused.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class SpecialOrderLockTest {

  private static final LocalDate FROM = LocalDate.parse("2026-01-01");

  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final SuborderDAO suborderDAO = mock(SuborderDAO.class);
  private final CustomerorderDAO customerorderDAO = mock(CustomerorderDAO.class);
  private final CustomerorderService customerorderService = mock(CustomerorderService.class);
  private final SpecialOrders specialOrders = mock(SpecialOrders.class);

  @BeforeEach
  void setUp() {
    var order = customerorder(7L, "URLAUB");
    var otherOrder = customerorder(8L, "OTHER");
    when(customerorderService.getCustomerorderById(7L)).thenReturn(order);
    when(customerorderService.getCustomerorderById(8L)).thenReturn(otherOrder);
    when(customerorderDAO.getCustomerorderById(7L)).thenReturn(order);
    when(suborderDAO.getSuborderById(10L)).thenReturn(suborder(10L, order, "2026"));
    when(suborderDAO.getSuborderById(11L)).thenReturn(suborder(11L, order, "Sonderurlaub"));
    when(specialOrders.isLockedCustomerorder(7L)).thenReturn(true);
    when(specialOrders.isLockedSuborder(11L)).thenReturn(true);
  }

  @Test
  void renaming_a_special_order_is_refused() {
    assertThatThrownBy(() -> customerorderService().update(7L, customerorderDto("HOLIDAY")))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.CO_SPECIAL_ORDER_LOCKED.getCode());
  }

  @Test
  void a_special_order_saved_under_its_sign_passes() {
    assertThatCode(() -> customerorderService().update(7L, customerorderDto("URLAUB"))).doesNotThrowAnyException();
  }

  @Test
  void deleting_a_special_order_is_refused() {
    assertThatThrownBy(() -> customerorderService().deleteCustomerorderById(7L))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.CO_SPECIAL_ORDER_LOCKED.getCode());
  }

  @Test
  void renaming_a_special_suborder_is_refused() {
    assertThatThrownBy(() -> suborderService().update(11L, suborderDto("Sonder", null), 7L))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.SO_SPECIAL_ORDER_LOCKED.getCode());
  }

  @Test
  void moving_a_special_suborder_is_refused() {
    assertThatThrownBy(() -> suborderService().update(11L, suborderDto("Sonderurlaub", 10L), 7L))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.SO_SPECIAL_ORDER_LOCKED.getCode());
  }

  @Test
  void deleting_a_special_suborder_is_refused() {
    assertThatThrownBy(() -> suborderService().deleteSuborderById(11L))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.SO_SPECIAL_ORDER_LOCKED.getCode());
  }

  /** A yearly suborder carries no meaning in its sign any more (#1341) and stays free. */
  @Test
  void a_yearly_suborder_can_still_be_renamed() {
    assertThatCode(() -> suborderService().update(10L, suborderDto("Urlaub 2026", null), 7L))
        .doesNotThrowAnyException();
  }

  private CustomerorderService customerorderService() {
    return new CustomerorderService(eventPublisher, mock(CommandPublisher.class), customerorderDAO,
        mock(CustomerDAO.class), mock(EmployeeDAO.class), mock(CustomerorderRepository.class, invocation ->
            invocation.getMethod().getName().equals("save") ? invocation.getArgument(0) : null),
        specialOrders);
  }

  private SuborderService suborderService() {
    return new SuborderService(eventPublisher, mock(CommandPublisher.class), suborderDAO,
        mock(SuborderRepository.class), customerorderService, specialOrders);
  }

  private static SuborderDTO suborderDto(String sign, Long parentId) {
    return new SuborderDTO(7L, sign, "Leistung", "Leistung", null, 'y', false, false, false, false,
        OrderType.STANDARD, "2026-01-01", "", null, null, false, parentId, null, null);
  }

  private static CustomerorderDTO customerorderDto(String sign) {
    return new CustomerorderDTO(1L, FROM, null, sign, "Auftrag", "Auftrag", null, null, null, List.of(1L), 1L,
        null, null, false, OrderType.STANDARD, TicketReferenceMode.LIMITED, 1);
  }

  private static Customerorder customerorder(long id, String sign) {
    var customerorder = new Customerorder();
    setField(customerorder, "id", id);
    customerorder.setSign(sign);
    customerorder.setFromDate(FROM);
    return customerorder;
  }

  private static Suborder suborder(long id, Customerorder customerorder, String sign) {
    var suborder = new Suborder();
    setField(suborder, "id", id);
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setFromDate(FROM);
    suborder.deriveCompleteOrderSign();
    return suborder;
  }
}
