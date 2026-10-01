package de.hbt.salat.dailyreport.service;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static de.hbt.salat.auth.domain.AccessLevel.WRITE;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static de.hbt.salat.common.exception.ErrorCode.WD_CLOSED_REQ_ADMIN;
import static de.hbt.salat.common.exception.ErrorCode.WD_COMMITTED_NOT_SELF;
import static de.hbt.salat.common.exception.ErrorCode.WD_COMMITTED_REQ_PEOPLE_LEAD_OR_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.WD_DELETE_REQ_EMPLOYEE_OR_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.WD_NOT_WORKED_TIMEREPORTS_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.WD_OUTSIDE_CONTRACT;
import static de.hbt.salat.common.exception.ErrorCode.TR_SUCCEEDED_CONTRACT_NOT_SELF;
import static de.hbt.salat.common.exception.ErrorCode.WD_READ_REQ_EMPLOYEE_OR_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.WD_SUCCEEDED_CONTRACT_NOT_SELF;
import static de.hbt.salat.common.exception.ErrorCode.WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER;
import static de.hbt.salat.common.GlobalConstants.MAX_HOURS_PER_DAY;
import static de.hbt.salat.common.util.DateUtils.today;
import static de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;

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
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.util.BusinessRuleCheckUtils;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.Publicholiday;
import de.hbt.salat.dailyreport.domain.ReportPeriod;
import de.hbt.salat.dailyreport.domain.TargetEnd;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.persistence.PublicholidayRepository;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayRepository;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.event.EmployeecontractConflictResolutionEvent;
import de.hbt.salat.employee.event.EmployeecontractDeleteEvent;
import de.hbt.salat.employee.service.EmployeecontractService;

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
  private final TimereportAuthorization timereportAuthorization;

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
   * Markiert einen Tag als „nicht gearbeitet", wie der Schalter in der Tagesansicht: Beginn und Pause
   * werden auf null gesetzt, ein fehlender Arbeitstag wird angelegt. Die Übersicht vor der Freigabe
   * bietet das für jeden Arbeitstag ohne Buchung an (#760).
   *
   * <p>Die Prüfungen sind die jedes Schreibens ({@link #upsertWorkingday}): Berechtigung am
   * Arbeitstag, Tag innerhalb des Vertrags und keine Buchung an diesem Tag.
   */
  public void markNotWorked(Employeecontract employeecontract, LocalDate date) {
    var workingday = workingdayRepository.findByRefdayAndEmployeecontractId(date, employeecontract.getId())
        .orElseGet(() -> {
          var created = new Workingday();
          created.setEmployeecontract(employeecontract);
          created.setRefday(date);
          return created;
        });
    workingday.setType(NOT_WORKED);
    workingday.setStarttimehour(0);
    workingday.setStarttimeminute(0);
    workingday.setBreakhours(0);
    workingday.setBreakminutes(0);
    upsertWorkingday(workingday);
  }

  /**
   * Berechtigung, Gültigkeit des Vertrags am Stichtag und der Sonderfall „nicht gearbeitet mit
   * vorhandenen Buchungen". Die Prüfungen laufen vor jedem Schreibversuch — auch vor dem zweiten,
   * weil sich die Ausgangslage bis dahin geändert hat.
   */
  private void checkUpsertAllowed(Workingday workingday) {
    var employeecontract = workingday.getEmployeecontract();
    checkWriteAllowed(employeecontract, workingday.getRefday(), WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER);

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
    checkWriteAllowed(workingday.getEmployeecontract(), workingday.getRefday(), WD_DELETE_REQ_EMPLOYEE_OR_MANAGER);

    workingdayRepository.deleteById(workingDayId);
  }

  /**
   * Wer den Arbeitstag schreiben oder löschen darf. Im offenen Zeitraum die Person selbst, die
   * Geschäftsführung und wer es über eine Regel darf; im freigegebenen und im abgenommenen Zeitraum
   * gilt dieselbe Regel wie für die Buchungen des Tages (#1164): freigegeben die Geschäftsführung und
   * die zuständige People Lead, aber nie die Person selbst, abgenommen nur noch ein Admin. Beginn und
   * Pause gehören zu dem, was freigegeben und abgenommen wird — sie ändern sonst nachträglich, was die
   * Prüfung vor der Freigabe über den Tag gesagt hat. Wie bei den Buchungen schreibt die Person selbst im offenen
   * Zeitraum eines beendeten Vertrags nicht mehr, sobald sie auf einem Folgevertrag freigegeben hat (#1215).
   */
  private void checkWriteAllowed(Employeecontract employeecontract, LocalDate day, ErrorCode openPeriodDenial) {
    writeDenial(employeecontract, day, openPeriodDenial).ifPresent(denial -> {
      throw new AuthorizationException(denial);
    });
  }

  /**
   * Ob der Arbeitstag an diesem Tag geschrieben werden darf — dieselbe Antwort, die das Speichern
   * gibt. „Rest nicht gearbeitet" nimmt damit nur die Tage, die es schreiben darf (#1164).
   */
  @Transactional(readOnly = true)
  public boolean isWriteAllowed(Employeecontract employeecontract, LocalDate day) {
    return writeDenial(employeecontract, day, WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER).isEmpty();
  }

  private Optional<ErrorCode> writeDenial(Employeecontract employeecontract, LocalDate day, ErrorCode openPeriodDenial) {
    if (TIMEREPORT_STATUS_OPEN.equals(ReportPeriod.statusOn(employeecontract, day))) {
      String employeeSign = employeecontract.getEmployee().getSign();
      if(!authorizedUser.isManager() &&
         !employeecontract.getEmployee().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()) &&
         !authService.isAuthorized(AUTH_CATEGORY_WORKINGDAY, today(), WRITE, employeeSign)) {
        return Optional.of(openPeriodDenial);
      }
      // the one denial of the open period that applies to the working day as well (#1215)
      return timereportAuthorization.writeDenialOn(employeecontract, day)
          .filter(TR_SUCCEEDED_CONTRACT_NOT_SELF::equals)
          .map(denial -> WD_SUCCEEDED_CONTRACT_NOT_SELF);
    }
    return timereportAuthorization.writeDenialOn(employeecontract, day).map(denial -> switch (denial) {
      case TR_CLOSED_TIME_REPORT_REQ_ADMIN -> WD_CLOSED_REQ_ADMIN;
      case TR_COMMITTED_TIME_REPORT_NOT_SELF -> WD_COMMITTED_NOT_SELF;
      default -> WD_COMMITTED_REQ_PEOPLE_LEAD_OR_MANAGER;
    });
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

  /**
   * When the day would be done if its full target were booked, with the mandatory break as the
   * person whose day it is has set it (#1236). Takes the already loaded working day (may be
   * {@code null}), like {@link #getEffectiveStart}.
   */
  public TargetEnd calculateTargetEnd(Workingday workingday, long employeecontractId, Duration dayTarget) {
    var bookedBreak = workingday != null
        ? Duration.ofHours(workingday.getBreakhours()).plusMinutes(workingday.getBreakminutes())
        : Duration.ZERO;
    var considerMandatoryBreak = dailyPreferenceService.getForEmployeeContractId(employeecontractId)
        .considerMandatoryBreak();
    return TargetEnd.of(getEffectiveStart(workingday, employeecontractId), bookedBreak, dayTarget,
        considerMandatoryBreak);
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
