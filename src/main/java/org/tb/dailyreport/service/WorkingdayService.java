package org.tb.dailyreport.service;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static org.tb.auth.domain.AccessLevel.WRITE;
import static org.tb.common.exception.ErrorCode.WD_DELETE_REQ_EMPLOYEE_OR_MANAGER;
import static org.tb.common.exception.ErrorCode.WD_NOT_WORKED_TIMEREPORTS_FOUND;
import static org.tb.common.exception.ErrorCode.WD_OUTSIDE_CONTRACT;
import static org.tb.common.exception.ErrorCode.WD_READ_REQ_EMPLOYEE_OR_MANAGER;
import static org.tb.common.exception.ErrorCode.WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER;
import static org.tb.common.GlobalConstants.MAX_HOURS_PER_DAY;
import static org.tb.common.util.DateUtils.today;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.util.BusinessRuleCheckUtils;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayRepository;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.persistence.WorkingdayRepository;
import org.tb.dailyreport.preferences.DailyPreferenceService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.event.EmployeecontractConflictResolutionEvent;
import org.tb.employee.event.EmployeecontractDeleteEvent;
import org.tb.employee.service.EmployeecontractService;

@Slf4j
@Service
@Transactional
@AllArgsConstructor
@Authorized
public class WorkingdayService {

  private static final String AUTH_CATEGORY_WORKINGDAY = "WORKINGDAY";

  private final WorkingdayRepository workingdayRepository;
  private final PublicholidayRepository publicholidayRepository;
  private final TimereportDAO timereportDAO;
  private final AuthorizedUser authorizedUser;
  private final WorkingdayDAO workingdayDAO;
  private final AuthService authService;
  private final EmployeecontractService employeecontractService;
  private final DailyPreferenceService dailyPreferenceService;
  private final PlatformTransactionManager transactionManager;

  /**
   * The time the working day started: the stored value when there is one, otherwise the configured
   * work day start.
   *
   * <p>Callers must not derive this themselves. The display in the daily view and the prefill in the
   * booking form each had their own version, and only one of them knew about the fallback — so the
   * first booking of a day did not pick up the start time the same page was showing (#851).
   *
   * <p>Takes the already loaded working day (may be {@code null}) so that callers which have it do
   * not query twice. A working day marked as not worked carries {@code 00:00} here; whether that is
   * a sensible starting point is the caller's decision.
   */
  public LocalTime getEffectiveStart(Workingday workingday, long employeecontractId) {
    if (workingday != null) {
      return LocalTime.of(workingday.getStarttimehour(), workingday.getStarttimeminute());
    }
    return dailyPreferenceService.getForEmployeeContractId(employeecontractId).workDayStart();
  }

