package org.tb.dailyreport.service;

import static java.lang.Boolean.TRUE;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.tb.common.GlobalConstants.DAY_MAX_LENGTH_ALLOWED_IN_MINUTES;
import static org.tb.common.GlobalConstants.REST_PERIOD_IN_MINUTES;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.GlobalConstants.WORKDAY_MAX_LENGTH_ALLOWED_IN_MINUTES;
import static org.tb.common.exception.ErrorCode.RL_ACCEPT_NOT_ALLOWED;
import static org.tb.common.exception.ErrorCode.RL_ACCEPTANCE_DATE_AFTER_RELEASE;
import static org.tb.common.exception.ErrorCode.RL_ACCEPTANCE_DATE_INVALID;
import static org.tb.common.exception.ErrorCode.RL_ACCEPTANCE_DATE_MOVED_BACKWARDS;
import static org.tb.common.exception.ErrorCode.RL_ACCEPTANCE_WITHOUT_RELEASE;
import static org.tb.common.exception.ErrorCode.RL_NOTHING_TO_ACCEPT;
import static org.tb.common.exception.ErrorCode.RL_NOTHING_TO_RELEASE;
import static org.tb.common.exception.ErrorCode.RL_RELEASE_DATE_BEFORE_ACCEPTANCE;
import static org.tb.common.exception.ErrorCode.RL_RELEASE_DATE_INVALID;
import static org.tb.common.exception.ErrorCode.RL_RELEASE_NOT_ALLOWED;
import static org.tb.common.exception.ErrorCode.RL_REVIEWED_PERIOD_CHANGED;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.WD_DAY_LENGTH_TOO_LONG;
import static org.tb.common.exception.ErrorCode.WD_LENGTH_TOO_LONG;
import static org.tb.common.exception.ErrorCode.WD_NO_TIMEREPORT;
import static org.tb.common.util.DateTimeUtils.now;
import static org.tb.common.util.DateUtils.max;
import static org.tb.common.util.DateUtils.min;
import static org.tb.common.util.DateUtils.today;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.service.TimereportService.isRelevantForWorkingTimeValidation;
import static org.tb.dailyreport.service.TimereportService.noTimeReportsFound;
import static org.tb.dailyreport.service.TimereportService.validateBeginOfWorkingDay;
import static org.tb.dailyreport.service.TimereportService.validateBreakTime;

