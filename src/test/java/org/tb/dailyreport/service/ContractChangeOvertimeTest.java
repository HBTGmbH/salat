package org.tb.dailyreport.service;

import static java.time.DayOfWeek.MONDAY;
import static java.time.temporal.TemporalAdjusters.previousOrSame;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.auth.service.AuthService;
import org.tb.common.GlobalConstants;
import org.tb.common.SalatProperties;
import org.tb.common.command.CommandPublisher;
import org.tb.common.util.DateUtils;
import org.tb.common.web.UiState;
import org.tb.customer.domain.Customer;
import org.tb.customer.persistence.CustomerDAO;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.persistence.WorkingdayRepository;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.auth.EmployeecontractAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
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
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;
import org.tb.testutils.EmployeeTestUtils;

/**
 * Eine Buchung, die den Vertrag wechselt, verändert das Überstundenkonto beider Verträge (#1128).
 * {@code overtimeStatic} hält die Überstunden bis zum Abnahmedatum; eine Buchung, die den
 * abgenommenen Zeitraum ihres alten Vertrags verlässt, steckte darin, bis dieser Vertrag das nächste
 * Mal neu berechnet wurde. Die Tests prüfen deshalb den berechneten Wert, nicht nur, dass gerechnet
 * wird.
 *
 * <p>Jeder Vertrag hat acht Stunden am Tag, eine abgenommene Woche also ein Soll von 40 Stunden.
 * Die Tage liegen relativ zu heute, weil eine Buchung nur im laufenden Jahr und den beiden
 * benachbarten angenommen wird.
 */
@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({AuthorizedUserAuditorAware.class, SalatProperties.class, AuthService.class,
    EmployeeService.class, EmployeeDAO.class, EmployeeAuthorization.class,
    EmployeecontractService.class, EmployeecontractDAO.class, EmployeecontractAuthorization.class,
    EmployeeorderService.class, EmployeeorderDAO.class, EmployeeorderAuthorization.class,
    SuborderService.class, SuborderDAO.class, CustomerorderService.class, CustomerorderDAO.class,
    CustomerDAO.class, CommandPublisher.class,
    TimereportService.class, TimereportDAO.class, TimereportAuthorization.class, PublicholidayDAO.class,
    WorkingdayDAO.class, OvertimeService.class, MoveTimereportsService.class})
class ContractChangeOvertimeTest {

  private static final Duration DAILY_WORKING_TIME = Duration.ofHours(8);
  private static final Duration WEEK_TARGET = DAILY_WORKING_TIME.multipliedBy(5);

  private static final LocalDate FIRST_MONDAY = DateUtils.today().with(previousOrSame(MONDAY)).minusWeeks(5);
  private static final LocalDate FIRST_SUNDAY = FIRST_MONDAY.plusDays(6);
  private static final LocalDate SECOND_MONDAY = FIRST_MONDAY.plusWeeks(1);
  private static final LocalDate SECOND_SUNDAY = SECOND_MONDAY.plusDays(6);
  private static final LocalDate THIRD_MONDAY = FIRST_MONDAY.plusWeeks(2);
  private static final LocalDate THIRD_SUNDAY = THIRD_MONDAY.plusDays(6);

  private static final LocalDateTime RELEASED_AT = FIRST_MONDAY.plusWeeks(3).atTime(9, 0);
  private static final LocalDateTime ACCEPTED_AT = FIRST_MONDAY.plusWeeks(3).atTime(10, 0);

  @Autowired
  private EmployeeService employeeService;
  @Autowired
  private EmployeecontractService employeecontractService;
  @Autowired
  private TimereportService timereportService;
  @Autowired
  private OvertimeService overtimeService;
  @Autowired
  private MoveTimereportsService moveTimereportsService;
  @Autowired
  private TimereportRepository timereportRepository;
  @Autowired
  private WorkingdayRepository workingdayRepository;
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
  private Customerorder customerorder;
  private Suborder project;

