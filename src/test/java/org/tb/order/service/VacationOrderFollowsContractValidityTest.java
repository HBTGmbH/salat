package org.tb.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.auth.service.AuthService;
import org.tb.common.GlobalConstants;
import org.tb.common.LocalDateRange;
import org.tb.common.SalatProperties;
import org.tb.common.command.CommandPublisher;
import org.tb.common.web.UiState;
import org.tb.customer.domain.Customer;
import org.tb.customer.persistence.CustomerDAO;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.auth.EmployeecontractAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.event.EmployeecontractChangedEvent;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.notification.service.NotificationService;
import org.tb.order.auth.EmployeeorderAuthorization;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.CustomerorderRepository;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.EmployeeorderRepository;
import org.tb.order.persistence.SuborderDAO;
import org.tb.order.persistence.SuborderRepository;
import org.tb.testutils.EmployeeTestUtils;

/**
 * Der Urlaubsauftrag folgt der Gueltigkeit des Vertrags in beide Richtungen (#565). Bis dahin
 * wurde er nur gekuerzt: nach einer Verlaengerung endete er weiter am alten Vertragsende und
 * behielt das anteilige Soll des kuerzeren Vertrags - im Urlaubskonto steht dann nach dem alten
 * Ende nicht mehr der Anspruch, sondern der tatsaechlich gebuchte Urlaub, also in aller Regel 0.
 */
@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({AuthorizedUserAuditorAware.class, SalatProperties.class, AuthService.class,
    EmployeeService.class, EmployeeDAO.class, EmployeeAuthorization.class,
    EmployeecontractService.class, EmployeecontractDAO.class, EmployeecontractAuthorization.class,
    EmployeeorderService.class, EmployeeorderDAO.class, EmployeeorderAuthorization.class,
    SuborderService.class, SuborderDAO.class, CustomerorderService.class, CustomerorderDAO.class,
    CustomerDAO.class, CommandPublisher.class})
public class VacationOrderFollowsContractValidityTest {

  private static final int YEAR = Year.now().getValue();
  private static final LocalDate YEAR_START = LocalDate.of(YEAR, 1, 1);
  private static final LocalDate APRIL = LocalDate.of(YEAR, 4, 1);
  private static final LocalDate HALF_YEAR = LocalDate.of(YEAR, 6, 30);
  private static final LocalDate YEAR_END = LocalDate.of(YEAR, 12, 31);
  private static final LocalDateRange WHOLE_YEAR = new LocalDateRange(YEAR_START, YEAR_END);
  private static final Duration DAILY_WORKING_TIME = Duration.ofHours(8);
  private static final int VACATION_DAYS = 30;
  private static final Duration FULL_ENTITLEMENT = DAILY_WORKING_TIME.multipliedBy(VACATION_DAYS);

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Autowired
  private EmployeeService employeeService;

  @Autowired
  private EmployeecontractService employeecontractService;

  @Autowired
  private EmployeeorderService employeeorderService;

  @Autowired
  private CustomerRepository customerRepository;

  @Autowired
  private CustomerorderRepository customerorderRepository;

  @Autowired
  private SuborderRepository suborderRepository;

  @Autowired
  private EmployeeorderRepository employeeorderRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoBean
  private UiState uiState;

  @MockitoBean
  private NotificationService notificationService;

  private Employee employee;
  private Employee supervisor;
  private Customer customer;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");

    employee = EmployeeTestUtils.createEmployee(EmployeeTestUtils.TESTY_SIGN);
    employeeService.createOrUpdate(employee);
    supervisor = EmployeeTestUtils.createEmployee(EmployeeTestUtils.BOSS_SIGN);
    supervisor.getSalatUser().setStatus(GlobalConstants.EMPLOYEE_STATUS_PV);
    employeeService.createOrUpdate(supervisor);