import com.google.common.annotations.VisibleForTesting;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.service.MailService;
import org.tb.common.service.MailService.MailContact;
import org.tb.common.util.DataValidationUtils;
import org.tb.dailyreport.auth.ReleaseAuthorization;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.ReviewPeriod;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.TimereportReview;
import org.tb.dailyreport.domain.TimereportReview.DayFinding;
import org.tb.dailyreport.domain.UnbookedWorkingDays;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.preferences.EmployeePreferenceService;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class ReleaseService {

  private final EmployeecontractDAO employeecontractDAO;
  private final TimereportDAO timereportDAO;
  private final TimereportRepository timereportRepository;
  private final PublicholidayDAO publicholidayDAO;
  private final OvertimeService overtimeService;
  private final AuthorizedUser authorizedUser;
  private final WorkingdayDAO workingdayDAO;
  private final MailService mailService;
  private final EmployeeService employeeService;
  private final EmployeecontractService employeecontractService;
  private final TimereportService timereportService;
  private final ReleaseAuthorization releaseAuthorization;
  private final EmployeePreferenceService employeePreferenceService;
  private final TimereportAuthorization timereportAuthorization;

  /**
   * Gibt genau den Zeitraum frei, den die Übersicht gezeigt hat ({@link #reviewRelease}, #760):
   * alle offenen Buchungen bis {@code reviewedEnd}, speichert das Ende als Freigabedatum und
   * benachrichtigt die People Leads.
   *
   * <p>Hat sich der Zeitraum seit dem Anzeigen geändert, gibt die Freigabe nichts frei und meldet
   * {@code RL-0008}, statt stillschweigend einen anderen Zeitraum freizugeben. Das Ende muss ein
   * Ende sein, das die Übersicht zeigen kann — ein Monatsletzter oder das Vertragsende in diesem
   * Monat, nie ein Tag mitten im Monat. Anfang und Ende werden neu bestimmt und müssen dem
   * gezeigten Zeitraum gleichen. Damit fällt auf, wenn inzwischen freigegeben wurde, etwa in einem
   * zweiten Fenster, wenn sich der Vertragsbeginn verschoben hat oder das Vertragsende so, dass der
   * gewählte Monat jetzt an einem anderen Tag endet. Den Inhalt der Buchungen vergleicht die
   * Freigabe nicht: die Anforderung spricht vom Zeitraum, und die Prüfung läuft unmittelbar vor dem
   * Freigeben ohnehin noch einmal ({@link #validateForRelease}) — ihre Befunde wirft sie, die über
   * den Zeitraum zuerst, dann die der Tage nach Datum.
   *
   * <p>Zweimal hintereinander abgeschickt, trifft die zweite Freigabe auf den schon freigegebenen
   * Zeitraum und meldet {@code RL-0008}. Laufen zwei Freigaben wirklich gleichzeitig durch den
   * Vergleich, scheitert die zweite beim Schreiben an der Versionsnummer von Vertrag und Buchungen
   * ({@code @Version} in {@link org.tb.common.domain.AuditedEntity}) und wird zurückgerollt; die
   * Mail verschickt diese Methode aber innerhalb der Transaktion, sie kann dann doppelt ankommen.
   * Eine eigene Sperre gibt es dafür bewusst nicht.
   *
   * @throws AuthorizationException ohne Freigabeberechtigung für den Vertrag
   * @throws BusinessRuleException  {@code RL-0008}, wenn sich der Zeitraum geändert hat, sonst die
   *                                Befunde der Prüfung
   */
  public void releaseTimereports(long employeecontractId, LocalDate reviewedBegin, LocalDate reviewedEnd) {
    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    DataValidationUtils.notNull(employeecontract, TR_EMPLOYEE_CONTRACT_NOT_FOUND);
    if(!releaseAuthorization.isReleaseAuthorized(employeecontract, AccessLevel.WRITE)) {
      throw new AuthorizationException(RL_RELEASE_NOT_ALLOWED);
    }
    DataValidationUtils.notNull(reviewedBegin, RL_RELEASE_DATE_INVALID);
    DataValidationUtils.notNull(reviewedEnd, RL_RELEASE_DATE_INVALID);

    // only an end the overview can show: the end of a month, or the contract end within it
    if(!reviewedEnd.equals(limitToContractEnd(employeecontract, YearMonth.from(reviewedEnd).atEndOfMonth()))) {
      throw new BusinessRuleException(RL_REVIEWED_PERIOD_CHANGED);
    }
    var period = releasePeriod(employeecontract, reviewedEnd);
    if(!period.begin().equals(reviewedBegin) || !period.end().equals(reviewedEnd)) {
      throw new BusinessRuleException(RL_REVIEWED_PERIOD_CHANGED);
    }

    validateForRelease(employeecontractId, reviewedEnd);

    // set status in timereports
    var timereports = timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(
        employeecontractId,
        reviewedEnd
    );
    for (var timereport : timereports) {
      releaseTimereport(timereport.getId(), authorizedUser.getLoginSign());
    }

    // store new release date in employee contract
    employeecontractService.updateReportReleaseData(employeecontractId, reviewedEnd, employeecontract.getReportAcceptanceDate());

    sendTimeReportsReleasedMail(employeecontract);
  }

  /**
   * Die Übersicht über den Zeitraum, den eine Freigabe bis {@code requestedEnd} erfasst (#760):
   * seine Buchungen, seine Bilanz und die Befunde der Prüfung, an ihrem Tag. Sehen darf sie, wer
   * freigeben darf.
   *
   * <p>Das Ende wird zuerst auf das Vertragsende beschnitten, erst dann wird geprüft — so bleibt
   * der letzte Monat eines Vertrags freigebbar (#324). Die Arbeitstage ohne Buchung sind die Tage
   * der Befunde {@code WD_NO_TIMEREPORT}, also die Regel {@link UnbookedWorkingDays} über die
   * offenen Buchungen. Anlegen darf an einem solchen Tag, wer eine offene Buchung schreiben darf.
   *
   * @throws AuthorizationException ohne Freigabeberechtigung für den Vertrag
   */
  @Transactional(readOnly = true)
  public TimereportReview reviewRelease(long employeecontractId, LocalDate requestedEnd) {
    var contract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    DataValidationUtils.notNull(contract, TR_EMPLOYEE_CONTRACT_NOT_FOUND);
    if(!releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.WRITE)) {
      throw new AuthorizationException(RL_RELEASE_NOT_ALLOWED);
    }
    DataValidationUtils.notNull(requestedEnd, RL_RELEASE_DATE_INVALID);

    var end = limitToContractEnd(contract, requestedEnd);
    var period = releasePeriod(contract, end);
    var findings = collectReleaseFindings(employeecontractId, contract, end);
    return buildReview(contract, period,
        findings.periodFindings(),
        findings.dayFindings(),
        findings.daysWithoutBooking(),
        findings.openBeforePeriod(),
        timereportAuthorization.isWriteAllowed(contract, TIMEREPORT_STATUS_OPEN),
        !period.isEmpty() && findings.isEmpty());
  }

  /**
   * Stellt die Übersicht über einen Zeitraum zusammen, dessen Grenzen und Befunde der Aufrufer
   * schon kennt — Freigabe (#760) und Abnahme (#1122) gleichermaßen. Den Zeitraum bestimmt und
   * prüft sie nicht selbst.
   *
   * <p>Ist der Zeitraum leer oder gibt es einen Befund über ihn, bleibt es bei den Befunden: keine
   * Bilanz, keine Buchungen, und weder die Regel noch die Gliederung sehen einen verkehrten
   * Zeitraum. Sonst stehen darin alle Buchungen des Zeitraums, gleich welchen Status — dieselben,
   * die die Bilanz summiert, so gehen Liste und Bilanz auf. Einen Befund zu einem Tag außerhalb
   * des Zeitraums führt sie bei den Befunden über den Zeitraum, damit er gezeigt wird und weiter
   * sperrt.
   *
   * <p>Die Buchungen kommen über die Leseprüfung von {@link TimereportDAO}, die Bilanz über eine
   * Summe ohne sie. Wer nur über eine Regel der Kategorie RELEASE_TIMEREPORTS freigibt und die
   * Buchungen nicht lesen darf, sieht deshalb eine richtige Bilanz zu einer unvollständigen Liste;
   * die Person selbst, die Geschäftsführung und die zuständige People Lead lesen alle.
   *
   * @param dayFindings        Befunde einzelner Tage, nach Datum sortiert
   * @param daysWithoutBooking die Arbeitstage ohne Buchung, wie {@link UnbookedWorkingDays} sie
   *                           für diese Aktion bestimmt hat
   * @param beforePeriod       Buchungen vor dem Zeitraum, die die Aktion ebenfalls erfasst
   * @param canCreate          an einem Tag ohne Buchung darf eine angelegt werden
   * @param actionAllowed      die Aktion ist möglich
   */
  private TimereportReview buildReview(Employeecontract contract, ReviewPeriod period,
      List<ServiceFeedbackMessage> periodFindings, List<DayFinding> dayFindings,
      List<LocalDate> daysWithoutBooking, List<TimereportDTO> beforePeriod,
      boolean canCreate, boolean actionAllowed) {
    var employee = contract.getEmployee();
    var ownContract = Objects.equals(authorizedUser.getEffectiveLoginSign(), employee.getLoginname());
    var overtimeAccount = !contract.getDailyWorkingTime().isZero();

    if(period.isEmpty() || !periodFindings.isEmpty()) {
      var findings = new ArrayList<>(periodFindings);
      dayFindings.forEach(finding -> findings.add(finding.message()));
      return new TimereportReview(contract.getId(), employee.getName(), employee.getSign(), ownContract,
          contract.getReportReleaseDate(), contract.getReportAcceptanceDate(), period,
          null, overtimeAccount, Duration.ZERO,
          List.copyOf(findings), List.of(), List.of(), List.of(), List.of(), Set.of(),
          canCreate, actionAllowed, 0);
    }

    long employeecontractId = contract.getId();
    var timereports = timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeecontractId, period.begin(), period.end());
    var notWorkedDays = workingdayDAO.getWorkingdaysByEmployeeContractId(employeecontractId, period.begin(), period.end())
        .stream()
        .filter(workingday -> workingday.getType() == NOT_WORKED)
        .map(Workingday::getRefday)
        .collect(Collectors.toSet());
    var publicHolidayNames = publicholidayDAO.getPublicHolidaysBetween(period.begin(), period.end())
        .stream()
        .collect(toMap(Publicholiday::getRefdate, Publicholiday::getName, (first, second) -> first));
    var balance = overtimeAccount
        ? overtimeService.calculateOvertimeBalance(employeecontractId, period.begin(), period.end()).orElse(null)
        : null;

    var findings = new ArrayList<>(periodFindings);
    dayFindings.stream()
        .filter(finding -> !period.contains(finding.date()))
        .forEach(finding -> findings.add(finding.message()));
    var dayFindingsInPeriod = dayFindings.stream()
        .filter(finding -> period.contains(finding.date()))
        .toList();
    var editableTimereportIds = Stream.concat(timereports.stream(), beforePeriod.stream())
        .filter(timereport -> timereportAuthorization.isWriteAllowed(contract, timereport.getStatus()))
        .map(TimereportDTO::getId)
        .collect(Collectors.toSet());

    return new TimereportReview(employeecontractId, employee.getName(), employee.getSign(), ownContract,
        contract.getReportReleaseDate(), contract.getReportAcceptanceDate(), period,
        balance, overtimeAccount,
        TimereportReviewGrouping.sum(timereports, TimereportReviewGrouping::standbyOf),
        List.copyOf(findings), dayFindingsInPeriod,
        TimereportReviewGrouping.byOrder(timereports),
        TimereportReviewGrouping.byMonth(period, timereports, notWorkedDays, publicHolidayNames,
            dayFindingsInPeriod, Set.copyOf(daysWithoutBooking)),
        List.copyOf(beforePeriod), Set.copyOf(editableTimereportIds),
        canCreate, actionAllowed, timereports.size());
  }

  /**
   * Die Übersicht über den Zeitraum, den eine Abnahme bis {@code requestedEnd} erfasst (#1122):
   * seine Buchungen, seine Bilanz und die Befunde über den Zeitraum. Sehen darf sie, wer abnehmen
   * darf — seine eigenen Buchungen nimmt niemand ab, auch die Geschäftsführung nicht.
   *
   * <p>Das Ende wird zuerst auf das Vertragsende beschnitten (#324). Gelistet sind wie bei der
   * Freigabe alle Buchungen des Zeitraums, gleich welchen Status; die Abnahme schließt davon die
   * freigegebenen ab. Freigegebene Buchungen vor dem Zeitraum erfasst sie ebenfalls, sie stehen
   * deshalb für sich.
   *
   * <p>Die Abnahme prüft keine Tage: Arbeitszeit, Pausen und Ruhezeit hat die Freigabe geprüft. Ein
   * Arbeitstag ohne Buchung ist deshalb kein Befund, sondern steht nur als solcher da — nach der
   * Regel {@link UnbookedWorkingDays} über die Buchungen jeden Status, denn an einem Tag mit einer
   * Buchung fehlt keine. Angelegt wird aus dieser Übersicht nichts: an Stelle der Person
   * nachzubuchen gehört nicht zur Abnahme.
   *
   * <p>Die Regel braucht die Buchungen, Arbeitstage und Feiertage des Zeitraums, die die Übersicht
   * danach noch einmal lädt — je Zeitraum ein fester Aufwand, keine Abfrage je Tag, auch wenn eine
   * erste Abnahme über Jahre reicht.
   *
   * @throws AuthorizationException ohne Abnahmeberechtigung für den Vertrag
   */
  @Transactional(readOnly = true)
  public TimereportReview reviewAcceptance(long employeecontractId, LocalDate requestedEnd) {
    var contract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    DataValidationUtils.notNull(contract, TR_EMPLOYEE_CONTRACT_NOT_FOUND);
    if(!releaseAuthorization.isAcceptAuthorized(contract, AccessLevel.WRITE)) {
      throw new AuthorizationException(RL_ACCEPT_NOT_ALLOWED);
    }
    DataValidationUtils.notNull(requestedEnd, RL_ACCEPTANCE_DATE_INVALID);

    var period = acceptancePeriod(contract, requestedEnd);
    var findings = acceptanceFindings(contract, period);
    if (!findings.isEmpty()) {
      return buildReview(contract, period, findings, List.of(), List.of(), List.of(), false, false);
    }
    var committedBeforePeriod = timereportDAO
        .getCommitedTimereportsByEmployeeContractIdBeforeDate(employeecontractId, period.end())
        .stream()
        .filter(timereport -> timereport.getReferenceday().isBefore(period.begin()))
        .toList();
    return buildReview(contract, period, List.of(), List.of(),
        workingDaysWithoutAnyBooking(contract, period), committedBeforePeriod, false, true);
  }

  /**
   * Ob der angemeldete Benutzer die Buchungen dieses Vertrags abnehmen darf (#1122). Die Seite der
   * Abnahme bietet die Übersicht nur dann an, statt in eine 403 zu führen — wählt jemand aus der
   * Geschäftsführung dort den eigenen Vertrag, sagt sie, dass er nicht abzunehmen ist.
   */
  @Transactional(readOnly = true)
  public boolean isAcceptAllowed(long employeecontractId) {
    var contract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    return contract != null && releaseAuthorization.isAcceptAuthorized(contract, AccessLevel.WRITE);
  }

  /** Die Arbeitstage des Zeitraums, an denen es keine Buchung gibt, gleich welchen Status (#1122). */
  private List<LocalDate> workingDaysWithoutAnyBooking(Employeecontract contract, ReviewPeriod period) {
    long employeecontractId = contract.getId();
    var bookedDays = timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeecontractId, period.begin(), period.end())
        .stream()
        .map(TimereportDTO::getReferenceday)
        .collect(Collectors.toSet());
    var workingDays = workingdayDAO.getWorkingdaysByEmployeeContractId(employeecontractId, period.begin(), period.end())
        .stream()
        .collect(toMap(Workingday::getRefday, identity(), (first, second) -> first));
    var publicHolidays = publicholidayDAO.getPublicHolidaysBetween(period.begin(), period.end())
        .stream()
        .map(Publicholiday::getRefdate)
        .collect(Collectors.toSet());
    return UnbookedWorkingDays.between(period.begin(), period.end(), contract, bookedDays, workingDays, publicHolidays);
  }

  /**
   * Nimmt genau den Zeitraum ab, den die Übersicht gezeigt hat ({@link #reviewAcceptance}, #1122):
   * alle freigegebenen Buchungen bis {@code reviewedEnd}, speichert das Ende als Abnahmedatum und
   * schreibt das Überstundenkonto bis dahin fest.
   *
   * <p>Hat sich der Zeitraum seit dem Anzeigen geändert, nimmt die Abnahme nichts ab und meldet
   * {@code RL-0008}, wie die Freigabe ({@link #releaseTimereports}): das Ende muss eines sein, das
   * die Übersicht zeigen kann — ein Monatsletzter oder das Vertragsende in diesem Monat —, und
   * Anfang und Ende werden neu bestimmt und müssen dem gezeigten Zeitraum gleichen. So fällt auf,
   * wenn inzwischen abgenommen wurde, etwa in einem zweiten Fenster, oder wenn sich Vertragsbeginn
   * oder Vertragsende verschoben haben. Eine inzwischen geöffnete Freigabe ändert den Zeitraum nicht,
   * die Befunde fallen aber anders aus ({@code RL-0006}); sie werden unmittelbar vor dem Abnehmen
   * erneut bestimmt und geworfen. Den Inhalt der Buchungen vergleicht die Abnahme nicht, die
   * Anforderung spricht vom Zeitraum.
   *
   * <p>Zweimal hintereinander abgeschickt, trifft die zweite Abnahme auf den schon abgenommenen
   * Zeitraum und meldet {@code RL-0008}. Laufen zwei Abnahmen wirklich gleichzeitig durch den
   * Vergleich, scheitert die zweite beim Schreiben an der Versionsnummer von Vertrag und Buchungen
   * ({@code @Version} in {@link org.tb.common.domain.AuditedEntity}) und wird zurückgerollt. Eine
   * eigene Sperre gibt es dafür bewusst nicht.
   *
   * @throws AuthorizationException ohne Abnahmeberechtigung für den Vertrag
   * @throws BusinessRuleException  {@code RL-0008}, wenn sich der Zeitraum geändert hat, sonst der
   *                                Befund über den Zeitraum
   */
  public void acceptTimereports(long employeecontractId, LocalDate reviewedBegin, LocalDate reviewedEnd) {
    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    DataValidationUtils.notNull(employeecontract, TR_EMPLOYEE_CONTRACT_NOT_FOUND);
    if(!releaseAuthorization.isAcceptAuthorized(employeecontract, AccessLevel.WRITE)) {
      throw new AuthorizationException(RL_ACCEPT_NOT_ALLOWED);
    }
    DataValidationUtils.notNull(reviewedBegin, RL_ACCEPTANCE_DATE_INVALID);
    DataValidationUtils.notNull(reviewedEnd, RL_ACCEPTANCE_DATE_INVALID);

    // only an end the overview can show: the end of a month, or the contract end within it
    if(!reviewedEnd.equals(limitToContractEnd(employeecontract, YearMonth.from(reviewedEnd).atEndOfMonth()))) {
      throw new BusinessRuleException(RL_REVIEWED_PERIOD_CHANGED);
    }
    var period = acceptancePeriod(employeecontract, reviewedEnd);
    if(!period.begin().equals(reviewedBegin) || !period.end().equals(reviewedEnd)) {
      throw new BusinessRuleException(RL_REVIEWED_PERIOD_CHANGED);
    }

    var findings = acceptanceFindings(employeecontract, period);
    if (!findings.isEmpty()) {
      throw new BusinessRuleException(findings);
    }

    // set status in timereports
    var timereports = timereportDAO.getCommitedTimereportsByEmployeeContractIdBeforeDate(employeecontractId, reviewedEnd);
    for (var timereport : timereports) {
      acceptTimereport(timereport.getId(), authorizedUser.getLoginSign());
    }

    // set new acceptance date in employee contract
    employeecontractService.updateReportReleaseData(employeecontractId, employeecontract.getReportReleaseDate(), reviewedEnd);

    // compute overtimeStatic and set it in employee contract
    overtimeService.updateOvertimeStatic(employeecontractId);
  }

  /**
   * Das Formular kennt nur Monate, ein Vertrag endet aber mitten im Monat: der Monatsletzte liegt
   * dann hinter {@code validUntil}. Gemeint ist „bis zum Vertragsende" — mehr gibt es dort nicht
   * abzunehmen, und eine Fehlermeldung machte den letzten Monat eines Vertrags unabnehmbar (#324).
   */
  private static LocalDate limitToContractEnd(Employeecontract contract, LocalDate date) {
    if (date == null) return null;
    return min(date, contract.getValidUntil());
  }

  @Authorized(requiresAdmin = true)
  public void reopenTimereports(long employeecontractId, LocalDate reopenDate) {

    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);

    if(reopenDate.isBefore(employeecontract.getValidFrom())) {
      reopenDate = employeecontract.getValidFrom();
    }

    // set status in timereports
    var timereports = timereportDAO.getTimereportsByEmployeeContractIdAfterDate(employeecontractId, reopenDate);
    for (var timereport : timereports) {
      reopenTimereport(timereport.getId());
    }

    LocalDate releaseDate = employeecontract.getReportReleaseDate();
    LocalDate acceptanceDate = employeecontract.getReportAcceptanceDate();
    if(releaseDate != null) {
      var newReportReleaseDate = min(releaseDate, reopenDate.minusDays(1));

      if(newReportReleaseDate.isBefore(employeecontract.getValidFrom())) {
        releaseDate = null;
      } else {
        releaseDate = newReportReleaseDate;
      }
    }
    if(acceptanceDate != null) {
      var newReportAcceptDate = min(acceptanceDate, reopenDate.minusDays(1));
      if(newReportAcceptDate.isBefore(employeecontract.getValidFrom())) {
        acceptanceDate = null;
      } else {
        acceptanceDate = newReportAcceptDate;
      }
    }

    // update accordingly
    employeecontractService.updateReportReleaseData(employeecontractId, releaseDate, acceptanceDate);

    // recompute overtimeStatic and set it in employeecontract
    Duration overtimeStatic = Duration.ZERO;
    // if employee contract had been accepted before, we need to recalculate
    if(acceptanceDate != null) {
      var otStatic = overtimeService.calculateOvertime(employeecontract.getId(), employeecontract.getValidFrom(), acceptanceDate);
      if(otStatic.isPresent()) {
        overtimeStatic = otStatic.get();
      }
    }
    employeecontractService.updateOvertimeStatic(employeecontractId, overtimeStatic);
  }

  /**
   * Die Prüfung vor der Freigabe als Exception: sie wirft, was {@link #collectReleaseFindings}
   * findet — erst die Befunde über den Zeitraum, dann die der einzelnen Tage nach Datum. Die
   * Freigabe prüft damit unmittelbar vor dem Freigeben, was die Übersicht schon gezeigt hat (#760).
   */
  @VisibleForTesting
  protected void validateForRelease(Long employeeContractId, LocalDate releaseDate) {
    var contract = employeecontractDAO.getEmployeecontractById(employeeContractId);
    var findings = collectReleaseFindings(employeeContractId, contract, releaseDate);
    if (!findings.isEmpty()) {
      throw new BusinessRuleException(findings.all());
    }
  }

  /**
   * Der Zeitraum, den eine Freigabe bis {@code end} erfasst: vom Tag nach der letzten Freigabe,
   * ohne Freigabe vom Vertragsbeginn an, bis {@code end} — beides auf die Laufzeit des Vertrags
   * beschnitten. Liegt {@code end} am oder vor dem Tag der letzten Freigabe, ist er leer.
   */
  static ReviewPeriod releasePeriod(Employeecontract contract, LocalDate end) {
    var currentReleaseDate = contract.getReportReleaseDate();
    var begin = currentReleaseDate != null ? currentReleaseDate.plusDays(1) : contract.getValidFrom();
    return new ReviewPeriod(max(begin, contract.getValidFrom()), limitToContractEnd(contract, end));
  }

  /**
   * Die Befunde der Prüfung vor der Freigabe bis {@code releaseDate}, mit ihrem Datum, statt sie zu
   * werfen (#760). Die Übersicht zeigt sie am betroffenen Tag, die Freigabe wirft sie weiterhin.
   *
   * <p>Befunde über den ganzen Zeitraum schließen die Prüfung der Tage aus, sie beantworten die
   * Frage schon: ein Datum außerhalb des Vertrags ({@code RL-0003}), eines vor der Abnahme
   * ({@code RL-0004}) und ein leerer Zeitraum ({@code RL-0009}) — der gewählte Monat ist schon
   * freigegeben. Der leere Zeitraum war bis #760 kein Befund: lag der Monat vor der letzten
   * Freigabe, scheiterte die Regel „Arbeitstag ohne Buchung" an einem verkehrten Zeitraum, und die
   * Freigabe endete auf der Fehlerseite; bis zur letzten Freigabe selbst erneut freizugeben tat
   * nichts und verschickte trotzdem die Mail.
   *
   * <p>Ungültig ist auch ein Datum nach {@link #latestReleaseDate()}: ein Vertrag ohne Ende setzt
   * der Freigabe keine Grenze, und der Monat kommt aus der Anfrage.
   *
   * <p>Die Befunde der Tage sind nach Datum sortiert. Geprüft werden alle offenen Buchungen bis
   * {@code releaseDate}, denn die gibt die Freigabe frei — auch eine, die vor dem Zeitraum liegt.
   *
   * @param employeeContractId die id von {@code contract}; mit ihr fragen die Ladevorgänge
   */
  ReleaseFindings collectReleaseFindings(long employeeContractId, Employeecontract contract, LocalDate releaseDate) {
    if (releaseDate == null
        || releaseDate.isBefore(contract.getValidFrom())
        || (contract.getValidUntil() != null && releaseDate.isAfter(contract.getValidUntil()))
        || releaseDate.isAfter(latestReleaseDate())) {
      return ReleaseFindings.periodWide(RL_RELEASE_DATE_INVALID);
    }
    if (contract.getReportAcceptanceDate() != null && releaseDate.isBefore(contract.getReportAcceptanceDate())) {
      return ReleaseFindings.periodWide(RL_RELEASE_DATE_BEFORE_ACCEPTANCE);
    }
    var period = releasePeriod(contract, releaseDate);
    if (period.isEmpty()) {
      return ReleaseFindings.periodWide(RL_NOTHING_TO_RELEASE);
    }

    final List<DayFinding> errors = new ArrayList<>();

    var begin = period.begin();
    var end = period.end();

    final var workingDays = workingdayDAO
        .getWorkingdaysByEmployeeContractId(employeeContractId, begin.minusDays(1), end)
        .stream()
        .collect(toMap(Workingday::getRefday, identity()));
    var timereports = timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, releaseDate);
    // restricted/external users are not in scope of regulations by law
    if(timereportService.needsWorkingHoursLawValidation(employeeContractId)) {
      final var timeReportsByDate = timereports
          .stream()
          .filter(timeReport -> isRelevantForWorkingTimeValidation(timeReport.getOrderType()))
          .collect(groupingBy(TimereportDTO::getReferenceday));
      if(!timeReportsByDate.isEmpty()) {
        var dates = timeReportsByDate.keySet().stream().sorted().toList();

        // load timereports from the day before (which is not open anymore thus not already loaded)
        var minTimereportDate = timeReportsByDate.keySet().stream().min(LocalDate::compareTo).orElseThrow();
        var extendedTimeReportsByDate = new HashMap<>(timeReportsByDate);
        LocalDate theDayBefore = minTimereportDate.minusDays(1);
        extendedTimeReportsByDate.put(theDayBefore, timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContractId, theDayBefore));

        for(var date : dates) {
          validateBeginOfWorkingDay(date, extendedTimeReportsByDate, workingDays).ifPresent(error -> errors.add(new DayFinding(date, error)));
          validateBreakTime(date, extendedTimeReportsByDate, workingDays).ifPresent(error -> errors.add(new DayFinding(date, error)));
          validateRestTime(date, extendedTimeReportsByDate, workingDays).ifPresent(error -> errors.add(new DayFinding(date, error)));
          validateWorkingDayLength(date, extendedTimeReportsByDate).ifPresent(error -> errors.add(new DayFinding(date, error)));
        }
      }
    }

    // A day has 24 hours for everyone, whatever the working time regulations say about the person:
    // standby is exempt from the 10 hour limit above, but together with the working time it still
    // has to fit into the day (#463).
    timereports.stream()
        .collect(groupingBy(TimereportDTO::getReferenceday))
        .forEach((date, reportsOfDay) -> validateDayLength(date, reportsOfDay)
            .ifPresent(error -> errors.add(new DayFinding(date, error))));

    var publicHolidays = publicholidayDAO.getPublicHolidaysBetween(begin, end)
        .stream()
        .map(Publicholiday::getRefdate)
        .collect(Collectors.toSet());

    // check if all working days have been booked correctly; only open bookings count here,
    // because only they get released (the rule leaves that choice to its caller)
    var bookedDays = timereports.stream().map(TimereportDTO::getReferenceday).collect(Collectors.toSet());
    var daysWithoutBooking = UnbookedWorkingDays.between(begin, end, contract, bookedDays, workingDays, publicHolidays);
    daysWithoutBooking.forEach(date -> errors.add(new DayFinding(date, ServiceFeedbackMessage.error(WD_NO_TIMEREPORT, date))));

    var dayFindings = errors.stream().sorted(Comparator.comparing(DayFinding::date)).toList();
    var openBeforePeriod = timereports.stream()
        .filter(timereport -> timereport.getReferenceday().isBefore(begin))
        .toList();
    return new ReleaseFindings(List.of(), dayFindings, daysWithoutBooking, openBeforePeriod);
  }

  /**
   * Wie weit eine Freigabe höchstens reicht: bis zum Ende des Monats in einem Jahr. Die Übersicht
   * steht unter einer Adresse, die jeder Angemeldete mit einem beliebigen Monat aufrufen kann, und
   * für einen Vertrag ohne Ende bestimmte ein Aufruf mit {@code until=9999-12} sonst Befunde, Tage
   * und Bilanz über Jahrtausende — genug, um den Speicher der Anwendung zu erschöpfen (#760). Eine
   * echte Freigabe kommt der Grenze nicht nahe: sie verlangt jeden Arbeitstag bis zu ihrem Ende
   * gebucht oder als nicht gearbeitet markiert.
   */
  static LocalDate latestReleaseDate() {
    return YearMonth.from(today()).plusYears(1).atEndOfMonth();
  }

  /**
   * Was die Prüfung vor der Freigabe findet (#760).
   *
   * @param periodFindings     Befunde über den ganzen Zeitraum; gibt es welche, sind die Tage nicht
   *                           geprüft
   * @param dayFindings        Befunde einzelner Tage, nach Datum sortiert
   * @param daysWithoutBooking die Arbeitstage ohne offene Buchung, aufsteigend — die Tage der
   *                           Befunde {@code WD_NO_TIMEREPORT}
   * @param openBeforePeriod   offene Buchungen vor dem Zeitraum; die Freigabe erfasst sie mit,
   *                           denn sie gibt alle offenen Buchungen bis zu ihrem Ende frei
   */
  record ReleaseFindings(List<ServiceFeedbackMessage> periodFindings,
                         List<DayFinding> dayFindings,
                         List<LocalDate> daysWithoutBooking,
                         List<TimereportDTO> openBeforePeriod) {

    static ReleaseFindings periodWide(ErrorCode errorCode) {
      return new ReleaseFindings(List.of(ServiceFeedbackMessage.error(errorCode)), List.of(), List.of(), List.of());
    }

    boolean isEmpty() {
      return periodFindings.isEmpty() && dayFindings.isEmpty();
    }

    /** Alle Befunde: die über den Zeitraum zuerst, dann die der Tage nach Datum. */
    List<ServiceFeedbackMessage> all() {
      var all = new ArrayList<>(periodFindings);
      dayFindings.forEach(finding -> all.add(finding.message()));
      return all;
    }
  }

  /**
   * Die Arbeitstage der Vorwoche ohne Buchung, für den Hinweis im Dashboard (#1124). Vorwoche heißt
   * Montag bis Sonntag vor der laufenden Woche; ab Montag rückt sie weiter. Was ein Arbeitstag ohne
   * Buchung ist, entscheidet {@link UnbookedWorkingDays}, dieselbe Regel wie bei der Freigabe.
   *
   * <p>Anders als bei der Freigabe zählt eine Buchung jedes Status: ein Monatsende mitten in der
   * Woche lässt Tage zurück, die schon freigegeben und trotzdem gebucht sind — mit nur den offenen
   * Buchungen erschienen sie als Lücke.
   *
   * <p>Keinen Hinweis bekommen Freelancer, Personen mit Status {@code restricted} und Verträge ohne
   * Sollarbeitszeit (entschieden in #1123). Die Ausnahmen gehören zum Hinweis, nicht zur Regel: die
   * Freigabe prüft auch diese Verträge.
   *
   * <p>Die Buchungstage kommen ohne den zeilenweisen READ-Filter von {@code TimereportDAO}; die
   * Arbeitstage kommen, wie in {@link #validateForRelease}, über {@code WorkingdayDAO} an der
   * Leseprüfung von {@code WorkingdayService} vorbei. Vorher wird das Schreibrecht auf die Freigabe
   * verlangt, und was der Hinweis damit preisgibt, beruht auf drei Voraussetzungen:
   * <ul>
   *   <li>{@link ReleaseAuthorization#isReleaseAuthorized} lässt die Person selbst, die
   *       Geschäftsführung, die zuständige Personalverantwortung, Admins und Inhaber einer
   *       Freigaberegel mit Schreibrecht zu;</li>
   *   <li>die ersten vier dürfen nach {@code TimereportAuthorization} jede Buchung des Vertrags
   *       lesen, für sie ist der Filter wirkungslos;</li>
   *   <li>wer nur über eine Freigaberegel zugelassen ist, darf die Buchungen der Person nicht
   *       unbedingt lesen. Er erfährt hier, an welchen Arbeitstagen der Vorwoche weder etwas
   *       gebucht noch der Tag als nicht gearbeitet markiert ist, im Umkehrschluss also, dass an
   *       den übrigen eins von beidem zutrifft; Inhalt und Status einer Buchung erfährt er nicht.
   *       Die Freigabe verrät ihm das nicht in dieser Form: sie liest die offenen Buchungen durch
   *       den READ-Filter, meldet ihm ohne Leserecht jeden Arbeitstag des Zeitraums als Lücke und
   *       prüft nur die Tage nach der letzten Freigabe, während der Hinweis auch schon freigegebene
   *       Tage der Vorwoche umfasst. Dass an einem Tag etwas gebucht ist, ist für jemanden, der die
   *       Buchungen dieser Person freigeben darf, hingenommen (#1124). Deshalb {@code WRITE}: wer
   *       die Freigabe nur lesen darf, kann nicht freigeben, und für ihn gälte diese Abwägung
   *       nicht.</li>
   * </ul>
   * Wer nicht freigeben darf, bekommt eine leere Liste statt einer Ausnahme — ein gemerkter fremder
   * Vertrag soll die Startseite nicht sperren.
   *
   * <p>Die Startseite ruft das bei jedem Aufruf: Buchungstage, Arbeitstage und Feiertage werden je
   * einmal für die ganze Woche geladen, nie je Tag, und für einen ausgenommenen Vertrag gar nicht.
   */
  @Transactional(readOnly = true)
  public List<LocalDate> getUnbookedWorkingDaysOfPreviousWeek(long employeecontractId) {
    var contract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    if (contract == null || !releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.WRITE)) {
      return List.of();
    }
    if (TRUE.equals(contract.getFreelancer())
        || contract.getEmployee().isRestricted()
        || contract.getDailyWorkingTime().isZero()) {
      return List.of();
    }

    var monday = today().with(DayOfWeek.MONDAY).minusWeeks(1);
    var sunday = monday.plusDays(6);

    var bookedDays = Set.copyOf(timereportRepository.findBookedDaysBetween(employeecontractId, monday, sunday));
    var workingDays = workingdayDAO.getWorkingdaysByEmployeeContractId(employeecontractId, monday, sunday)
        .stream()
        .collect(toMap(Workingday::getRefday, identity()));
    var publicHolidays = publicholidayDAO.getPublicHolidaysBetween(monday, sunday)
        .stream()
        .map(Publicholiday::getRefdate)
        .collect(Collectors.toSet());

    return UnbookedWorkingDays.between(monday, sunday, contract, bookedDays, workingDays, publicHolidays);
  }

  private void releaseTimereport(long timereportId, String releasedBy) {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidationUtils.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    timereportService.updateReleaseData(timereportId,
        TIMEREPORT_STATUS_COMMITED,
        releasedBy,
        now(),
        timereport.getAcceptedby(),
        timereport.getAccepted()
    );
  }

  private void acceptTimereport(long timereportId, String acceptedBy) {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidationUtils.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    timereportService.updateReleaseData(timereportId,
        TIMEREPORT_STATUS_CLOSED,
        timereport.getReleasedby(),
        timereport.getReleased(),
        acceptedBy,
        now()
    );
  }

  public void reopenTimereport(long timereportId) {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidationUtils.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    timereportService.updateReleaseData(timereportId,
        TIMEREPORT_STATUS_OPEN,
        null,
        null,
        null,
        null
    );
  }

  public void sendReleaseReminderMail(long employeeId) {

    var recipient = employeeService.getEmployeeById(employeeId);
    var sender = employeeService.getLoginEmployee();

    String subject = "SALAT: Erinnerung SALAT freigeben";
    StringBuilder message = new StringBuilder();
    if (GlobalConstants.GENDER_FEMALE == recipient.getGender()) {
      message.append("Liebe ");
    } else {
      message.append("Lieber ");
    }
    message.append(recipient.getFirstname());
    message.append(",\n\n");
    message.append("bitte gib deine SALAT-Buchungen des abgelaufenen Monats frei.\n\n");
    message.append(sender.getName());

    mailService.sendEmail(
        subject,
        message.toString(),
        new MailContact(sender.getName(), employeePreferenceService.getNotificationEmailFor(sender)),
        new MailContact(recipient.getName(), employeePreferenceService.getNotificationEmailFor(recipient))
    );
  }

  public void sendAcceptanceReminderMail(long employeecontracttoAcceptId) {

    var employeeContract = employeecontractService.getEmployeecontractById(employeecontracttoAcceptId);
    var coworker = employeeContract.getEmployee();
    var sender = employeeService.getLoginEmployee();

    String subject = "SALAT: Erinnerung SALAT-Freigabe abnehmen";
    for (var recipient : employeeContract.getSupervisors()) {
      StringBuilder message = new StringBuilder();
      if (GlobalConstants.GENDER_FEMALE == recipient.getGender()) {
        message.append("Liebe ");
      } else {
        message.append("Lieber ");
      }
      message.append(recipient.getFirstname());
      message.append(",\n\n");
      message.append("bitte nimm die SALAT-Buchungen des abgelaufenen Monats von ");
      if (GlobalConstants.GENDER_FEMALE == coworker.getGender()) {
        message.append("Kollegin ");
      } else {
        message.append("Kollege ");
      }
      message.append(coworker.getName());
      message.append(" ab.\n\n");
      message.append(sender.getName());

      mailService.sendEmail(
          subject,
          message.toString(),
          new MailContact(sender.getName(), employeePreferenceService.getNotificationEmailFor(sender)),
          new MailContact(recipient.getName(), employeePreferenceService.getNotificationEmailFor(recipient))
      );
    }
  }

  private void sendTimeReportsReleasedMail(Employeecontract releasedEmployeeContract) {
    var sender = releasedEmployeeContract.getEmployee();
    String subject = "SALAT: Buchungen durch " + sender.getSign() + " freigegeben";

    for (var recipient : releasedEmployeeContract.getSupervisors()) {
      StringBuilder message = new StringBuilder();
      if (GlobalConstants.GENDER_FEMALE == recipient.getGender()) {
        message.append("Liebe Personalverantwortliche ");
      } else {
        message.append("Lieber Personalverantwortlicher ");
      }
      message.append(recipient.getFirstname());
      message.append(",\n\n");
      message.append(sender.getName());
      message.append(" hat eben ");
      if (GlobalConstants.GENDER_FEMALE == sender.getGender()) {
        message.append("ihre ");
      } else {
        message.append("seine ");
      }
      message.append("SALAT-Buchungen freigegeben.\n");
      message.append("Bitte nimm diese ab.");

      mailService.sendEmail(
          subject,
          message.toString(),
          new MailContact(sender.getName(), employeePreferenceService.getNotificationEmailFor(sender)),
          new MailContact(recipient.getName(), employeePreferenceService.getNotificationEmailFor(recipient))
      );
    }
  }

  /**
   * Der Zeitraum, den eine Abnahme bis {@code end} erfasst (#1122): vom Tag nach der letzten
   * Abnahme, ohne Abnahme vom Vertragsbeginn an, bis {@code end} — beides auf die Laufzeit des
   * Vertrags beschnitten. Liegt {@code end} am oder vor dem Tag der letzten Abnahme, ist er leer.
   */
  static ReviewPeriod acceptancePeriod(Employeecontract contract, LocalDate end) {
    var currentAcceptanceDate = contract.getReportAcceptanceDate();
    var begin = currentAcceptanceDate != null ? currentAcceptanceDate.plusDays(1) : contract.getValidFrom();
    return new ReviewPeriod(max(begin, contract.getValidFrom()), limitToContractEnd(contract, end));
  }

  /**
   * Die Befunde über den Zeitraum einer Abnahme (#1122), gesammelt statt geworfen: die Übersicht
   * zeigt sie, die Abnahme wirft sie. Es gilt der erste, der zutrifft, denn jeder beantwortet die
   * Frage schon:
   *
   * <ol>
   *   <li>{@code RL-0005}: das Ende liegt außerhalb des Vertrags.</li>
   *   <li>{@code RL-0011}: es ist nichts freigegeben. Bis #1122 ließ sich ein nie freigegebener
   *       Vertrag abnehmen: das Überstundenkonto wurde festgeschrieben, die Buchungen blieben offen,
   *       und keine davon wurde abgenommen.</li>
   *   <li>{@code RL-0006}: das Ende liegt hinter der Freigabe.</li>
   *   <li>{@code RL-0007}: das Ende liegt vor der letzten Abnahme — für jeden. Bis #1122 durfte die
   *       Administration die Abnahme so zurücksetzen (#652); die Buchungen dazwischen blieben
   *       abgenommen, obwohl sie nun hinter dem Abnahmedatum lagen, und das festgeschriebene
   *       Überstundenkonto zählte sie nicht mehr. Zurück geht es mit „Öffnen", das Freigabe,
   *       Abnahme und Buchungen gemeinsam zurücksetzt.</li>
   *   <li>{@code RL-0010}: der Zeitraum ist leer, der Monat ist schon abgenommen.</li>
   * </ol>
   *
   * <p>Die Tage prüft die Abnahme nicht: das hat die Freigabe getan.
   */
  private static List<ServiceFeedbackMessage> acceptanceFindings(Employeecontract contract, ReviewPeriod period) {
    var end = period.end();
    if (end.isBefore(contract.getValidFrom())
        || (contract.getValidUntil() != null && end.isAfter(contract.getValidUntil()))) {
      return List.of(ServiceFeedbackMessage.error(RL_ACCEPTANCE_DATE_INVALID));
    }
    if (contract.getReportReleaseDate() == null) {
      return List.of(ServiceFeedbackMessage.error(RL_ACCEPTANCE_WITHOUT_RELEASE));
    }
    if (end.isAfter(contract.getReportReleaseDate())) {
      return List.of(ServiceFeedbackMessage.error(RL_ACCEPTANCE_DATE_AFTER_RELEASE));
    }
    if (contract.getReportAcceptanceDate() != null && end.isBefore(contract.getReportAcceptanceDate())) {
      return List.of(ServiceFeedbackMessage.error(RL_ACCEPTANCE_DATE_MOVED_BACKWARDS));
    }
    if (period.isEmpty()) {
      return List.of(ServiceFeedbackMessage.error(RL_NOTHING_TO_ACCEPT));
    }
    return List.of();
  }

  private Optional<ServiceFeedbackMessage> validateRestTime(LocalDate date,
      Map<LocalDate, List<TimereportDTO>> timeReports,
      Map<LocalDate, Workingday> workingDays) {

    LocalDate dayBeforeDate = date.minusDays(1);
    if(noTimeReportsFound(timeReports, date) || noTimeReportsFound(timeReports, dayBeforeDate)) return empty();

    Workingday workingDay = workingDays.get(date);
    Workingday theDayBefore = workingDays.get(dayBeforeDate);
    if (workingDay == null || theDayBefore == null) return empty();

    Duration theDayBeforeWorkDurationSum = timeReports.get(theDayBefore.getRefday()).stream()
        .filter(timeReport -> isRelevantForWorkingTimeValidation(timeReport.getOrderType()))
        .map(TimereportDTO::getDuration)
        .reduce(Duration.ZERO, Duration::plus);
    if(!theDayBeforeWorkDurationSum.isPositive()) return empty(); // not worked = no validation
    LocalDateTime theDayBeforeEndOfWorkingDay = theDayBefore.getStartOfWorkingDay().plus(theDayBefore.getBreakLength()).plus(theDayBeforeWorkDurationSum);
    LocalDateTime startOfWorkingDay = workingDay.getStartOfWorkingDay();
    Duration restTime = Duration.between(theDayBeforeEndOfWorkingDay, startOfWorkingDay);
    if (restTime.toMinutes() < REST_PERIOD_IN_MINUTES) {
      return of(ServiceFeedbackMessage.error(ErrorCode.WD_REST_TIME_TOO_SHORT, date));
    }
    return empty();
  }

  /**
   * Everything booked on one day has to fit into it (#463). Unlike
   * {@link #validateWorkingDayLength} this counts standby too — it is the only limit standby is
   * subject to.
   */
  @VisibleForTesting
  static Optional<ServiceFeedbackMessage> validateDayLength(LocalDate date, List<TimereportDTO> reportsOfDay) {
    Duration bookedSum = reportsOfDay.stream()
        .map(TimereportDTO::getDuration)
        .reduce(Duration.ZERO, Duration::plus);
    if(bookedSum.toMinutes() > DAY_MAX_LENGTH_ALLOWED_IN_MINUTES) {
      return of(ServiceFeedbackMessage.error(WD_DAY_LENGTH_TOO_LONG, date));
    }
    return empty();
  }

  private Optional<ServiceFeedbackMessage> validateWorkingDayLength(LocalDate date,
      HashMap<LocalDate, List<TimereportDTO>> timeReports) {
    if(noTimeReportsFound(timeReports, date)) return empty();
    Duration workDurationSum = timeReports.get(date).stream()
        .filter(timeReport -> isRelevantForWorkingTimeValidation(timeReport.getOrderType()))
        .map(TimereportDTO::getDuration)
        .reduce(Duration.ZERO, Duration::plus);
    if(workDurationSum.toMinutes() > WORKDAY_MAX_LENGTH_ALLOWED_IN_MINUTES) {
      return of(ServiceFeedbackMessage.error(WD_LENGTH_TOO_LONG, date));
    }
    return empty();
  }

}