  @BeforeEach
  void setUp() {
    // a manager who is not the booked person, so every status may be written
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn(EmployeeTestUtils.BOSS_SIGN);
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(EmployeeTestUtils.BOSS_SIGN);

    employee = EmployeeTestUtils.createEmployee(EmployeeTestUtils.TESTY_SIGN);
    employeeService.createOrUpdate(employee);
    supervisor = EmployeeTestUtils.createEmployee(EmployeeTestUtils.BOSS_SIGN);
    supervisor.getSalatUser().setStatus(GlobalConstants.EMPLOYEE_STATUS_PV);
    employeeService.createOrUpdate(supervisor);

    customerorder = customerorder();
    project = suborder("01", null);
  }

  @Test
  void a_booking_edited_into_the_next_contract_leaves_the_static_overtime_of_both_right() {
    long first = contract(FIRST_MONDAY, FIRST_SUNDAY, FIRST_SUNDAY, FIRST_SUNDAY);
    long second = contract(SECOND_MONDAY, null, SECOND_SUNDAY, SECOND_SUNDAY);
    long booking = book(first, employeeorder(first, project, FIRST_MONDAY, FIRST_SUNDAY), FIRST_MONDAY);
    long target = employeeorder(second, project, SECOND_MONDAY, null);
    recalculate(first, second);
    assertThat(overtimeStatic(first)).isEqualTo(Duration.ofHours(8).minus(WEEK_TARGET));
    assertThat(overtimeStatic(second)).isEqualTo(WEEK_TARGET.negated());

    moveBooking(booking, second, target, SECOND_MONDAY);

    assertThat(overtimeStatic(first)).isEqualTo(WEEK_TARGET.negated());
    assertThat(overtimeStatic(second)).isEqualTo(Duration.ofHours(8).minus(WEEK_TARGET));
  }

  @Test
  void a_booking_edited_into_the_next_contract_takes_the_status_of_that_contract() {
    long first = contract(FIRST_MONDAY, FIRST_SUNDAY, FIRST_SUNDAY, FIRST_SUNDAY);
    long second = contract(SECOND_MONDAY, null, SECOND_SUNDAY, null);
    long booking = book(first, employeeorder(first, project, FIRST_MONDAY, FIRST_SUNDAY), FIRST_MONDAY);
    long target = employeeorder(second, project, SECOND_MONDAY, null);
    releasedAndAccepted(booking);

    moveBooking(booking, second, target, SECOND_MONDAY);

    var moved = timereportRepository.findById(booking).orElseThrow();
    assertThat(moved.getEmployeecontract().getId()).isEqualTo(second);
    assertThat(moved.getStatus()).isEqualTo(TIMEREPORT_STATUS_COMMITED);
    assertThat(moved.getReleasedby()).isEqualTo(EmployeeTestUtils.TESTY_SIGN);
    assertThat(moved.getAcceptedby()).isNull();
    assertThat(moved.getAccepted()).isNull();
  }

  /** Bereitschaft zählt nicht als Arbeitszeit — der Status bleibt, das Konto nicht. */
  @Test
  void moving_a_booking_to_a_standby_suborder_takes_it_out_of_the_static_overtime() {
    long contract = contract(FIRST_MONDAY, FIRST_SUNDAY, FIRST_SUNDAY, FIRST_SUNDAY);
    var standby = suborder("02", OrderType.BEREITSCHAFT);
    long booking = book(contract, employeeorder(contract, project, FIRST_MONDAY, FIRST_SUNDAY), FIRST_MONDAY);
    employeeorder(contract, standby, FIRST_MONDAY, FIRST_SUNDAY);
    recalculate(contract);
    assertThat(overtimeStatic(contract)).isEqualTo(Duration.ofHours(8).minus(WEEK_TARGET));

    moveTimereportsService.move(project.getId(), standby.getId(), List.of(), FIRST_MONDAY, FIRST_SUNDAY);

    assertThat(overtimeStatic(contract)).isEqualTo(WEEK_TARGET.negated());
    assertThat(timereportRepository.findById(booking).orElseThrow().getStatus()).isEqualTo(TIMEREPORT_STATUS_CLOSED);
  }

