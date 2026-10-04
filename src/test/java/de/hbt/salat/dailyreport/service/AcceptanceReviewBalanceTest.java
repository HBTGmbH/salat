package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_COMMITTED;

import java.time.Duration;
import java.time.LocalDate;
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
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.service.MailService;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.web.UiState;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.customer.persistence.CustomerRepository;
import de.hbt.salat.dailyreport.auth.ReleaseAuthorization;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.Publicholiday;
import de.hbt.salat.dailyreport.domain.ReviewPeriod;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.persistence.PublicholidayDAO;
import de.hbt.salat.dailyreport.persistence.PublicholidayRepository;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayRepository;
import de.hbt.salat.employee.auth.EmployeeAuthorization;
import de.hbt.salat.employee.auth.EmployeecontractAuthorization;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.Overtime;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.employee.persistence.OvertimeRepository;
import de.hbt.salat.employee.preferences.EmployeePreferenceService;
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
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SpecialOrders;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.testutils.EmployeeTestUtils;

/**
 * Die Differenz der Übersicht vor der Abnahme ist genau das, um das die Abnahme das
 * festgeschriebene Überstundenkonto ändert (#1122): neues {@code overtimeStatic} minus altes. Die
 * Tests rechnen gegen die echte Datenbank und die echte Überstundenrechnung, denn genau deren
 * Übereinstimmung sagt die Seite zu.
 *
 * <p>Der Vertrag beginnt am 01.06.2026 mit acht Stunden am Tag, ist bis zum 30.06.2026 abgenommen
 * und bis zum 31.08.2026 freigegeben; der Zeitraum reicht über Juli und August 2026. Darin liegen
 * ein Feiertag, eine Abwesenheit, eine Bereitschaft und eine Überstundenanpassung, davor eine
 * abgenommene Buchung und eine zweite Anpassung — beide dürfen die Differenz nicht berühren. Juli
 * und August 2026 haben 44 Werktage, ohne den Feiertag 43, das Soll ist also 344 Stunden.
 */
@DataJpaTest
@FixedClock("2026-09-15T10:00:00")
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({AuthorizedUserAuditorAware.class, SalatProperties.class, AuthService.class,
    EmployeeService.class, EmployeeDAO.class, EmployeeAuthorization.class,
    EmployeecontractService.class, EmployeecontractDAO.class, EmployeecontractAuthorization.class,
    EmployeeorderService.class, EmployeeorderDAO.class, EmployeeorderAuthorization.class,
    SuborderService.class, SuborderDAO.class, CustomerorderService.class, CustomerorderDAO.class, SpecialOrders.class,
    CustomerDAO.class, CommandPublisher.class,
    TimereportService.class, TimereportDAO.class, TimereportAuthorization.class, PublicholidayDAO.class,
    WorkingdayDAO.class, OvertimeService.class, ReleaseService.class, ReleaseAuthorization.class})
class AcceptanceReviewBalanceTest {

  private static final Duration DAILY_WORKING_TIME = Duration.ofHours(8);
  private static final LocalDate CONTRACT_START = LocalDate.of(2026, 6, 1);
  private static final LocalDate ACCEPTED_UNTIL = LocalDate.of(2026, 6, 30);
  private static final LocalDate RELEASED_UNTIL = LocalDate.of(2026, 8, 31);
  private static final LocalDate BEGIN = LocalDate.of(2026, 7, 1);
  private static final LocalDate END = RELEASED_UNTIL;
  private static final LocalDate HOLIDAY = LocalDate.of(2026, 7, 15);

  private static final Duration TARGET = DAILY_WORKING_TIME.multipliedBy(43);
  private static final Duration WORKING_TIME = Duration.ofHours(8 + 8 + 6);
  private static final Duration ADJUSTMENT = Duration.ofHours(2);

  @Autowired
  private EmployeeService employeeService;
  @Autowired
  private EmployeecontractService employeecontractService;
  @Autowired
  private TimereportService timereportService;
  @Autowired
  private TimereportDAO timereportDAO;
  @Autowired
  private OvertimeService overtimeService;
  @Autowired
  private ReleaseService releaseService;
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
  @MockitoBean
  private MailService mailService;
  @MockitoBean
  private EmployeePreferenceService employeePreferenceService;

  private final Set<LocalDate> workingdays = new HashSet<>();
  private Employee supervisor;
  private Customerorder customerorder;
  private long contract;

