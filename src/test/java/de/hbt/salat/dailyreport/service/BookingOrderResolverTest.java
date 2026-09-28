package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_AMBIGUOUS_EMPLOYEE_ORDER;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_EMPLOYEE_UNKNOWN;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_NO_CONTRACT;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_NO_EMPLOYEE_ORDER;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_OF_OTHER_EMPLOYEE;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_ORDER_CONTRADICTS_SIGN;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_ORDER_NOT_NAMED;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;

import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.dailyreport.rest.DailyReportData;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.EmployeeorderService;

/**
 * The two rules by which a booking names its employee order (#1142): the REST API, where an id wins
 * without looking at the signs, and the CSV import of the UI, where the file belongs to the employee
 * of the selected contract and id and signs have to agree.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class BookingOrderResolverTest {

  private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

  @Mock
  private EmployeeService employeeService;
  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private EmployeeorderService employeeorderService;
  @Mock
  private AuthorizedEmployee authorizedEmployee;

  @InjectMocks
  private BookingOrderResolver resolver;

  private Employee employee;
  private Employeeorder order;

  @BeforeEach
  void setUp() {
    employee = employee(42L, "abc");
    var contract = contract(7L, employee);
    order = order(78L, contract, "4711", "01");

    when(employeeService.getEmployeeBySign("abc")).thenReturn(employee);
    when(employeecontractService.getEmployeeContractValidAt(42L, DAY)).thenReturn(contract);
    when(employeeorderService.getEmployeeorderById(78L)).thenReturn(order);
    when(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(7L, "4711/01", DAY)).thenReturn(List.of(order));
  }

  // the REST API

  @Test
  void the_api_names_the_order_by_suborder_and_employee_sign() {
    assertThat(resolver.resolve(bySigns("4711/01", "abc"), DAY)).isSameAs(order);
  }

  @Test
  void the_api_takes_the_id_without_looking_at_the_signs() {
    var booking = DailyReportData.builder().employeeorderId(78L).suborderSign("999/99").employeeSign("niemand").build();

    assertThat(resolver.resolve(booking, DAY)).isSameAs(order);
    verifyNoInteractions(employeeService, employeecontractService);
  }

  @Test
  void the_api_rejects_an_id_without_an_order() {
    var booking = DailyReportData.builder().employeeorderId(79L).build();

    assertRejected(() -> resolver.resolve(booking, DAY), TR_EMPLOYEE_ORDER_NOT_FOUND);
  }

  @Test
  void the_api_takes_the_authenticated_employee_where_the_sign_is_missing() {
    when(authorizedEmployee.getSign()).thenReturn("abc");

    assertThat(resolver.resolve(bySigns("4711/01", null), DAY)).isSameAs(order);
  }

  @Test
  void rejects_an_unknown_employee_sign() {
    assertRejected(() -> resolver.resolve(bySigns("4711/01", "xyz"), DAY), TR_BOOKING_EMPLOYEE_UNKNOWN);
  }

  @Test
  void rejects_a_booking_naming_no_order_at_all() {
    assertRejected(() -> resolver.resolve(bySigns(" ", "abc"), DAY), TR_BOOKING_ORDER_NOT_NAMED);
  }

  @Test
  void rejects_a_day_without_a_contract() {
    assertRejected(() -> resolver.resolve(bySigns("4711/01", "abc"), DAY.plusDays(1)), TR_BOOKING_NO_CONTRACT);
  }

  @Test
  void rejects_a_sign_without_an_order() {
    assertRejected(() -> resolver.resolve(bySigns("4711/1", "abc"), DAY), TR_BOOKING_NO_EMPLOYEE_ORDER);
  }

  @Test
  void rejects_a_sign_matching_several_orders() {
    when(employeeorderService.getEmployeeordersByCompleteOrderSignValidAt(7L, "4711/01", DAY))
        .thenReturn(List.of(order, order(80L, order.getEmployeecontract(), "4711", "01")));

    assertRejected(() -> resolver.resolve(bySigns("4711/01", "abc"), DAY), TR_BOOKING_AMBIGUOUS_EMPLOYEE_ORDER);
  }

  // the CSV import of the UI

  @Test
  void the_import_names_the_order_by_suborder_sign_for_the_selected_employee() {
    assertThat(resolver.resolveFor(employee, bySigns("4711/01", null), DAY)).isSameAs(order);
    verifyNoInteractions(authorizedEmployee);
  }

  @Test
  void the_import_accepts_an_id_with_a_fitting_suborder_sign() {
    var booking = DailyReportData.builder().employeeorderId(78L).suborderSign("4711/01").employeeSign("abc").build();

    assertThat(resolver.resolveFor(employee, booking, DAY)).isSameAs(order);
  }

  @Test
  void the_import_rejects_an_id_contradicting_the_suborder_sign() {
    var booking = DailyReportData.builder().employeeorderId(78L).suborderSign("4711/02").build();

    assertRejected(() -> resolver.resolveFor(employee, booking, DAY), TR_BOOKING_ORDER_CONTRADICTS_SIGN);
  }

  @Test
  void the_import_rejects_an_employee_sign_of_someone_else() {
    when(employeeService.getEmployeeBySign("def")).thenReturn(employee(43L, "def"));

    assertRejected(() -> resolver.resolveFor(employee, bySigns("4711/01", "def"), DAY), TR_BOOKING_OF_OTHER_EMPLOYEE);
  }

  @Test
  void the_import_rejects_an_id_whose_order_belongs_to_someone_else() {
    when(employeeorderService.getEmployeeorderById(90L)).thenReturn(order(90L, contract(8L, employee(43L, "def")), "4711", "01"));
    var booking = DailyReportData.builder().employeeorderId(90L).build();

    assertRejected(() -> resolver.resolveFor(employee, booking, DAY), TR_BOOKING_OF_OTHER_EMPLOYEE);
  }

  // fixtures

  private static void assertRejected(ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call).isInstanceOfSatisfying(InvalidDataException.class, ex ->
        assertThat(ex.getMessages()).singleElement().satisfies(message ->
            assertThat(message.getErrorCode()).isEqualTo(errorCode)));
  }

  private static DailyReportData bySigns(String suborderSign, String employeeSign) {
    return DailyReportData.builder().suborderSign(suborderSign).employeeSign(employeeSign).hours(1).build();
  }

  private static Employee employee(long id, String sign) {
    var employee = new Employee();
    ReflectionTestUtils.setField(employee, "id", id);
    employee.setSign(sign);
    return employee;
  }

  private static Employeecontract contract(long id, Employee employee) {
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", id);
    contract.setEmployee(employee);
    return contract;
  }

  private static Employeeorder order(long id, Employeecontract contract, String customerorderSign, String suborderSign) {
    var customerorder = new Customerorder();
    customerorder.setSign(customerorderSign);
    var suborder = new Suborder();
    suborder.setSign(suborderSign);
    suborder.setCustomerorder(customerorder);
    var order = new Employeeorder();
    ReflectionTestUtils.setField(order, "id", id);
    order.setSuborder(suborder);
    order.setEmployeecontract(contract);
    return order;
  }
}
