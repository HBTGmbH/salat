package org.tb.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.customer.domain.Customer;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employee_;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Employeecontract_;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractRepository;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Customerorder_;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.Suborder_;
import org.tb.order.persistence.CustomerorderRepository;
import org.tb.order.persistence.SuborderRepository;

/**
 * The query side of the rule from #1104, against a database: what {@link Hiding#notHidden} selects
 * has to be exactly what {@link Hiding#isHidden(Boolean)} says in Java — otherwise a select box and
 * the detail view of the same record disagree about whether anybody hid it.
 *
 * <p>The decisive case is the third state of the column: a row whose {@code hide} was never written
 * at all. It is created here by simply not calling {@code setHide} — the column is nullable, and
 * the schema default applies to an insert that omits the column, not to the {@code null} Hibernate
 * writes for an unset field. A {@code hide <> 1} is <em>unknown</em> for such a row in SQL and
 * drops it; every one of the entities below had a query that did exactly that.
 *
 * <p>Every entity carrying a {@code hide} flag is checked here, {@link Customer} included: it has
 * no {@code JpaSpecificationExecutor} and asks the same question as JPQL in its repository, which
 * has to give the same answer.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HidingSpecificationTest {

  private static final LocalDate FROM = LocalDate.of(2020, 1, 1);

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private CustomerRepository customerRepository;

  @Autowired
  private CustomerorderRepository customerorderRepository;

  @Autowired
  private SuborderRepository suborderRepository;

  @Autowired
  private EmployeeRepository employeeRepository;

  @Autowired
  private EmployeecontractRepository employeecontractRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customer customer;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    customer = customer("TK", false);
  }

  // --- die Java-Seite ---------------------------------------------------------------------------

  @Test
  void reads_a_flag_that_was_never_set_as_not_hidden() {
    assertThat(Hiding.isHidden(null)).isFalse();
    assertThat(Hiding.isHidden(false)).isFalse();
    assertThat(Hiding.isHidden(true)).isTrue();
  }

  // --- die Abfrageseite, je Entität -------------------------------------------------------------

  @Test
  void selects_the_customerorders_the_java_side_calls_not_hidden() {
    customerorder("hide-null", null);
    customerorder("hide-false", false);
    customerorder("hide-true", true);

    assertThat(customerorderRepository.findAll(Hiding.notHidden(Customerorder_.hide)))
        .extracting(Customerorder::getSign)
        .containsExactlyInAnyOrder("hide-null", "hide-false");
    assertThat(customerorderRepository.findAll(Hiding.notHidden(Customerorder_.hide)))
        .allSatisfy(co -> assertThat(co.getHide()).isFalse());
  }

  @Test
  void selects_the_suborders_the_java_side_calls_not_hidden() {
    var order = customerorder("co", false);
    suborder(order, "hide-null", null);
    suborder(order, "hide-false", false);
    suborder(order, "hide-true", true);

    assertThat(suborderRepository.findAll(Hiding.notHidden(Suborder_.hide)))
        .extracting(Suborder::getSign)
        .containsExactlyInAnyOrder("hide-null", "hide-false");
    assertThat(suborderRepository.findAll(Hiding.notHidden(Suborder_.hide)))
        .allSatisfy(so -> assertThat(so.isHide()).isFalse());
  }

  @Test
  void selects_the_employees_the_java_side_calls_not_hidden() {
    employee("hide-null", null);
    employee("hide-false", false);
    employee("hide-true", true);

    assertThat(employeeRepository.findAll(Hiding.notHidden(Employee_.hide)))
        .extracting(Employee::getSign)
        .containsExactlyInAnyOrder("hide-null", "hide-false");
    assertThat(employeeRepository.findAll(Hiding.notHidden(Employee_.hide)))
        .allSatisfy(e -> assertThat(e.getHide()).isFalse());
  }

  @Test
  void selects_the_employeecontracts_the_java_side_calls_not_hidden() {
    contract(employee("hide-null", false), null);
    contract(employee("hide-false", false), false);
    contract(employee("hide-true", false), true);

    assertThat(employeecontractRepository.findAll(Hiding.notHidden(Employeecontract_.hide)))
        .extracting(ec -> ec.getEmployee().getSign())
        .containsExactlyInAnyOrder("hide-null", "hide-false");
    assertThat(employeecontractRepository.findAllNotHidden())
        .extracting(ec -> ec.getEmployee().getSign())
        .containsExactlyInAnyOrder("hide-null", "hide-false");
  }

  @Test
  void selects_the_customers_the_java_side_calls_not_hidden() {
    customer("hide-null", null);
    customer("hide-false", false);
    customer("hide-true", true);

    assertThat(customerRepository.findAllVisibleOrderByShortnameIgnoreCase())
        .extracting(Customer::getShortname)
        .containsExactlyInAnyOrder("TK", "hide-null", "hide-false");
    assertThat(customerRepository.findAllVisibleOrderByShortnameIgnoreCase())
        .allSatisfy(c -> assertThat(c.getHide()).isFalse());
  }

  // --- Testdaten --------------------------------------------------------------------------------

  /** {@code hide} bleibt bei {@code null} ungesetzt — genau das ist der Fall, um den es geht. */
  private Customer customer(String shortname, Boolean hide) {
    var created = new Customer();
    created.setName("Testkunde " + shortname);
    created.setShortname(shortname);
    created.setAddress("Teststraße 1");
    if (hide != null) {
      created.setHide(hide);
    }
    return customerRepository.save(created);
  }

  private Customerorder customerorder(String sign, Boolean hide) {
    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign(sign);
    order.setDescription(sign);
    order.setFromDate(FROM);
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    if (hide != null) {
      order.setHide(hide);
    }
    return customerorderRepository.save(order);
  }

  private void suborder(Customerorder order, String sign, Boolean hide) {
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setSign(sign);
    suborder.setDescription(sign);
    suborder.setFromDate(FROM);
    suborder.setDebithours(Duration.ZERO);
    if (hide != null) {
      suborder.setHide(hide);
    }
    suborderRepository.save(suborder);
  }

  private Employee employee(String sign, Boolean hide) {
    var user = new SalatUser();
    user.setLoginname(sign);
    user.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
    entityManager.persist(user);

    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname-" + sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(user);
    if (hide != null) {
      employee.setHide(hide);
    }
    return employeeRepository.save(employee);
  }

  private void contract(Employee employee, Boolean hide) {
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(FROM);
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setOvertimeStatic(Duration.ZERO);
    if (hide != null) {
      contract.setHide(hide);
    }
    employeecontractRepository.save(contract);
  }
}
