package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.EmployeeorderDAO;

/**
 * Finding an employee order by the complete sign of its suborder (#1142). The lookup it replaces
 * normalized the sign and took the shortest one that contained the input, so {@code 4711/1} also
 * found {@code 4711/10} or {@code 14711/1}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeeorderServiceTest {

  private static final long CONTRACT_ID = 10L;
  private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

  @Mock
  private EmployeeorderDAO employeeorderDAO;

  @InjectMocks
  private EmployeeorderService employeeorderService;

  @Test
  void finds_the_order_whose_suborder_carries_exactly_the_sign() {
    var order = order(1L, "4711", "01", DAY.minusMonths(1));
    ordersOfContract(order(2L, "4711", "02", DAY.minusMonths(1)), order);

    assertThat(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(CONTRACT_ID, "4711/01", DAY))
        .containsExactly(order);
  }

  @Test
  void does_not_take_a_sign_that_merely_contains_the_input() {
    ordersOfContract(order(1L, "4711", "10", DAY.minusMonths(1)), order(2L, "14711", "1", DAY.minusMonths(1)));

    assertThat(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(CONTRACT_ID, "4711/1", DAY)).isEmpty();
  }

  @Test
  void compares_case_and_separators_as_they_are() {
    ordersOfContract(order(1L, "ALPHA", "BE", DAY.minusMonths(1)));

    assertThat(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(CONTRACT_ID, "alpha/be", DAY)).isEmpty();
    assertThat(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(CONTRACT_ID, "ALPHA.BE", DAY)).isEmpty();
  }

  @Test
  void ignores_surrounding_blanks() {
    var order = order(1L, "4711", "01", DAY.minusMonths(1));
    ordersOfContract(order);

    assertThat(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(CONTRACT_ID, " 4711/01 ", DAY))
        .containsExactly(order);
  }

  @Test
  void matches_the_complete_sign_of_a_nested_suborder() {
    var parent = order(1L, "4711", "01", DAY.minusMonths(1));
    var child = new Suborder();
    child.setSign("A");
    child.setCustomerorder(parent.getSuborder().getCustomerorder());
    child.setParentorder(parent.getSuborder());
    child.deriveCompleteOrderSign();
    var childOrder = order(2L, child, DAY.minusMonths(1));
    ordersOfContract(parent, childOrder);

    assertThat(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(CONTRACT_ID, "4711/01/A", DAY))
        .containsExactly(childOrder);
  }

  /* The DAO only leaves out orders that ended before the day; one beginning after it is not valid either. */
  @Test
  void leaves_out_an_order_that_begins_after_the_day() {
    ordersOfContract(order(1L, "4711", "01", DAY.plusDays(1)));

    assertThat(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(CONTRACT_ID, "4711/01", DAY)).isEmpty();
  }

  /* Neither the database nor the forms keep the sign unique; the caller must see both. */
  @Test
  void answers_with_every_match() {
    var first = order(1L, "4711", "01", DAY.minusMonths(1));
    var second = order(2L, "4711", "01", DAY.minusMonths(2));
    ordersOfContract(first, second);

    assertThat(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(CONTRACT_ID, "4711/01", DAY))
        .containsExactly(first, second);
  }

  private void ordersOfContract(Employeeorder... orders) {
    when(employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(CONTRACT_ID, DAY)).thenReturn(List.of(orders));
  }

  private static Employeeorder order(long id, String customerorderSign, String suborderSign, LocalDate from) {
    var customerorder = new Customerorder();
    customerorder.setSign(customerorderSign);
    var suborder = new Suborder();
    suborder.setSign(suborderSign);
    suborder.setCustomerorder(customerorder);
    suborder.deriveCompleteOrderSign();
    return order(id, suborder, from);
  }

  private static Employeeorder order(long id, Suborder suborder, LocalDate from) {
    var contract = new Employeecontract();
    setField(contract, "id", CONTRACT_ID);
    var order = new Employeeorder();
    setField(order, "id", id);
    order.setSuborder(suborder);
    order.setEmployeecontract(contract);
    order.setFromDate(from);
    return order;
  }
}
