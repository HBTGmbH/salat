package de.hbt.salat.order.persistence;

import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.auth.EmployeeorderAuthorization;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;

/**
 * The employee order list asks the database for the rows the user may read (#1380) instead of dropping the others in
 * Java. The condition of the query, {@link EmployeeorderAuthorization#readable()}, must say what
 * {@link EmployeeorderAuthorization#isAuthorized} says with {@code READ}: for each role, the rows of the query are
 * exactly those the rule in Java lets through.
 */
@DataJpaTest
@Import({AuthorizedUserAuditorAware.class, EmployeeorderDAO.class, SuborderDAO.class,
    EmployeeorderAuthorization.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeeorderReadableTest {

  private static final LocalDate FROM = LocalDate.of(2020, 1, 1);

  @Autowired
  private EmployeeorderDAO employeeorderDAO;

  @Autowired
  private EmployeeorderAuthorization authorization;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");

    var lead = employee("pl");
    var suborder = suborder();
    employeeorder(contract(employee("own")), suborder);
    employeeorder(contract(employee("led"), lead), suborder);
    employeeorder(contract(employee("ldx"), employee("other"), lead), suborder);
    employeeorder(contract(employee("foreign"), employee("other2")), suborder);
    employeeorder(contract(lead), suborder);
  }

  @Test
  void a_manager_reads_every_employee_order() {
    as("mgr", true, false);

    assertThat(readByQuery()).containsExactlyInAnyOrder("own", "led", "ldx", "foreign", "pl");
    assertThat(readByQuery()).containsExactlyInAnyOrderElementsOf(readByRule());
  }

  @Test
  void a_people_lead_reads_their_own_and_those_of_the_contracts_they_supervise() {
    as("pl", false, true);

    assertThat(readByQuery()).containsExactlyInAnyOrder("led", "ldx", "pl");
    assertThat(readByQuery()).containsExactlyInAnyOrderElementsOf(readByRule());
  }

  /** Being named supervisor is not enough — without the role it gives no right to read. */
  @Test
  void a_supervisor_without_the_role_reads_only_their_own() {
    as("pl", false, false);

    assertThat(readByQuery()).containsExactly("pl");
    assertThat(readByQuery()).containsExactlyInAnyOrderElementsOf(readByRule());
  }

  @Test
  void an_employee_reads_only_their_own() {
    as("own", false, false);

    assertThat(readByQuery()).containsExactly("own");
    assertThat(readByQuery()).containsExactlyInAnyOrderElementsOf(readByRule());
  }

  private void as(String loginSign, boolean manager, boolean peopleLead) {
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(loginSign);
    when(authorizedUser.isManager()).thenReturn(manager);
    when(authorizedUser.isPeopleLead()).thenReturn(peopleLead);
  }

  private List<String> readByQuery() {
    return employeeorderDAO.findEmployeeorders(true, null, null, null, null, null, true, 500).rows().stream()
        .map(EmployeeorderReadableTest::signOf)
        .toList();
  }

  private List<String> readByRule() {
    return employeeorderDAO.getEmployeeorders().stream()
        .filter(employeeorder -> authorization.isAuthorized(employeeorder, READ))
        .map(EmployeeorderReadableTest::signOf)
        .toList();
  }

  private static String signOf(Employeeorder employeeorder) {
    return employeeorder.getEmployeecontract().getEmployee().getSign();
  }

  private Suborder suborder() {
    var customer = new Customer();
    customer.setName("Testkunde");
    customer.setShortname("TK");
    customer.setAddress("Teststraße 1");
    entityManager.persist(customer);

    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign("ORD");
    order.setDescription("Auftrag");
    order.setFromDate(FROM);
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    entityManager.persist(order);

    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setSign("01");
    suborder.setDescription("Unterauftrag");
    suborder.setFromDate(FROM);
    suborder.setDebithours(Duration.ZERO);
    return entityManager.persist(suborder);
  }

  private void employeeorder(Employeecontract contract, Suborder suborder) {
    var employeeorder = new Employeeorder();
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSuborder(suborder);
    employeeorder.setFromDate(FROM);
    employeeorder.setDebithours(Duration.ZERO);
    entityManager.persist(employeeorder);
    entityManager.flush();
  }

  private Employee employee(String sign) {
    var user = new SalatUser();
    user.setLoginname(sign);
    user.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
    entityManager.persist(user);

    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname(sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    employee.setSalatUser(user);
    return entityManager.persist(employee);
  }

  private Employeecontract contract(Employee employee, Employee... supervisors) {
    var created = new Employeecontract();
    created.setEmployee(employee);
    created.setValidFrom(FROM);
    created.setDailyWorkingTime(Duration.ofHours(8));
    created.setOvertimeStatic(Duration.ZERO);
    created.setHide(false);
    created.setSupervisors(List.of(supervisors));
    return entityManager.persist(created);
  }
}