  private boolean isSupervisedByCurrentUser(Employeecontract ec) {
    return ec.getSupervisors().stream()
        .anyMatch(s -> s.getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()));
  }

  public Workingday getWorkingday(long employeecontractId, LocalDate date) {
    var employeecontract = employeecontractService.getEmployeecontractById(employeecontractId);
    String employeeSign = employeecontract.getEmployee().getSign();
    if(!authorizedUser.isManager() &&
       !(authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(employeecontract)) &&
       !employeecontract.getEmployee().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()) &&
       !authService.isAuthorized(AUTH_CATEGORY_WORKINGDAY, today(), WRITE, employeeSign)) {
      throw new AuthorizationException(WD_READ_REQ_EMPLOYEE_OR_MANAGER);
    }
    return workingdayRepository.findByRefdayAndEmployeecontractId(date, employeecontractId).orElse(null);
  }

  /**
   * Legt den Arbeitstag an oder schreibt den vorhandenen fort.
   *
   * <p>Denselben Arbeitstag zweimal anzulegen ist ein harmloser Sonderfall: ein doppelter Klick, ein
   * nach einem Abbruch erneut abgeschicktes Formular, zwei offene Registerkarten. Zwischen dem
   * Lesen und dem Schreiben steht nichts, was das abfängt — beide Vorgänge finden nichts und fügen
   * ein, der zweite verletzt den Unique Key auf Mitarbeitervertrag und Tag. Statt auf der
   * Fehlerseite zu enden, übernimmt der zweite Vorgang den inzwischen vorhandenen Arbeitstag und
   * wendet seine Änderung darauf an; der gewünschte Zustand ist zu diesem Zeitpunkt ohnehin
   * hergestellt (#1111). Eine reine Vorabprüfung („gibt es den schon?") verschiebt dieses Fenster
   * nur, statt es zu schließen.
   *
   * <p>Das Einfügen läuft dafür in einer eigenen Transaktion, und der zweite Versuch ebenso. Beides
   * ist nötig, nicht Geschmackssache:
   * <ul>
   *   <li>Nach der Verletzung ist der Persistenzkontext unbrauchbar und die Transaktion auf
   *       Rollback gestellt. Ohne eigenen Rahmen nähme der Konflikt die Transaktion des Aufrufers
   *       mit, und der Aufruf endete trotz Auflösung mit einem Fehler.</li>
   *   <li>Der zweite Versuch muss den Satz sehen, den der andere Vorgang gerade festgeschrieben
   *       hat. In der Transaktion, die vorher vergeblich gesucht hat, liegt unter MySQLs
   *       {@code REPEATABLE READ} derselbe Schnappschuss — sie fände ihn nicht.</li>
   * </ul>
   *
   * <p>Der Preis dafür: ein angelegter Arbeitstag ist festgeschrieben, auch wenn der Aufrufer
   * danach scheitert. Das ist genau der Satz, den der Aufrufer anlegen wollte, und das erneute
   * Anlegen trifft ab dann auf den vorhandenen. Für das Fortschreiben eines bereits gespeicherten
   * Arbeitstags bleibt es beim Rahmen des Aufrufers — dort gibt es keinen Konflikt aufzulösen, und
   * ein eigener Rahmen brächte nur den zweiten Schreibvorgang auf demselben Datensatz mit sich.
   */
  public void upsertWorkingday(Workingday workingday) {
    checkUpsertAllowed(workingday);

    if(!workingday.isNew()) {
      workingdayRepository.save(workingday);
      return;
    }

    try {
      inOwnTransaction(() -> workingdayRepository.save(workingday));
    } catch (DataIntegrityViolationException conflict) {
      inOwnTransaction(() -> takeOverConcurrentlyCreatedWorkingday(workingday, conflict));
    }
  }

  /**
   * Berechtigung, Gültigkeit des Vertrags am Stichtag und der Sonderfall „nicht gearbeitet mit
   * vorhandenen Buchungen". Die Prüfungen laufen vor jedem Schreibversuch — auch vor dem zweiten,
   * weil sich die Ausgangslage bis dahin geändert hat.
   */
  private void checkUpsertAllowed(Workingday workingday) {
    var employeecontract = workingday.getEmployeecontract();
    String employeeSign = employeecontract.getEmployee().getSign();
    if(!authorizedUser.isManager() &&
       !employeecontract.getEmployee().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()) &&
       !authService.isAuthorized(AUTH_CATEGORY_WORKINGDAY, today(), WRITE, employeeSign)) {
      throw new AuthorizationException(WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER);
    }

    BusinessRuleCheckUtils.isTrue(employeecontract.isValidAt(workingday.getRefday()), WD_OUTSIDE_CONTRACT);

    if(workingday.getType() == NOT_WORKED) {
      var timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(employeecontract.getId(), workingday.getRefday());
      BusinessRuleCheckUtils.empty(timereports, WD_NOT_WORKED_TIMEREPORTS_FOUND);
    }
  }

  /**
   * Überträgt die Angaben des nicht angelegten Arbeitstags auf den, der inzwischen da ist.
   *
   * <p>Findet sich keiner, war die Verletzung eine andere als die erwartete — dann bleibt es bei
   * dem ursprünglichen Fehler, statt ihn zu verschlucken.
   */
  private void takeOverConcurrentlyCreatedWorkingday(Workingday workingday, DataIntegrityViolationException conflict) {
    var existing = workingdayRepository
        .findByRefdayAndEmployeecontractId(workingday.getRefday(), workingday.getEmployeecontract().getId())
        .orElseThrow(() -> conflict);

    existing.setStarttimehour(workingday.getStarttimehour());
    existing.setStarttimeminute(workingday.getStarttimeminute());
    existing.setBreakhours(workingday.getBreakhours());
    existing.setBreakminutes(workingday.getBreakminutes());
    existing.setType(workingday.getType());

    checkUpsertAllowed(existing);
    workingdayRepository.save(existing);

    log.info("Der Arbeitstag am {} war bereits angelegt, die Änderung wurde auf den vorhandenen Satz angewendet.",
        workingday.getRefday());
  }

  /**
   * Ein eigener Transaktionsrahmen für einen Schritt, der die Transaktion des Aufrufers weder
   * mitreißen noch dessen Schnappschuss erben darf.
   *
   * <p>Programmatisch und nicht als {@code @Transactional(REQUIRES_NEW)}: die Methode dahinter
   * müsste öffentlich und über den Proxy aufgerufen werden, also entweder in einer zweiten Bohne
   * stehen — die dann als Einzige außerhalb eines {@code @Service} auf ein Repository zugriffe —
   * oder als Selbstverweis eingespritzt werden.
   */
  private void inOwnTransaction(Runnable action) {
    var ownTransaction = new TransactionTemplate(transactionManager);
    ownTransaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    ownTransaction.executeWithoutResult(status -> action.run());
  }

  public Workingday getNextRegularWorkingday(Workingday workingday) {
    Workingday nextWorkingDay = null;
    LocalDate day = workingday.getRefday();
    var employeecontractId = workingday.getEmployeecontract().getId();
    do {
      LocalDate nextDay = DateUtils.addDays(day, 1);
      if(isRegularWorkingday(nextDay)) {
        // we have found a weekday that is not a public holiday, hooray!
        var match = workingdayRepository.findByRefdayAndEmployeecontractId(nextDay, employeecontractId);
        if(match.isPresent()) {
          nextWorkingDay = match.get();
        } else {
          nextWorkingDay = new Workingday();
          nextWorkingDay.setRefday(nextDay);
          nextWorkingDay.setEmployeecontract(workingday.getEmployeecontract());
        }
      }
      day = nextDay; // prepare next iteration
    } while(nextWorkingDay == null);
    return nextWorkingDay;
  }

  public boolean isRegularWorkingday(LocalDate date) {
    if(DateUtils.isWeekday(date)) {
      Optional<Publicholiday> publicHoliday = publicholidayRepository.findByRefdate(date);
      if(publicHoliday.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  public void deleteWorkingdayById(long workingDayId) {
    var workingday = workingdayRepository.findById(workingDayId).orElseThrow();
    String employeeSign = workingday.getEmployeecontract().getEmployee().getSign();
    var employeecontract = workingday.getEmployeecontract();
    if(!authorizedUser.isManager() &&
       !employeecontract.getEmployee().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()) &&
       !authService.isAuthorized(AUTH_CATEGORY_WORKINGDAY, today(), WRITE, employeeSign)) {
      throw new AuthorizationException(WD_DELETE_REQ_EMPLOYEE_OR_MANAGER);
    }

    workingdayRepository.deleteById(workingDayId);
  }

  public List<Workingday> getWorkingdaysByEmployeeContractId(long employeeContractId, LocalDate dateFirst,
      LocalDate dateLast) {
    var employeecontract = employeecontractService.getEmployeecontractById(employeeContractId);
    String employeeSign = employeecontract.getEmployee().getSign();
    if(!authorizedUser.isManager() &&
       !(authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(employeecontract)) &&
       !employeecontract.getEmployee().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()) &&
       !authService.isAuthorized(AUTH_CATEGORY_WORKINGDAY, today(), WRITE, employeeSign)) {
      throw new AuthorizationException(WD_READ_REQ_EMPLOYEE_OR_MANAGER);
    }
    return workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, dateFirst, dateLast);
  }

  public LocalTime determineBeginTimeToDisplay(long ecId, LocalDate date, Workingday workingday) {
    Duration elapsed = timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, date)
        .stream()
        .map(TimereportDTO::getWorkingTime)
        .reduce(Duration.ZERO, Duration::plus);
    if (workingday != null) {
      elapsed = elapsed
          .plusHours(workingday.getStarttimehour())
          .plusMinutes(workingday.getStarttimeminute())
          .plusHours(workingday.getBreakhours())
          .plusMinutes(workingday.getBreakminutes());
    }
    return LocalTime.MIDNIGHT.plus(elapsed);
  }

  /**
   * When the day is done based on what has been booked so far.
   *
   * <p>Works without a stored working day: the start then comes from {@link #getEffectiveStart}, so
   * the daily view shows a quitting time from the first visit on instead of only after the first
   * booking (#831).
   */
  public String calculateQuittingTime(long employeecontractId, LocalDate date) {
    Duration laborTime = timereportDAO
        .getTimereportsByDateAndEmployeeContractId(employeecontractId, date)
        .stream().map(TimereportDTO::getWorkingTime).reduce(Duration.ZERO, Duration::plus);
    return endOfDay(getWorkingday(employeecontractId, date), employeecontractId, laborTime);
  }

  /** When the day would be done if the full daily working time were booked. */
  public String calculateWorkingDayEnds(long employeecontractId, LocalDate date) {
    var dailyWorkingTime = employeecontractService.getEmployeecontractById(employeecontractId)
        .getDailyWorkingTime();
    return endOfDay(getWorkingday(employeecontractId, date), employeecontractId, dailyWorkingTime);
  }

  private String endOfDay(Workingday workingday, long employeecontractId, Duration worked) {
    var start = getEffectiveStart(workingday, employeecontractId);
    LocalTime end = LocalTime.MIDNIGHT
        .plusHours(start.getHour())
        .plusMinutes(start.getMinute())
        .plusHours(workingday != null ? workingday.getBreakhours() : 0)
        .plusMinutes(workingday != null ? workingday.getBreakminutes() : 0)
        .plus(worked);
    return "%02d:%02d".formatted(end.getHour(), end.getMinute());
  }

  public boolean checkLaborTimeMaximum(List<TimereportDTO> timereports) {
    Duration actual = timereports.stream().map(TimereportDTO::getWorkingTime).reduce(Duration.ZERO, Duration::plus);
    return checkLaborTimeMaximum(actual);
  }

  public boolean checkLaborTimeMaximum(Duration workedTime) {
    return Duration.ofHours(MAX_HOURS_PER_DAY).minus(workedTime).isNegative();
  }

  public void seedWorkingday(long ecId, LocalDate date, int beginHour, int beginMinute) {
    var workingday = getWorkingday(ecId, date);
    if (workingday == null) {
      workingday = new Workingday();
      workingday.setEmployeecontract(employeecontractService.getEmployeecontractById(ecId));
      workingday.setRefday(date);
      workingday.setBreakhours(0);
      workingday.setBreakminutes(0);
      workingday.setStarttimehour(beginHour);
      workingday.setStarttimeminute(beginMinute);
    } else {
      if (workingday.getStarttimehour() == 0) workingday.setStarttimehour(beginHour);
      if (workingday.getStarttimeminute() == 0) workingday.setStarttimeminute(beginMinute);
    }
    workingday.setType(Workingday.WorkingDayType.WORKED);
    upsertWorkingday(workingday);
  }

  @EventListener
  void onEmployeecontractDelete(EmployeecontractDeleteEvent event) {
    var workingdays = workingdayRepository.findAllByEmployeecontractId(event.getId());
    workingdayRepository.deleteAll(workingdays);
  }

  @EventListener
  void onEmployeecontractConflictResolution(EmployeecontractConflictResolutionEvent event) {
    var updatingEmployeecontract = event.getUpdatingEmployeecontract();
    var conflictingEmployeecontract = event.getConflictingEmployeecontract();

    var workingdays = workingdayRepository.findAllByEmployeecontractIdAndReferencedayBetween(
        conflictingEmployeecontract.getId(),
        updatingEmployeecontract.getValidFrom(),
        updatingEmployeecontract.getValidUntil()
    );

    workingdays.forEach(wd -> {
      wd.setEmployeecontract(updatingEmployeecontract);
      workingdayRepository.save(wd);
      event.addLog("Informationen zum Arbeitstag am %s nach Vertrag (%s) verschoben".formatted(
          DateUtils.format(wd.getRefday()),
          updatingEmployeecontract.getValidity()
      ));
    });
  }

}