    customer = customer();
    vacationSuborder();
  }

  @Test
  public void extending_the_contract_extends_the_vacation_order_and_its_budget() {
    long contractId = contractWithStandardOrders(HALF_YEAR);
    assertThat(vacationOrderOf(contractId).getUntilDate()).isEqualTo(HALF_YEAR);
    assertThat(vacationOrderOf(contractId).getDebithours()).isEqualTo(FULL_ENTITLEMENT.dividedBy(2));

    updateContract(contractId, YEAR_START, YEAR_END);

    assertThat(vacationOrderOf(contractId).getUntilDate()).isEqualTo(YEAR_END);
    assertThat(vacationOrderOf(contractId).getDebithours()).isEqualTo(FULL_ENTITLEMENT);
  }

  @Test
  public void an_earlier_contract_begin_extends_the_vacation_order_and_its_budget() {
    long contractId = contractWithStandardOrders(APRIL, YEAR_END);
    assertThat(vacationOrderOf(contractId).getFromDate()).isEqualTo(APRIL);

    updateContract(contractId, YEAR_START, YEAR_END);

    assertThat(vacationOrderOf(contractId).getFromDate()).isEqualTo(YEAR_START);
    assertThat(vacationOrderOf(contractId).getDebithours()).isEqualTo(FULL_ENTITLEMENT);
  }

  /**
   * Das Ende des Mitarbeiterauftrags ist das Minimum aus Vertragsende und Ende des
   * Urlaubsunterauftrags: ein Vertrag, der ins nächste Jahr reicht, verlängert den Urlaubsauftrag
   * dieses Jahres nur bis zum Jahresende.
   */
  @Test
  public void the_vacation_order_never_outlives_its_suborder() {
    long contractId = contractWithStandardOrders(HALF_YEAR);

    updateContract(contractId, YEAR_START, YEAR_END.plusYears(1));

    assertThat(vacationOrderOf(contractId).getUntilDate()).isEqualTo(YEAR_END);
    assertThat(vacationOrderOf(contractId).getDebithours()).isEqualTo(FULL_ENTITLEMENT);
  }

  @Test
  public void shortening_the_contract_still_reduces_the_vacation_order_and_its_budget() {
    long contractId = contractWithStandardOrders(YEAR_END);
    assertThat(vacationOrderOf(contractId).getDebithours()).isEqualTo(FULL_ENTITLEMENT);

    updateContract(contractId, YEAR_START, HALF_YEAR);

    assertThat(vacationOrderOf(contractId).getUntilDate()).isEqualTo(HALF_YEAR);
    assertThat(vacationOrderOf(contractId).getDebithours()).isEqualTo(FULL_ENTITLEMENT.dividedBy(2));
  }

  /**
   * Ein Projektauftrag ist eine Zusage auf einen ausgehandelten Zeitraum. Waechst er mit dem
   * Vertrag mit, ist eine Buchungsberechtigung erteilt, die niemand vergeben hat.
   */
  @Test
  public void extending_the_contract_leaves_a_project_order_where_it_is() {
    long contractId = contractWithStandardOrders(HALF_YEAR);
    var projectOrder = projectOrder(contractId, YEAR_START, HALF_YEAR);

    updateContract(contractId, YEAR_START, YEAR_END);

    var reloaded = employeeorderService.getEmployeeorderById(projectOrder.getId());
    assertThat(reloaded.getUntilDate()).isEqualTo(HALF_YEAR);
  }

  private long contractWithStandardOrders(LocalDate validUntil) {
    return contractWithStandardOrders(YEAR_START, validUntil);
  }

  private long contractWithStandardOrders(LocalDate validFrom, LocalDate validUntil) {
    long contractId = employeecontractService.createEmployeecontract(
        employee.getId(), validFrom, validUntil, List.of(supervisor.getId()),
        "task", false, false, DAILY_WORKING_TIME, VACATION_DAYS, Duration.ZERO, false).getId();
    eventPublisher.publishEvent(new EmployeecontractChangedEvent(this, contractId));
    return contractId;
  }

  private void updateContract(long contractId, LocalDate validFrom, LocalDate validUntil) {
    employeecontractService.updateEmployeecontract(
        contractId, validFrom, validUntil, List.of(supervisor.getId()),
        "task", false, false, DAILY_WORKING_TIME, VACATION_DAYS, false);
  }

  private Employeeorder vacationOrderOf(long contractId) {
    var orders = employeeorderService.getVacationEmployeeOrders(contractId, WHOLE_YEAR);
    assertThat(orders).hasSize(1);
    return orders.getFirst();
  }

  private Employeeorder projectOrder(long contractId, LocalDate from, LocalDate until) {
    var suborder = suborder(customerorder("PROJECT", "Projekt"), "01", "Entwicklung", false);
    var employeeorder = new Employeeorder();
    employeeorder.setEmployeecontract(employeecontractService.getEmployeecontractById(contractId));
    employeeorder.setSuborder(suborder);
    employeeorder.setSign(" ");
    employeeorder.setFromDate(from);
    employeeorder.setUntilDate(until);
    employeeorder.setDebithours(Duration.ZERO);
    return employeeorderRepository.save(employeeorder);
  }

  private Customer customer() {
    var created = new Customer();
    created.setShortname("cust");
    created.setName("Customer");
    created.setAddress("Teststraße 1");
    return customerRepository.save(created);
  }

  private void vacationSuborder() {
    suborder(customerorder(GlobalConstants.CUSTOMERORDER_SIGN_VACATION, "Urlaub"),
        String.valueOf(YEAR), "Urlaub " + YEAR, true);
  }

  private Customerorder customerorder(String sign, String description) {
    var customerorder = new Customerorder();
    customerorder.setCustomer(customer);
    customerorder.setSign(sign);
    customerorder.setDescription(description);
    customerorder.setFromDate(YEAR_START);
    customerorder.setUntilDate(YEAR_END);
    customerorder.setOrderType(OrderType.STANDARD);
    customerorder.setDebithours(Duration.ZERO);
    return customerorderRepository.save(customerorder);
  }

  private Suborder suborder(Customerorder customerorder, String sign, String description, boolean standard) {
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setDescription(description);
    suborder.setFromDate(YEAR_START);
    suborder.setUntilDate(YEAR_END);
    suborder.setStandard(standard);
    suborder.setDebithours(Duration.ZERO);
    return suborderRepository.save(suborder);
  }

}
