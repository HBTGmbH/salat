package de.hbt.salat.order.service;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
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
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.web.UiState;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.customer.persistence.CustomerRepository;
import de.hbt.salat.employee.auth.EmployeeAuthorization;
import de.hbt.salat.employee.auth.EmployeecontractAuthorization;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.event.EmployeecontractChangedEvent;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.notification.service.NotificationService;
import de.hbt.salat.order.auth.EmployeeorderAuthorization;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.EmployeeorderDAO;
import de.hbt.salat.order.persistence.EmployeeorderRepository;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.persistence.SuborderRepository;
import de.hbt.salat.testutils.EmployeeTestUtils;

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
    CustomerDAO.class, CommandPublisher.class, SpecialOrders.class})
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
  private static final String VACATION_SIGN = "URLAUB";
  private static final String SPECIAL_LEAVE_SIGN = "Sonderurlaub";

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

  @Autowired
  private SalatProperties salatProperties;

  @Autowired
  private SpecialOrders specialOrders;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @MockitoBean
  private UiState uiState;

  @MockitoBean
  private NotificationService notificationService;

  private Employee employee;
  private Employee supervisor;
  private Customer customer;
  private Suborder yearlySuborder;
  private Suborder specialLeave;

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

  /**
   * Special leave has no entitlement (#1341). Shortening a contract used to recalculate it like a
   * yearly suborder and parse its sign as the year — the change of the contract failed.
   */
  @Test
  public void shortening_the_contract_cuts_special_leave_without_calculating_an_entitlement() {
    long contractId = contractWithStandardOrders(YEAR_END);
    var special = orderOn(contractId, specialLeave, YEAR_START, YEAR_END, Duration.ZERO);

    updateContract(contractId, YEAR_START, HALF_YEAR);

    var reloaded = employeeorderService.getEmployeeorderById(special.getId());
    assertThat(reloaded.getUntilDate()).isEqualTo(HALF_YEAR);
    assertThat(reloaded.getDebithours()).isEqualTo(Duration.ZERO);
  }

  /** The year of the entitlement is the year the suborder begins; its sign means nothing (#1341). */
  @Test
  public void the_entitlement_is_that_of_the_year_the_suborder_begins_whatever_its_sign() {
    yearlySuborder.setSign("Urlaub dieses Jahres");
    suborderRepository.save(yearlySuborder);

    long contractId = contractWithStandardOrders(HALF_YEAR);

    assertThat(vacationOrderOf(contractId).getDebithours()).isEqualTo(FULL_ENTITLEMENT.dividedBy(2));
  }

  /** Created by hand without a debit, the order gets what the automatic creation would give it (#1341). */
  @Test
  public void a_vacation_order_created_by_hand_without_a_debit_gets_the_calculated_entitlement() {
    long contractId = contractWithoutStandardOrders(HALF_YEAR);

    employeeorderService.create(newOrder(contractId, yearlySuborder, Duration.ZERO));

    assertThat(vacationOrderOf(contractId).getDebithours()).isEqualTo(FULL_ENTITLEMENT.dividedBy(2));
  }

  /** A debit a manager enters stays — until the next change of the contract calculates it again. */
  @Test
  public void a_debit_entered_by_hand_stays_until_the_contract_changes() {
    long contractId = contractWithoutStandardOrders(YEAR_END);

    employeeorderService.create(newOrder(contractId, yearlySuborder, Duration.ofHours(10)));
    assertThat(vacationOrderOf(contractId).getDebithours()).isEqualTo(Duration.ofHours(10));

    updateContract(contractId, YEAR_START, HALF_YEAR);
    assertThat(vacationOrderOf(contractId).getDebithours()).isEqualTo(FULL_ENTITLEMENT.dividedBy(2));
  }

  private long contractWithoutStandardOrders(LocalDate validUntil) {
    return employeecontractService.createEmployeecontract(
        employee.getId(), YEAR_START, validUntil, List.of(supervisor.getId()),
        "task", false, false, DAILY_WORKING_TIME, VACATION_DAYS, Duration.ZERO, false).getId();
  }

  private Employeeorder newOrder(long contractId, Suborder suborder, Duration debithours) {
    var employeeorder = new Employeeorder();
    employeeorder.setEmployeecontract(employeecontractService.getEmployeecontractById(contractId));
    employeeorder.setSuborder(suborder);
    employeeorder.setFromDate(YEAR_START);
    employeeorder.setUntilDate(employeecontractService.getEmployeecontractById(contractId).getValidUntil());
    employeeorder.setDebithours(debithours);
    return employeeorder;
  }

  private Employeeorder orderOn(long contractId, Suborder suborder, LocalDate from, LocalDate until,
                                Duration debithours) {
    var employeeorder = newOrder(contractId, suborder, debithours);
    employeeorder.setFromDate(from);
    employeeorder.setUntilDate(until);
    return employeeorderRepository.save(employeeorder);
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
    employeeorder.setFromDate(from);
    employeeorder.setUntilDate(until);
    employeeorder.setDebithours(Duration.ZERO);
    return employeeorderRepository.save(employeeorder);
  }

  private Customer customer() {
    var created = new Customer();
    created.setShortname(uniqueShortname("cust"));
    created.setName("Customer");
    created.setAddress("Teststraße 1");
    return customerRepository.save(created);
  }

  /**
   * The vacation order with its yearly suborder and special leave, named as the special orders the
   * way {@code application.yaml} names them in operation (#1341).
   */
  private void vacationSuborder() {
    var vacationOrder = customerorder(VACATION_SIGN, "Urlaub");
    yearlySuborder = suborder(vacationOrder, String.valueOf(YEAR), "Urlaub " + YEAR, true);
    specialLeave = suborder(vacationOrder, SPECIAL_LEAVE_SIGN, "Sonderurlaub", false);
    salatProperties.getVacation().setCustomerorderSign(VACATION_SIGN);
    salatProperties.getVacation().setDoNotCalculateSigns(List.of(VACATION_SIGN + "/" + SPECIAL_LEAVE_SIGN));
    specialOrders.resolve();
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
