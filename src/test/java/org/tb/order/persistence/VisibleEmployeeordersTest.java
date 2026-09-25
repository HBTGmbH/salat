package org.tb.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.common.GlobalConstants;
import org.tb.customer.domain.Customer;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.auth.EmployeeorderAuthorization;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;

/**
 * Was die Mitarbeiterauftragsliste zeigt, wenn „verborgene anzeigen" aus ist (#1104).
 *
 * <p>{@link Employeeorder} hat kein eigenes {@code hide} und erbt die Sichtbarkeit von seinen
 * Eltern; das Prädikat prüft deshalb über zwei Joins {@code suborder.hide} und
 * {@code suborder.customerorder.hide}. Die Regel dahinter ist dieselbe wie für eine Entität mit
 * eigenem Flag und steht in {@code org.tb.common.Hiding}: {@code null} heißt nicht verborgen. Vor
 * #1104 stand hier zweimal {@code hide != true} — der Mitarbeiterauftrag unter einem Unterauftrag,
 * dessen Flag nie gesetzt wurde, fiel damit aus der Liste, obwohl ihn niemand verborgen hat.
 *
 * <p>Der Zeitfilter ist ausgeschaltet ({@code showInactive = true}), damit allein die Sichtbarkeit
 * über das Ergebnis entscheidet.
 */
@DataJpaTest
@Import({EmployeeorderDAO.class, SuborderDAO.class, AuthorizedUserAuditorAware.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class VisibleEmployeeordersTest {

  private static final LocalDate FROM = LocalDate.of(2020, 1, 1);

  @Autowired
  private EmployeeorderDAO employeeorderDAO;

  @Autowired
  private CustomerRepository customerRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoBean
  private EmployeeorderAuthorization employeeorderAuthorization;

  private Employeecontract contract;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    when(employeeorderAuthorization.isAuthorized(any(), any())).thenReturn(true);
    contract = contract();
  }

  /** Der Befund aus #1104: beide Eltern haben ihr Flag nie gesetzt. */
  @Test
  void offers_an_order_whose_parents_never_had_their_hide_flag_set() {
    employeeorder("never-set", suborder(customerorder("co", null), null));

    assertThat(visibleSigns()).containsExactly("never-set");
  }

  @Test
  void offers_an_order_whose_suborder_never_had_its_hide_flag_set() {
    employeeorder("suborder-null", suborder(customerorder("co", false), null));

    assertThat(visibleSigns()).containsExactly("suborder-null");
  }

  @Test
  void offers_an_order_whose_customerorder_never_had_its_hide_flag_set() {
    employeeorder("customerorder-null", suborder(customerorder("co", null), false));

    assertThat(visibleSigns()).containsExactly("customerorder-null");
  }

  @Test
  void leaves_out_an_order_under_a_hidden_suborder() {
    employeeorder("hidden-suborder", suborder(customerorder("co", false), true));

    assertThat(visibleSigns()).isEmpty();
  }

  @Test
  void leaves_out_an_order_under_a_hidden_customerorder() {
    employeeorder("hidden-customerorder", suborder(customerorder("co", true), false));

    assertThat(visibleSigns()).isEmpty();
  }

  /** Mit dem Schalter kommen die verborgenen zurück — das Prädikat entfällt dann ganz. */
  @Test
  void shows_the_hidden_ones_when_asked_for() {
    employeeorder("never-set", suborder(customerorder("co1", null), null));
    employeeorder("hidden-suborder", suborder(customerorder("co2", false), true));

    assertThat(employeeorderDAO
        .getEmployeeordersByFilters(true, null, null, null, null, null, true))
        .extracting(Employeeorder::getSign)
        .containsExactlyInAnyOrder("never-set", "hidden-suborder");
  }

  private List<String> visibleSigns() {
    return employeeorderDAO.getEmployeeordersByFilters(true, null, null, null, null, null, false)
        .stream()
        .map(Employeeorder::getSign)
        .toList();
  }

  /** {@code hide} bleibt bei {@code null} ungesetzt — genau das ist der Fall, um den es geht. */
  private Customerorder customerorder(String sign, Boolean hide) {
    var customer = new Customer();
    customer.setName("Testkunde " + sign);
    customer.setShortname(sign);
    customer.setAddress("Teststraße 1");
    customerRepository.save(customer);

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
    return entityManager.persist(order);
  }

  private Suborder suborder(Customerorder order, Boolean hide) {
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setSign("so-" + order.getSign());
    suborder.setDescription("Unterauftrag");
    suborder.setFromDate(FROM);
    suborder.setDebithours(Duration.ZERO);
    if (hide != null) {
      suborder.setHide(hide);
    }
    return entityManager.persist(suborder);
  }

  private void employeeorder(String sign, Suborder suborder) {
    var employeeorder = new Employeeorder();
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSuborder(suborder);
    employeeorder.setSign(sign);
    employeeorder.setFromDate(FROM);
    employeeorder.setDebithours(Duration.ZERO);
    entityManager.persist(employeeorder);
    entityManager.flush();
  }

  private Employeecontract contract() {
    var user = new SalatUser();
    user.setLoginname("test");
    user.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
    entityManager.persist(user);

    var employee = new Employee();
    employee.setSign("tst");
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    employee.setSalatUser(user);
    entityManager.persist(employee);

    var created = new Employeecontract();
    created.setEmployee(employee);
    created.setValidFrom(FROM);
    created.setDailyWorkingTime(Duration.ofHours(8));
    created.setOvertimeStatic(Duration.ZERO);
    created.setHide(false);
    return entityManager.persist(created);
  }
}
