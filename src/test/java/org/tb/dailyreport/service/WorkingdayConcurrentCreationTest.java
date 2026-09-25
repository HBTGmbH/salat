package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.GlobalConstants;
import org.tb.common.scheduling.SchedulerRequestAttributes;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.WorkingdayRepository;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractRepository;

/**
 * Zwei Anfragen legen denselben Arbeitstag an (#1111).
 *
 * <p>Der Fall entsteht durch einen doppelten Klick, ein nach einem Abbruch erneut abgeschicktes
 * Formular oder zwei offene Registerkarten: beide Anfragen lesen, finden nichts, und beide fügen
 * ein. Die zweite verletzte den Unique Key auf Mitarbeitervertrag und Tag und endete auf der
 * Fehlerseite, obwohl der Arbeitstag zu diesem Zeitpunkt längst existiert.
 *
 * <p>Nachgestellt wird die Ausgangslage der zweiten Anfrage, und die ist genau dies: sie hält einen
 * noch nicht gespeicherten Arbeitstag für eine Kombination, die es inzwischen gibt. Zwei Threads
 * bräuchte es dafür nicht, und sie würden hier etwas anderes prüfen als im Betrieb — H2 beantwortet
 * den Zusammenstoß zweier <em>offener</em> Transaktionen mit einem Nebenläufigkeitsfehler, während
 * die Meldung aus dem Betrieb ein {@code Duplicate entry} gegen einen bereits festgeschriebenen
 * Satz ist.
 *
 * <p>Eigene H2-Datenbank: die übrigen {@code @SpringBootTest}-Klassen teilen sich eine, und ein
 * zweiter Anwendungskontext mit {@code ddl-auto: create} legte sie beim Hochfahren neu an.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1111;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR"
})
@DisplayNameGeneration(ReplaceUnderscores.class)
class WorkingdayConcurrentCreationTest {

  private static final String SIGN = "w11";
  private static final LocalDate DAY = LocalDate.of(2026, 3, 17);

  @Autowired
  private WorkingdayService workingdayService;
  @Autowired
  private WorkingdayRepository workingdayRepository;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private EmployeecontractRepository employeecontractRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;
  @Autowired
  private AuthorizedUser authorizedUser;

  private Employeecontract contract;

  @BeforeEach
  void seedContractAndAuthorizeAsJob() {
    // ADR-0006: ohne laufende Anfrage gibt es keine request-scoped Bohne - der Job-Modus ist der
    // dafür vorgesehene Weg und macht den Aufrufer zugleich berechtigt
    setRequestAttributes(new SchedulerRequestAttributes());
    authorizedUser.initForJob();

    contract = employeeRepository.findBySign(SIGN)
        .map(employee -> employeecontractRepository.findAllByEmployeeId(employee.getId()).getFirst())
        .orElseGet(this::createContract);
    workingdayRepository.deleteAll(workingdayRepository.findAllByEmployeecontractId(contract.getId()));
  }

  @AfterEach
  void unbind() {
    resetRequestAttributes();
  }

  @Test
  void the_second_creation_takes_over_the_working_day_created_meanwhile() {
    var firstRequest = newWorkingday(8, 0);
    var secondRequest = newWorkingday(9, 30);

    workingdayService.upsertWorkingday(firstRequest);

    assertThatNoException().isThrownBy(() -> workingdayService.upsertWorkingday(secondRequest));

    assertThat(storedWorkingdays()).hasSize(1);
    var stored = storedWorkingdays().getFirst();
    assertThat(stored.getStarttimehour()).isEqualTo(9);
    assertThat(stored.getStarttimeminute()).isEqualTo(30);
  }

  /**
   * Der aufgelöste Zusammenstoß ist kein Fehler und wird auch nicht als einer gemeldet: im Log
   * bleibt es bei der Zeile, die sagt, was geschehen ist.
   */
  @Test
  void the_resolved_collision_is_not_reported_as_an_error() {
    workingdayService.upsertWorkingday(newWorkingday(8, 0));

    var logged = recordLog();
    workingdayService.upsertWorkingday(newWorkingday(9, 30));
    stopRecording(logged);

    assertThat(logged.list)
        .as("aufgeloest, also kein Fehler")
        .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.ERROR));
  }

  /**
   * Der Arbeitstag, der schon da ist, wird fortgeschrieben und nicht ein zweites Mal angelegt — auch
   * dann nicht, wenn die dritte Anfrage wieder mit einem neuen Objekt kommt.
   */
  @Test
  void a_third_creation_finds_the_same_single_working_day() {
    workingdayService.upsertWorkingday(newWorkingday(8, 0));
    workingdayService.upsertWorkingday(newWorkingday(9, 30));

    assertThatNoException().isThrownBy(() -> workingdayService.upsertWorkingday(newWorkingday(10, 15)));

    assertThat(storedWorkingdays()).hasSize(1);
    assertThat(storedWorkingdays().getFirst().getStarttimehour()).isEqualTo(10);
  }

  private ListAppender<ILoggingEvent> recordLog() {
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(appender);
    return appender;
  }

  private void stopRecording(ListAppender<ILoggingEvent> appender) {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(appender);
    appender.stop();
  }

  private List<Workingday> storedWorkingdays() {
    return workingdayRepository.findAllByEmployeecontractId(contract.getId());
  }

  private Workingday newWorkingday(int startHour, int startMinute) {
    var workingday = new Workingday();
    workingday.setEmployeecontract(contract);
    workingday.setRefday(DAY);
    workingday.setStarttimehour(startHour);
    workingday.setStarttimeminute(startMinute);
    workingday.setBreakhours(0);
    workingday.setBreakminutes(0);
    workingday.setType(WORKED);
    return workingday;
  }

  private Employeecontract createContract() {
    var salatUser = new SalatUser();
    salatUser.setLoginname(SIGN);
    salatUser.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
    salatUser = salatUserRepository.save(salatUser);

    var employee = new Employee();
    employee.setSign(SIGN);
    employee.setFirstname("Vorname");
    employee.setLastname(SIGN);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employee = employeeRepository.save(employee);

    var employeecontract = new Employeecontract();
    employeecontract.setEmployee(employee);
    employeecontract.setValidFrom(LocalDate.of(2000, 1, 1));
    employeecontract.setDailyWorkingTime(Duration.ofHours(8));
    return employeecontractRepository.save(employeecontract);
  }

}