  @Test
  void moving_a_booking_from_a_standby_suborder_puts_it_into_the_static_overtime() {
    long contract = contract(FIRST_MONDAY, FIRST_SUNDAY, FIRST_SUNDAY, FIRST_SUNDAY);
    var standby = suborder("02", OrderType.BEREITSCHAFT);
    book(contract, employeeorder(contract, standby, FIRST_MONDAY, FIRST_SUNDAY), FIRST_MONDAY);
    employeeorder(contract, project, FIRST_MONDAY, FIRST_SUNDAY);
    recalculate(contract);
    assertThat(overtimeStatic(contract)).isEqualTo(WEEK_TARGET.negated());

    moveTimereportsService.move(standby.getId(), project.getId(), List.of(), FIRST_MONDAY, FIRST_SUNDAY);

    assertThat(overtimeStatic(contract)).isEqualTo(Duration.ofHours(8).minus(WEEK_TARGET));
  }

  /**
   * Ein neuer Vertrag übernimmt die zweite Woche eines abgenommenen Vertrags. Freigabe- und
   * Abnahmedatum des alten Vertrags werden auf sein neues Ende gekürzt und wandern nicht mit; die
   * umgezogene Buchung ist danach offen und trägt weder Freigabe noch Abnahme.
   */
  @Test
  void a_new_contract_taking_over_an_accepted_period_opens_the_moved_bookings() {
    long old = contract(FIRST_MONDAY, null, SECOND_SUNDAY, SECOND_SUNDAY);
    long order = employeeorder(old, project, FIRST_MONDAY, null);
    book(old, order, FIRST_MONDAY);
    long moving = book(old, order, SECOND_MONDAY);
    releasedAndAccepted(moving);
    recalculate(old);
    assertThat(overtimeStatic(old)).isEqualTo(Duration.ofHours(16).minus(WEEK_TARGET.multipliedBy(2)));

    long taking = employeecontractService.createEmployeecontract(employee.getId(), SECOND_MONDAY, null,
        List.of(supervisor.getId()), "task", false, false, DAILY_WORKING_TIME, 30, Duration.ZERO, true).getId();

    var moved = timereportRepository.findById(moving).orElseThrow();
    assertThat(moved.getEmployeecontract().getId()).isEqualTo(taking);
    assertThat(moved.getStatus()).isEqualTo(TIMEREPORT_STATUS_OPEN);
    assertThat(moved.getReleasedby()).isNull();
    assertThat(moved.getReleased()).isNull();
    assertThat(moved.getAcceptedby()).isNull();
    assertThat(moved.getAccepted()).isNull();
    assertThat(overtimeStatic(old)).isEqualTo(Duration.ofHours(8).minus(WEEK_TARGET));
    assertThat(overtimeStatic(taking)).isEqualTo(Duration.ZERO);
  }

  /**
   * Ein bestehender, schon abgenommener Vertrag wird nach vorn verlängert. Die umgezogene Buchung
   * liegt in seinem abgenommenen Zeitraum und zählt danach in seinem Konto — das setzt voraus, dass
   * er erst nach dem Umzug neu berechnet wird.
   */
  @Test
  void an_accepted_contract_taking_over_a_period_closes_the_moved_bookings_and_counts_them() {
    long old = contract(FIRST_MONDAY, SECOND_SUNDAY, SECOND_SUNDAY, SECOND_SUNDAY);
    long oldOrder = employeeorder(old, project, FIRST_MONDAY, SECOND_SUNDAY);
    book(old, oldOrder, FIRST_MONDAY);
    long moving = book(old, oldOrder, SECOND_MONDAY);
    long taking = contract(THIRD_MONDAY, null, THIRD_SUNDAY, THIRD_SUNDAY);
    book(taking, employeeorder(taking, project, THIRD_MONDAY, null), THIRD_MONDAY);
    recalculate(old, taking);
    assertThat(overtimeStatic(old)).isEqualTo(Duration.ofHours(16).minus(WEEK_TARGET.multipliedBy(2)));
    assertThat(overtimeStatic(taking)).isEqualTo(Duration.ofHours(8).minus(WEEK_TARGET));

    employeecontractService.updateEmployeecontract(taking, SECOND_MONDAY, null, List.of(supervisor.getId()),
        "task", false, false, DAILY_WORKING_TIME, 30, true);

    var moved = timereportRepository.findById(moving).orElseThrow();
    assertThat(moved.getEmployeecontract().getId()).isEqualTo(taking);
    assertThat(moved.getStatus()).isEqualTo(TIMEREPORT_STATUS_CLOSED);
    assertThat(overtimeStatic(old)).isEqualTo(Duration.ofHours(8).minus(WEEK_TARGET));
    assertThat(overtimeStatic(taking)).isEqualTo(Duration.ofHours(16).minus(WEEK_TARGET.multipliedBy(2)));
  }

