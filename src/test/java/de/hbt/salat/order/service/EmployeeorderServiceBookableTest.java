package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.auth.EmployeeorderAuthorization;
import de.hbt.salat.order.domain.BookableSuborder;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.EmployeeorderDAO;

/** What a contract may book on as values (#1532): the condition of the booking form, under the read rule. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeeorderServiceBookableTest {

  private static final long CONTRACT_ID = 7L;
  private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

  @Mock
  private EmployeeorderDAO employeeorderDAO;
  @Mock
  private EmployeeorderAuthorization employeeorderAuthorization;
  @InjectMocks
  private EmployeeorderService employeeorderService;

  private Customerorder customerorder;

  @BeforeEach
  void setUp() {
    customerorder = new Customerorder();
    customerorder.setSign("ABC-12");
    customerorder.setShortdescription("Wartung");
    when(employeeorderAuthorization.isAuthorized(any(), any())).thenReturn(true);
  }

  @Test
  void the_suborders_with_an_employee_order_valid_on_the_day_in_the_order_of_their_sign() {
    var later = employeeorder(2L, suborder(20L, "02"), DAY.plusDays(1));
    var second = employeeorder(3L, suborder(30L, "03"), DAY.minusYears(1));
    var first = employeeorder(1L, suborder(10L, "01"), DAY);
    when(employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(CONTRACT_ID, DAY))
        .thenReturn(List.of(later, second, first));

    assertThat(employeeorderService.getBookableSuborders(CONTRACT_ID, DAY))
        .extracting(BookableSuborder::suborderId, BookableSuborder::employeeorderId, BookableSuborder::completeOrderSign)
        .containsExactly(
            tuple(10L, 1L, "ABC-12/01"),
            tuple(30L, 3L, "ABC-12/03"));
  }

  @Test
  void an_employee_order_the_user_may_not_read_does_not_count() {
    var foreign = employeeorder(1L, suborder(10L, "01"), DAY);
    when(employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(CONTRACT_ID, DAY)).thenReturn(List.of(foreign));
    when(employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(CONTRACT_ID, 10L, DAY)).thenReturn(foreign);
    when(employeeorderAuthorization.isAuthorized(foreign, AccessLevel.READ)).thenReturn(false);

    assertThat(employeeorderService.getBookableSuborders(CONTRACT_ID, DAY)).isEmpty();
    assertThat(employeeorderService.getEmployeeorderId(CONTRACT_ID, 10L, DAY)).isEmpty();
  }

  @Test
  void the_employee_order_of_contract_suborder_and_day_as_its_id() {
    var employeeorder = employeeorder(5L, suborder(10L, "01"), DAY);
    when(employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(CONTRACT_ID, 10L, DAY))
        .thenReturn(employeeorder);

    assertThat(employeeorderService.getEmployeeorderId(CONTRACT_ID, 10L, DAY)).contains(5L);
    assertThat(employeeorderService.getEmployeeorderId(CONTRACT_ID, 99L, DAY)).isEmpty();
  }

  private Suborder suborder(long id, String sign) {
    var suborder = new Suborder();
    ReflectionTestUtils.setField(suborder, "id", id);
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setShortdescription(sign);
    suborder.deriveCompleteOrderSign();
    return suborder;
  }

  private static Employeeorder employeeorder(long id, Suborder suborder, LocalDate from) {
    var employeeorder = new Employeeorder();
    ReflectionTestUtils.setField(employeeorder, "id", id);
    employeeorder.setSuborder(suborder);
    employeeorder.setFromDate(from);
    var contract = new Employeecontract();
    contract.setValidFrom(DAY.minusYears(5));
    employeeorder.setEmployeecontract(contract);
    return employeeorder;
  }
}
