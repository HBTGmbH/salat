package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;
import static org.tb.common.exception.ErrorCode.WD_READ_REQ_EMPLOYEE_OR_MANAGER;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.scheduling.SchedulerRequestAttributes;
import org.tb.customer.domain.Customer;
import org.tb.customer.persistence.CustomerRepository;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayRepository;
import org.tb.dailyreport.persistence.ReferencedayRepository;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.persistence.WorkingdayRepository;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractRepository;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.CustomerorderRepository;
import org.tb.order.persistence.EmployeeorderRepository;
import org.tb.order.persistence.SuborderRepository;

/**
 * „Rest nicht gearbeitet" ({@link MatrixService#fillNotWorked}) markiert jeden Arbeitstag eines
 * Monats, an dem nichts gebucht ist, als nicht gearbeitet — und nur diese Tage.
 *
 * <p>Geprüft wird am Ergebnis in der Datenbank, nicht an den Aufrufen dazwischen: welche Tage danach
 * als nicht gearbeitet gespeichert sind, welche unberührt bleiben, und wer die Aktion auslösen darf.
 * So hält der Test das Verhalten fest, gleich auf welchem Weg der Service die Buchungen, Arbeitstage
 * und Feiertage des Monats liest (#1124).
 *
 * <p>Eine echte Datenbank statt Mocks, und {@code @SpringBootTest} statt {@code @DataJpaTest}: einen
 * neuen Arbeitstag legt {@link WorkingdayService#upsertWorkingday} in einer eigenen Transaktion an
 * (#1111). In der Transaktion eines {@code @DataJpaTest} sähe diese den noch nicht
 * festgeschriebenen Vertrag nicht.
 *
 * <p>Eigene H2-Datenbank: die übrigen {@code @SpringBootTest}-Klassen teilen sich eine, und ein
 * zweiter Anwendungskontext mit {@code ddl-auto: create} legte sie beim Hochfahren neu an. Jeder
 * Test legt seine eigene Person mit eigenem Vertrag an; Feiertage gelten für alle und werden vor
 * jedem Test entfernt.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1124;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR"
})
@DisplayNameGeneration(ReplaceUnderscores.class)
class MatrixServiceFillNotWorkedTest {

  /** März 2026: der 1. ist ein Sonntag, der 31. ein Dienstag — 22 Wochentage. */
  private static final YearMonth MONTH = YearMonth.of(2026, 3);
  private static final int WEEKDAYS_OF_MONTH = 22;

  private static final AtomicInteger PERSONS = new AtomicInteger();

  @Autowired
  private MatrixService matrixService;
  @Autowired
  private WorkingdayRepository workingdayRepository;
  @Autowired
  private TimereportRepository timereportRepository;
  @Autowired
  private ReferencedayRepository referencedayRepository;
  @Autowired
  private PublicholidayRepository publicholidayRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private EmployeecontractRepository employeecontractRepository;
  @Autowired
  private CustomerRepository customerRepository;
  @Autowired
  private CustomerorderRepository customerorderRepository;
  @Autowired
  private SuborderRepository suborderRepository;
  @Autowired
  private EmployeeorderRepository employeeorderRepository;

  private Employeecontract contract;

  @BeforeEach
  void seedOwnContractAndLogInAsItsEmployee() {
    // ADR-0006: ohne laufende Anfrage gibt es keine request-scoped Bohne; der Stellvertreter für
    // Jobs stellt AuthorizedUser bereit, angemeldet wird wie im Betrieb über den SecurityContext
    setRequestAttributes(new SchedulerRequestAttributes());
    publicholidayRepository.deleteAll();

    contract = contract(LocalDate.of(2000, 1, 1), null);
    logInAs(signOf(contract));
  }

  @AfterEach
  void logOutAndUnbind() {
    SecurityContextHolder.clearContext();
    resetRequestAttributes();
  }

  @Test
  void marks_every_weekday_of_a_month_without_bookings_and_leaves_the_weekends_alone() {
    fillNotWorked();

    assertThat(notWorkedDays())
        .hasSize(WEEKDAYS_OF_MONTH)
        .noneMatch(day -> day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY)
        .startsWith(day(2))
        .endsWith(day(31));
    assertThat(storedDays()).hasSize(WEEKDAYS_OF_MONTH);
  }

  @Test
  void leaves_a_booked_day_alone() {
    book(day(3), GlobalConstants.TIMEREPORT_STATUS_OPEN);

    fillNotWorked();

    assertThat(storedDays()).doesNotContain(day(3)).contains(day(2), day(4));
  }

  /** Freigegeben oder abgenommen ist ein Tag erst recht gebucht. */
  @Test
  void a_released_or_accepted_booking_counts_as_well() {
    book(day(4), GlobalConstants.TIMEREPORT_STATUS_COMMITED);
    book(day(5), GlobalConstants.TIMEREPORT_STATUS_CLOSED);

    fillNotWorked();

    assertThat(storedDays()).doesNotContain(day(4), day(5)).contains(day(3), day(6));
  }

  @Test
  void leaves_a_public_holiday_on_a_weekday_alone() {
    publicholidayRepository.save(new Publicholiday(day(6), "Testfeiertag"));

    fillNotWorked();

    assertThat(storedDays()).doesNotContain(day(6)).contains(day(5), day(9));
    assertThat(notWorkedDays()).hasSize(WEEKDAYS_OF_MONTH - 1);
  }

  /** Mittwoch, der 11., bis Dienstag, der 17.: das Wochenende dazwischen bleibt ohnehin frei. */
  @Test
  void stays_within_the_validity_of_the_contract() {
    contract = contract(day(11), day(17));
    logInAs(signOf(contract));

    fillNotWorked();

    assertThat(notWorkedDays()).containsExactly(day(11), day(12), day(13), day(16), day(17));
  }

  @Test
  void does_nothing_in_a_month_the_contract_does_not_reach() {
    contract = contract(MONTH.plusMonths(1).atDay(1), null);
    logInAs(signOf(contract));

    fillNotWorked();

    assertThat(storedDays()).isEmpty();
  }

  /** Der vorhandene Satz wird umgeschrieben, kein zweiter angelegt. */
  @Test
  void turns_a_worked_day_without_booking_into_a_not_worked_one() {
    var worked = workingday(day(9), WORKED, 8, 30, 0, 45);

    fillNotWorked();

    var stored = storedWorkingday(day(9));
    assertThat(stored.getId()).isEqualTo(worked.getId());
    assertThat(stored.getType()).isEqualTo(NOT_WORKED);
    assertThat(List.of(stored.getStarttimehour(), stored.getStarttimeminute(), stored.getBreakhours(),
        stored.getBreakminutes())).containsOnly(0);
  }

  @Test
  void keeps_a_day_already_marked_as_not_worked_marked() {
    var notWorked = workingday(day(10), NOT_WORKED, 0, 0, 0, 0);

    fillNotWorked();

    var stored = storedWorkingday(day(10));
    assertThat(stored.getId()).isEqualTo(notWorked.getId());
    assertThat(stored.getType()).isEqualTo(NOT_WORKED);
  }

  @Test
  void the_management_may_fill_the_month_of_someone_else() {
    logInAs("gf" + PERSONS.incrementAndGet(), "ROLE_MANAGER");

    fillNotWorked();

    assertThat(notWorkedDays()).hasSize(WEEKDAYS_OF_MONTH);
  }

  @Test
  void another_employee_may_not_and_nothing_is_written() {
    logInAs(signOf(contract(LocalDate.of(2000, 1, 1), null)));

    assertThatThrownBy(this::fillNotWorked)
        .isInstanceOf(AuthorizationException.class)
        .extracting(e -> ((ErrorCodeException) e).getMessages().getFirst().getErrorCode())
        .isEqualTo(WD_READ_REQ_EMPLOYEE_OR_MANAGER);
    assertThat(storedDays()).isEmpty();
  }

  private void fillNotWorked() {
    matrixService.fillNotWorked(MONTH, contract.getId());
  }

  private static LocalDate day(int dayOfMonth) {
    return MONTH.atDay(dayOfMonth);
  }

  private List<LocalDate> storedDays() {
    return workingdayRepository.findAllByEmployeecontractId(contract.getId()).stream()
        .map(Workingday::getRefday)
        .sorted()
        .toList();
  }

  private List<LocalDate> notWorkedDays() {
    return workingdayRepository.findAllByEmployeecontractId(contract.getId()).stream()
        .filter(workingday -> workingday.getType() == NOT_WORKED)
        .map(Workingday::getRefday)
        .sorted()
        .toList();
  }

  private Workingday storedWorkingday(LocalDate date) {
    var stored = workingdayRepository.findAllByEmployeecontractId(contract.getId()).stream()
        .filter(workingday -> workingday.getRefday().equals(date))
        .toList();
    assertThat(stored).as("Arbeitstage am %s", date).hasSize(1);
    return stored.getFirst();
  }

  private void logInAs(String sign, String... roles) {
    var authorities = Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(sign, "N/A", authorities));
  }

  private static String signOf(Employeecontract employeecontract) {
    return employeecontract.getEmployee().getSign();
  }

  private Workingday workingday(LocalDate date, Workingday.WorkingDayType type, int startHour, int startMinute,
      int breakHours, int breakMinutes) {
    var workingday = new Workingday();
    workingday.setEmployeecontract(contract);
    workingday.setRefday(date);
    workingday.setType(type);
    workingday.setStarttimehour(startHour);
    workingday.setStarttimeminute(startMinute);
    workingday.setBreakhours(breakHours);
    workingday.setBreakminutes(breakMinutes);
    return workingdayRepository.save(workingday);
  }

  private void book(LocalDate date, String status) {
    var referenceday = referencedayRepository.findByRefdate(date).orElseGet(() -> {
      var newReferenceday = new Referenceday();
      newReferenceday.setRefdate(date);
      return referencedayRepository.save(newReferenceday);
    });
    var employeeorder = employeeorder();

    var timereport = new Timereport();
    timereport.setEmployeecontract(contract);
    timereport.setEmployeeorder(employeeorder);
    timereport.setSuborder(employeeorder.getSuborder());
    timereport.setReferenceday(referenceday);
    timereport.setDurationhours(1);
    timereport.setDurationminutes(0);
    timereport.setStatus(status);
    timereport.setTaskdescription("");
    timereport.setTraining(false);
    timereportRepository.save(timereport);
  }

  private Employeeorder employeeorder() {
    var sign = "FNW" + PERSONS.incrementAndGet();
    var validFrom = MONTH.atDay(1).minusYears(1);

    var customer = new Customer();
    customer.setName("Testkunde");
    customer.setShortname("TK");
    customer.setAddress("Teststraße 1");
    customer = customerRepository.save(customer);

    var customerorder = new Customerorder();
    customerorder.setCustomer(customer);
    customerorder.setSign(sign);
    customerorder.setDescription(sign);
    customerorder.setFromDate(validFrom);
    customerorder.setDebithours(Duration.ZERO);
    customerorder.setOrderType(OrderType.STANDARD);
    customerorder.setHide(false);
    customerorder = customerorderRepository.save(customerorder);

    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setDescription(sign);
    suborder.setShortdescription(sign);
    suborder.setInvoice(GlobalConstants.INVOICE_YES);
    suborder.setFromDate(validFrom);
    suborder.setDebithours(Duration.ZERO);
    suborder.setHide(false);
    suborder = suborderRepository.save(suborder);

    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSign(sign);
    employeeorder.setFromDate(validFrom);
    return employeeorderRepository.save(employeeorder);
  }

  /** Eine neue Person mit Status MA und diesem einen Vertrag, acht Stunden am Tag. */
  private Employeecontract contract(LocalDate validFrom, LocalDate validUntil) {
    var sign = "fn" + PERSONS.incrementAndGet();

    var salatUser = new SalatUser();
    salatUser.setLoginname(sign);
    salatUser.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
    salatUser = salatUserRepository.save(salatUser);

    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname(sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employee = employeeRepository.save(employee);

    var employeecontract = new Employeecontract();
    employeecontract.setEmployee(employee);
    employeecontract.setValidFrom(validFrom);
    employeecontract.setValidUntil(validUntil);
    employeecontract.setDailyWorkingTime(Duration.ofHours(8));
    return employeecontractRepository.save(employeecontract);
  }
}