  @BeforeEach
  void setUp() {
    // an admin who is not the booked person: may accept, and may write bookings of every status -
    // accepted ones only an admin writes (#1164)
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.isAdmin()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn(EmployeeTestUtils.BOSS_SIGN);
    when(authorizedUser.getEffectiveLoginSign()).thenReturn(EmployeeTestUtils.BOSS_SIGN);

    var employee = EmployeeTestUtils.createEmployee(EmployeeTestUtils.TESTY_SIGN);
    employeeService.createOrUpdate(employee);
    supervisor = EmployeeTestUtils.createEmployee(EmployeeTestUtils.BOSS_SIGN);
    supervisor.getSalatUser().setStatus(GlobalConstants.EMPLOYEE_STATUS_PV);
    employeeService.createOrUpdate(supervisor);
    customerorder = customerorder();
    publicholidayRepository.save(new Publicholiday(HOLIDAY, "Testfeiertag"));

    // released and accepted before anything is booked: each booking gets the status of its day
    contract = employeecontractService.createEmployeecontract(employee.getId(), CONTRACT_START, null,
        List.of(supervisor.getId()), "task", false, false, DAILY_WORKING_TIME, 30, Duration.ZERO, false).getId();
    employeecontractService.updateReportReleaseData(contract, RELEASED_UNTIL, ACCEPTED_UNTIL);
    var project = employeeorder(suborder("01", null));
    var standby = employeeorder(suborder("02", OrderType.BEREITSCHAFT));
    var absence = employeeorder(suborder("03", OrderType.KRANK_URLAUB_ABWESEND));

    // before the period: accepted already, and part of the fixed account
    book(project, LocalDate.of(2026, 6, 15), 8);
    adjustment(CONTRACT_START, Duration.ofHours(-3));

    // the period
    book(project, LocalDate.of(2026, 7, 1), 8);
    book(absence, LocalDate.of(2026, 7, 2), 8);
    book(standby, LocalDate.of(2026, 7, 2), 4);
    book(project, LocalDate.of(2026, 8, 3), 6);
    adjustment(LocalDate.of(2026, 8, 10), ADJUSTMENT);

    // the fixed account as the acceptance of June left it; the listeners keep it current in operation
    overtimeService.updateOvertimeStatic(contract);
  }

  @Test
  void the_difference_is_the_change_of_the_fixed_overtime_account() {
    var fixedBefore = overtimeStatic();
    var review = releaseService.reviewAcceptance(contract, END);

    releaseService.acceptTimereports(contract, review.period().begin(), review.period().end());

    assertThat(review.period()).isEqualTo(new ReviewPeriod(BEGIN, END));
    assertThat(review.balance().diff()).isEqualTo(overtimeStatic().minus(fixedBefore));
  }

  /** Die Anpassung im Zeitraum gehört zur Differenz — ohne sie ginge die Rechnung der Seite nicht auf. */
  @Test
  void the_difference_includes_the_adjustment_of_the_period() {
    var balance = releaseService.reviewAcceptance(contract, END).balance();

    assertThat(balance.target()).as("Soll ohne den Feiertag").isEqualTo(TARGET);
    assertThat(balance.workingTime()).as("gebucht, Abwesenheit ja, Bereitschaft nein").isEqualTo(WORKING_TIME);
    assertThat(balance.adjustment()).as("nur die Anpassung im Zeitraum").isEqualTo(ADJUSTMENT);
    assertThat(balance.diff())
        .isEqualTo(WORKING_TIME.minus(TARGET).plus(ADJUSTMENT))
        .isNotEqualTo(balance.workingTime().minus(balance.target()));
  }

  /** Gelistet sind die freigegebenen Buchungen des Zeitraums, und sie ergeben, was die Bilanz gebucht nennt. */
  @Test
  void the_listed_bookings_add_up_to_the_booked_time() {
    var review = releaseService.reviewAcceptance(contract, END);

    var listed = review.byOrder().stream().flatMap(group -> group.timereports().stream()).toList();
    assertThat(listed).extracting(TimereportDTO::getStatus).containsOnly(TIMEREPORT_STATUS_COMMITTED);
    assertThat(TimereportReviewGrouping.sum(listed, TimereportDTO::getWorkingTime)).isEqualTo(review.balance().workingTime());
    assertThat(review.timereportCount()).isEqualTo(4);
    assertThat(review.beforePeriod()).isEmpty();
  }

  /** Abgenommen wird, was die Übersicht gezeigt hat — nicht mehr und nicht weniger. */
  @Test
  void exactly_the_listed_bookings_are_accepted() {
    var review = releaseService.reviewAcceptance(contract, END);
    var listedIds = review.byOrder().stream()
        .flatMap(group -> group.timereports().stream())
        .map(TimereportDTO::getId)
        .toList();

    releaseService.acceptTimereports(contract, review.period().begin(), review.period().end());

    var inThePeriod = timereportDAO.getTimereportsByDatesAndEmployeeContractId(contract, BEGIN, END);
    assertThat(inThePeriod).extracting(TimereportDTO::getId).containsExactlyInAnyOrderElementsOf(listedIds);
    assertThat(inThePeriod).extracting(TimereportDTO::getStatus).containsOnly(TIMEREPORT_STATUS_CLOSED);
    assertThat(employeecontractService.getEmployeecontractById(contract).getReportAcceptanceDate()).isEqualTo(END);
  }

  /** The value of the fixed overtime account as the contract stores it. */
  private Duration overtimeStatic() {
    return employeecontractService.getEmployeecontractById(contract).getOvertimeStatic();
  }

  private void book(long employeeorder, LocalDate day, int hours) {
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

  private void adjustment(LocalDate effective, Duration time) {
    var overtime = new Overtime();
    overtime.setEmployeecontract(employeecontractService.getEmployeecontractById(contract));
    overtime.setComment("Anpassung");
    overtime.setEffective(effective);
    overtime.setTime(time);
    overtimeRepository.save(overtime);
  }

  private long employeeorder(Suborder suborder) {
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
    customer.setShortname(uniqueShortname("cust"));
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