  private void moveBooking(long booking, long contract, long employeeorder, LocalDate day) {
    workingday(contract, day);
    timereportService.updateTimereport(booking, contract, employeeorder, day, "task", null, false, 8, 0);
  }

  private long contract(LocalDate validFrom, LocalDate validUntil, LocalDate releaseDate, LocalDate acceptanceDate) {
    long id = employeecontractService.createEmployeecontract(employee.getId(), validFrom, validUntil,
        List.of(supervisor.getId()), "task", false, false, DAILY_WORKING_TIME, 30, Duration.ZERO, false).getId();
    employeecontractService.updateReportReleaseData(id, releaseDate, acceptanceDate);
    return id;
  }

  private long book(long contract, long employeeorder, LocalDate day) {
    workingday(contract, day);
    timereportService.createTimereports(contract, employeeorder, day, "task", false, 8, 0, 1);
    return timereportRepository.findAllByEmployeecontractIdAndReferencedayRefdate(contract, day).getFirst().getId();
  }

  private void releasedAndAccepted(long booking) {
    Timereport timereport = timereportRepository.findById(booking).orElseThrow();
    timereport.setStatus(TIMEREPORT_STATUS_CLOSED);
    timereport.setReleasedby(EmployeeTestUtils.TESTY_SIGN);
    timereport.setReleased(RELEASED_AT);
    timereport.setAcceptedby(EmployeeTestUtils.BOSS_SIGN);
    timereport.setAccepted(ACCEPTED_AT);
    timereportRepository.save(timereport);
  }

  private void recalculate(long... contracts) {
    for (long contract : contracts) {
      overtimeService.updateOvertimeStatic(contract);
    }
  }

  private Duration overtimeStatic(long contract) {
    return employeecontractService.getEmployeecontractById(contract).getOvertimeStatic();
  }

  private void workingday(long contract, LocalDate day) {
    var workingday = new Workingday();
    workingday.setEmployeecontract(employeecontractService.getEmployeecontractById(contract));
    workingday.setRefday(day);
    workingday.setStarttimehour(8);
    workingdayRepository.save(workingday);
  }

  private long employeeorder(long contract, Suborder suborder, LocalDate from, LocalDate until) {
    Employeecontract employeecontract = employeecontractService.getEmployeecontractById(contract);
    var employeeorder = new Employeeorder();
    employeeorder.setEmployeecontract(employeecontract);
    employeeorder.setSuborder(suborder);
    employeeorder.setSign(" ");
    employeeorder.setFromDate(from);
    employeeorder.setUntilDate(until);
    employeeorder.setDebithours(Duration.ZERO);
    return employeeorderRepository.save(employeeorder).getId();
  }

  private Customerorder customerorder() {
    var customer = new Customer();
    customer.setShortname("cust");
    customer.setName("Customer");
    customer.setAddress("Teststraße 1");
    customerRepository.save(customer);

    var created = new Customerorder();
    created.setCustomer(customer);
    created.setSign("PROJECT");
    created.setDescription("Projekt");
    created.setFromDate(FIRST_MONDAY.minusYears(1));
    created.setOrderType(OrderType.STANDARD);
    created.setDebithours(Duration.ZERO);
    return customerorderRepository.save(created);
  }

  private Suborder suborder(String sign, OrderType orderType) {
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setDescription(sign);
    suborder.setFromDate(FIRST_MONDAY.minusYears(1));
    suborder.setOrderType(orderType);
    suborder.setDebithours(Duration.ZERO);
    return suborderRepository.save(suborder);
  }

}
