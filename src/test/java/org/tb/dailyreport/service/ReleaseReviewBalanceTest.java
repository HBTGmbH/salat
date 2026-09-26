package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.tb.common.test.FixedClock;
import org.tb.common.util.ClockProvider;
import org.tb.common.web.UiState;
import org.tb.customer.domain.Customer;
import org.tb.customer.persistence.CustomerDAO;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.dailyreport.domain.OvertimeBalance;
import org.tb.dailyreport.domain.OvertimeReportMonth;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.PublicholidayRepository;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.persistence.WorkingdayRepository;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.auth.EmployeecontractAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Overtime;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.persistence.OvertimeRepository;
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
 * Die Differenz der Übersicht vor der Freigabe stimmt mit der Veränderung des Überstundenkontos
 * über denselben Zeitraum überein (#760). Die Tests rechnen gegen die echte Datenbank und die
 * echte Überstundenrechnung, denn genau deren Übereinstimmung ist die Zusage.
 *
 * <p>Der Vertrag beginnt am 01.06.2026 mit acht Stunden am Tag und ist bis zum 30.06.2026
 * freigegeben; der Zeitraum reicht über Juli und August 2026. Darin liegen ein Feiertag, eine
 * Abwesenheit, eine Bereitschaft und eine Überstundenanpassung, davor eine Buchung und eine
 * zweite Anpassung — beide dürfen die Differenz nicht berühren. Juli und August 2026 haben 44
 * Werktage, ohne den Feiertag 43, das Soll ist also 344 Stunden.
 */
@DataJpaTest
@FixedClock("2026-09-15T10:00:00")
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({AuthorizedUserAuditorAware.class, SalatProperties.class, AuthService.class,
    EmployeeService.class, EmployeeDAO.class, EmployeeAuthorization.class,
    EmployeecontractService.class, EmployeecontractDAO.class, EmployeecontractAuthorization.class,
    EmployeeorderService.class, EmployeeorderDAO.class, EmployeeorderAuthorization.class,
    SuborderService.class, SuborderDAO.class, CustomerorderService.class, CustomerorderDAO.class,
    CustomerDAO.class, CommandPublisher.class,
    TimereportService.class, TimereportDAO.class, TimereportAuthorization.class, PublicholidayDAO.class,
    WorkingdayDAO.class, OvertimeService.class})
class ReleaseReviewBalanceTest {

  private static final Duration DAILY_WORKING_TIME = Duration.ofHours(8);
  private static final LocalDate CONTRACT_START = LocalDate.of(2026, 6, 1);
  private static final LocalDate RELEASED_UNTIL = LocalDate.of(2026, 6, 30);
  private static final LocalDate BEGIN = LocalDate.of(2026, 7, 1);
  private static final LocalDate END = LocalDate.of(2026, 8, 31);
  private static final LocalDate HOLIDAY = LocalDate.of(2026, 7, 15);

  private static final Duration TARGET = DAILY_WORKING_TIME.multipliedBy(43);
  private static final Duration WORKING_TIME = Duration.ofHours(8 + 8 + 6);
  private static final Duration STANDBY = Duration.ofHours(4);
  private static final Duration ADJUSTMENT = Duration.ofHours(2);

  @Autowired
  private EmployeeService employeeService;
  @Autowired
  private EmployeecontractService employeecontractService;
  @Autowired
  private TimereportService timereportService;
  @Autowired
  private OvertimeService overtimeService;
  @Autowired
  private WorkingdayRepository workingdayRepository;
  @Autowired
  private PublicholidayRepository publicholidayRepository;
  @Autowired
  private OvertimeRepository overtimeRepository;
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

  private final Set<LocalDate> workingdays = new HashSet<>();
  private Employee employee;
  private Employee supervisor;
  private Customerorder customerorder;
  private long contract;

  @BeforeEach
  void setUp() {
    // a manager who is not the booked person, so bookings may be written in every period
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
    publicholidayRepository.save(new Publicholiday(HOLIDAY, "Testfeiertag"));

    contract = contract(employee, DAILY_WORKING_TIME);
    var project = employeeorder(contract, suborder("01", null));
    var standby = employeeorder(contract, suborder("02", OrderType.BEREITSCHAFT));
    var absence = employeeorder(contract, suborder("03", OrderType.KRANK_URLAUB_ABWESEND));

    // before the period: released already, and part of the account before the period begins
    book(contract, project, LocalDate.of(2026, 6, 15), 8);
    adjustment(contract, CONTRACT_START, Duration.ofHours(-3));

    // the period
    book(contract, project, LocalDate.of(2026, 7, 1), 8);
    book(contract, absence, LocalDate.of(2026, 7, 2), 8);
    book(contract, standby, LocalDate.of(2026, 7, 2), 4);
    book(contract, project, LocalDate.of(2026, 8, 3), 6);
    adjustment(contract, LocalDate.of(2026, 8, 10), ADJUSTMENT);
  }

  @Test
  void the_balance_names_target_working_time_and_adjustment_of_the_period() {
    var balance = overtimeService.calculateOvertimeBalance(contract, BEGIN, END).orElseThrow();

    assertThat(balance.target()).as("Soll ohne den Feiertag").isEqualTo(TARGET);
    assertThat(balance.workingTime()).as("gebucht, Abwesenheit ja, Bereitschaft nein").isEqualTo(WORKING_TIME);
    assertThat(balance.adjustment()).as("nur die Anpassung im Zeitraum").isEqualTo(ADJUSTMENT);
  }

  @Test
  void the_difference_is_working_time_minus_target_plus_adjustment() {
    var balance = overtimeService.calculateOvertimeBalance(contract, BEGIN, END).orElseThrow();

    assertThat(balance.diff()).isEqualTo(WORKING_TIME.minus(TARGET).plus(ADJUSTMENT));
  }

  @Test
  void the_difference_is_the_change_of_the_overtime_account_over_the_period() {
    var balance = overtimeService.calculateOvertimeBalance(contract, BEGIN, END).orElseThrow();

    assertThat(balance.diff()).isEqualTo(overtimeAccountAt(END).minus(overtimeAccountAt(BEGIN.minusDays(1))));
  }

  /** Die Monatsübersicht des Überstundenkontos rechnet über einen eigenen Weg — und kommt auf dasselbe. */
  @Test
  void the_difference_is_the_sum_of_the_months_of_the_overtime_report() {
    var balance = overtimeService.calculateOvertimeBalance(contract, BEGIN, END).orElseThrow();

    var months = overtimeService.createDetailedReportForEmployee(contract, false).getMonths().stream()
        .filter(month -> !month.getYearMonth().isBefore(YearMonth.from(BEGIN)) && !month.getYearMonth().isAfter(YearMonth.from(END)))
        .map(OvertimeReportMonth::getDiff)
        .reduce(Duration.ZERO, Duration::plus);
    assertThat(balance.diff()).isEqualTo(months);
  }

  @Test
  void the_overtime_of_the_period_is_the_difference_of_the_balance() {
    assertThat(overtimeService.calculateOvertime(contract, BEGIN, END))
        .isEqualTo(overtimeService.calculateOvertimeBalance(contract, BEGIN, END).map(OvertimeBalance::diff));
  }

  @Test
  void a_contract_without_daily_working_time_has_no_balance() {
    var another = EmployeeTestUtils.createEmployee("zz");
    employeeService.createOrUpdate(another);
    long withoutTarget = contract(another, Duration.ZERO);

    assertThat(overtimeService.calculateOvertimeBalance(withoutTarget, BEGIN, END)).isEmpty();
  }

  /** The total of the overtime account as it reads on the day after {@code date}, i.e. up to {@code date}. */
  private Duration overtimeAccountAt(LocalDate date) {
    ClockProvider.useFixedClock(date.plusDays(1).atTime(10, 0));
    return overtimeService.calculateOvertime(contract, false).orElseThrow().getTotal().getDuration();
  }

  private long contract(Employee employee, Duration dailyWorkingTime) {
    long id = employeecontractService.createEmployeecontract(employee.getId(), CONTRACT_START, null,
        List.of(supervisor.getId()), "task", false, false, dailyWorkingTime, 30, Duration.ZERO, false).getId();
    employeecontractService.updateReportReleaseData(id, RELEASED_UNTIL, null);
    return id;
  }

  private void book(long contract, long employeeorder, LocalDate day, int hours) {
    if (workingdays.add(day)) {
      var workingday = new Workingday();
      workingday.setEmployeecontract(employeecontractService.getEmployeecontractById(contract));
      workingday.setRefday(day);
      workingday.setStarttimehour(8);
      workingday.setBreakminutes(45);
      workingdayRepository.save(workingday);
    }
    timereportService.createTimereports(contract, employeeorder, day, "task", false, hours, 0, 1);
  }

  private void adjustment(long contract, LocalDate effective, Duration time) {
    var overtime = new Overtime();
    overtime.setEmployeecontract(employeecontractService.getEmployeecontractById(contract));
    overtime.setComment("Anpassung");
    overtime.setEffective(effective);
    overtime.setTime(time);
    overtimeRepository.save(overtime);
  }

  private long employeeorder(long contract, Suborder suborder) {
    Employeecontract employeecontract = employeecontractService.getEmployeecontractById(contract);
    var employeeorder = new Employeeorder();
    employeeorder.setEmployeecontract(employeecontract);
    employeeorder.setSuborder(suborder);
    employeeorder.setSign(" ");
    employeeorder.setFromDate(CONTRACT_START);
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
    created.setFromDate(CONTRACT_START.minusYears(1));
    created.setOrderType(OrderType.STANDARD);
    created.setDebithours(Duration.ZERO);
    return customerorderRepository.save(created);
  }

  private Suborder suborder(String sign, OrderType orderType) {
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setDescription(sign);
    suborder.setFromDate(CONTRACT_START.minusYears(1));
    suborder.setOrderType(orderType);
    suborder.setDebithours(Duration.ZERO);
    return suborderRepository.save(suborder);
  }
}
