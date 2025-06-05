package org.tb.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;

@ExtendWith(MockitoExtension.class)
class EmployeeorderServiceTest {

  @Mock
  private EmployeeorderDAO employeeorderDAO;

  @Mock
  private EmployeeService employeeService;

  @Mock
  private EmployeecontractService employeecontractService;

  @InjectMocks
  private EmployeeorderService employeeorderService;

  @Test
  void shouldReturnEmployeeOrderWhenEmployeeAndSuborderMatch() {

    String employeeSign = "EMP001";
    String suborderSign = "CUSTOMERORDER001/SUB001";
    LocalDate date = LocalDate.now();

    Employee employee = new Employee();
    setField(employee, "id", 1L);
    employee.setSign(employeeSign);

    Employeecontract employeecontract = new Employeecontract();
    setField(employeecontract, "id", 10L);
    employeecontract.setEmployee(employee);

    Customerorder customerorder = new Customerorder();
    customerorder.setSign("CUSTOMERORDER001");
    Suborder suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign("SUB001");

    Employeeorder expectedOrder = new Employeeorder();
    expectedOrder.setSuborder(suborder);
    expectedOrder.setEmployeecontract(employeecontract);

    when(employeeService.getEmployeeBySign(employeeSign)).thenReturn(employee);
    when(employeecontractService.getEmployeeContractValidAt(employee.getId(), date)).thenReturn(employeecontract);
    when(employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(employeecontract.getId(), date))
        .thenReturn(List.of(expectedOrder));

    // Act
    Employeeorder result = employeeorderService.getEmployeeorderByEmployeeAndSuborder(employeeSign, suborderSign, date);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedOrder);
  }

  @Test
  void shouldReturnNullWhenNoMatchingEmployeeOrder() {
    // Arrange
    String employeeSign = "EMP002";
    String suborderSign = "CUSTOMERORDER001/SUB002";
    LocalDate date = LocalDate.now();

    Employee employee = new Employee();
    setField(employee, "id", 2L);
    employee.setSign(employeeSign);

    Employeecontract employeecontract = new Employeecontract();
    setField(employeecontract, "id", 20L);
    employeecontract.setEmployee(employee);

    when(employeeService.getEmployeeBySign(employeeSign)).thenReturn(employee);
    when(employeecontractService.getEmployeeContractValidAt(employee.getId(), date)).thenReturn(employeecontract);
    when(employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(employeecontract.getId(), date))
        .thenReturn(List.of());

    // Act
    Employeeorder result = employeeorderService.getEmployeeorderByEmployeeAndSuborder(employeeSign, suborderSign, date);

    // Assert
    assertThat(result).isNull();
  }

  @Test
  void shouldReturnCorrectEmployeeOrderWhenMultipleOrdersPresent() {
    // Arrange
    String employeeSign = "EMP003";
    String suborderSign = "CUSTOMERORDER001/SUB003A";
    LocalDate date = LocalDate.now();

    Employee employee = new Employee();
    setField(employee, "id", 3L);
    employee.setSign(employeeSign);

    Employeecontract employeecontract = new Employeecontract();
    setField(employeecontract, "id", 30L);
    employeecontract.setEmployee(employee);

    Customerorder customerorder = new Customerorder();
    customerorder.setSign("CUSTOMERORDER001");

    Suborder suborder1 = new Suborder();
    suborder1.setCustomerorder(customerorder);
    suborder1.setSign("SUB003A");
    Employeeorder order1 = new Employeeorder();
    order1.setSuborder(suborder1);
    order1.setEmployeecontract(employeecontract);

    Suborder suborder2 = new Suborder();
    suborder2.setCustomerorder(customerorder);
    suborder2.setSign("SUB003AA");
    Employeeorder order2 = new Employeeorder();
    order2.setSuborder(suborder2);
    order2.setEmployeecontract(employeecontract);

    when(employeeService.getEmployeeBySign(employeeSign)).thenReturn(employee);
    when(employeecontractService.getEmployeeContractValidAt(employee.getId(), date)).thenReturn(employeecontract);
    when(employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(employeecontract.getId(), date))
        .thenReturn(List.of(order2, order1));

    // Act
    Employeeorder result = employeeorderService.getEmployeeorderByEmployeeAndSuborder(employeeSign, suborderSign, date);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(order1);
  }

  @Test
  void shouldReturnCorrectOrderForMultipleSubordersWithOverlappingSigns () {
    // Arrange
    String employeeSign = "EMP005";
    String suborderSign = "CUSTOMERORDER003/SUB005A";
    LocalDate date = LocalDate.now();

    Employee employee = new Employee();
    setField(employee, "id", 5L);
    employee.setSign(employeeSign);

    Employeecontract employeecontract = new Employeecontract();
    setField(employeecontract, "id", 50L);
    employeecontract.setEmployee(employee);

    Customerorder customerorder = new Customerorder();
    customerorder.setSign("CUSTOMERORDER003");

    Suborder suborder1 = new Suborder();
    suborder1.setCustomerorder(customerorder);
    suborder1.setSign("SUB005A");
    Employeeorder order1 = new Employeeorder();
    order1.setSuborder(suborder1);
    order1.setEmployeecontract(employeecontract);

    Suborder suborder2 = new Suborder();
    suborder2.setCustomerorder(customerorder);
    suborder2.setSign("SUB005");
    Employeeorder order2 = new Employeeorder();
    order2.setSuborder(suborder2);
    order2.setEmployeecontract(employeecontract);

    when(employeeService.getEmployeeBySign(employeeSign)).thenReturn(employee);
    when(employeecontractService.getEmployeeContractValidAt(employee.getId(), date)).thenReturn(employeecontract);
    when(employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(employeecontract.getId(), date))
        .thenReturn(List.of(order1, order2));

    // Act
    Employeeorder result = employeeorderService.getEmployeeorderByEmployeeAndSuborder(employeeSign, suborderSign,
        date);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(order1);
  }

  @Test
  void shouldNormalizeOrderSignWithSpecialCharacters () {
    // Arrange
    String employeeSign = "EMP006";
    String suborderSign = "CUSTOMERORDER004/SUB-006";
    LocalDate date = LocalDate.now();

    Employee employee = new Employee();
    setField(employee, "id", 6L);
    employee.setSign(employeeSign);

    Employeecontract employeecontract = new Employeecontract();
    setField(employeecontract, "id", 60L);
    employeecontract.setEmployee(employee);

    Customerorder customerorder = new Customerorder();
    customerorder.setSign("CUSTOMERORDER004");

    Suborder suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign("SUB.006");
    Employeeorder expectedOrder = new Employeeorder();
    expectedOrder.setSuborder(suborder);
    expectedOrder.setEmployeecontract(employeecontract);

    when(employeeService.getEmployeeBySign(employeeSign)).thenReturn(employee);
    when(employeecontractService.getEmployeeContractValidAt(employee.getId(), date)).thenReturn(employeecontract);
    when(employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(employeecontract.getId(), date))
        .thenReturn(List.of(expectedOrder));

    // Act
    Employeeorder result = employeeorderService.getEmployeeorderByEmployeeAndSuborder(employeeSign, suborderSign,
        date);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expectedOrder);
  }

  @Test
  void shouldReturnNullWhenNoEmployeeFound() {
    // Arrange
    String employeeSign = "NON_EXISTENT_EMP";
    String suborderSign = "SUB004";
    LocalDate date = LocalDate.now();

    when(employeeService.getEmployeeBySign(employeeSign)).thenReturn(null);

    // Act
    Employeeorder result = employeeorderService.getEmployeeorderByEmployeeAndSuborder(employeeSign, suborderSign, date);

    // Assert
    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullWhenEmployeeContractNotValidOnGivenDate () {
    // Arrange
    String employeeSign = "EMP004";
    String suborderSign = "CUSTOMERORDER002/SUB004";
    LocalDate date = LocalDate.now();

    Employee employee = new Employee();
    setField(employee, "id", 4L);
    employee.setSign(employeeSign);

    when(employeeService.getEmployeeBySign(employeeSign)).thenReturn(employee);
    when(employeecontractService.getEmployeeContractValidAt(employee.getId(), date)).thenReturn(null);

    // Act
    Employeeorder result = employeeorderService.getEmployeeorderByEmployeeAndSuborder(employeeSign, suborderSign,
        date);

    // Assert
    assertThat(result).isNull();
  }

}