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
import static org.tb.common.exception.ErrorCode.RL_NOTHING_TO_RELEASE;
import static org.tb.common.exception.ErrorCode.RL_RELEASE_DATE_BEFORE_ACCEPTANCE;
import static org.tb.common.exception.ErrorCode.RL_RELEASE_DATE_INVALID;
import static org.tb.common.exception.ErrorCode.RL_RELEASE_NOT_ALLOWED;
import static org.tb.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.WD_DAY_LENGTH_TOO_LONG;
import static org.tb.common.exception.ErrorCode.WD_LENGTH_TOO_LONG;
import static org.tb.common.exception.ErrorCode.WD_NO_TIMEREPORT;
import static org.tb.common.util.DateTimeUtils.now;
import static org.tb.common.util.DateUtils.max;
import static org.tb.common.util.DateUtils.min;
import static org.tb.common.util.DateUtils.today;
import static org.tb.dailyreport.service.TimereportService.isRelevantForWorkingTimeValidation;
import static org.tb.dailyreport.service.TimereportService.noTimeReportsFound;
import static org.tb.dailyreport.service.TimereportService.validateBeginOfWorkingDay;
import static org.tb.dailyreport.service.TimereportService.validateBreakTime;

import com.google.common.annotations.VisibleForTesting;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
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
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.ReviewPeriod;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportDTO;
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

  public void releaseTimereports(long employeecontractId, LocalDate releaseDate) {
    // check authorization
    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    if(!releaseAuthorization.isReleaseAuthorized(employeecontract, AccessLevel.WRITE)) {
      throw new AuthorizationException(RL_RELEASE_NOT_ALLOWED);
    }

    var effectiveReleaseDate = limitToContractEnd(employeecontract, releaseDate);

    validateForRelease(employeecontractId, effectiveReleaseDate);

    // set status in timereports
    var timereports = timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(
        employeecontractId,
        effectiveReleaseDate
    );
    for (var timereport : timereports) {
      releaseTimereport(timereport.getId(), authorizedUser.getLoginSign());
    }

    // store new release date in employee contract
    employeecontractService.updateReportReleaseData(employeecontractId, effectiveReleaseDate, employeecontract.getReportAcceptanceDate());

    sendTimeReportsReleasedMail(employeecontract);
  }

  public void acceptTimereports(long employeecontractId, LocalDate acceptanceDate) {
    // check authorization
    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    if(!releaseAuthorization.isAcceptAuthorized(employeecontract, AccessLevel.WRITE)) {
      throw new AuthorizationException(RL_ACCEPT_NOT_ALLOWED);
    }

    var effectiveAcceptanceDate = limitToContractEnd(employeecontract, acceptanceDate);

    validateForAcceptance(employeecontractId, effectiveAcceptanceDate);

    // set status in timereports
    var timereports = timereportDAO.getCommitedTimereportsByEmployeeContractIdBeforeDate(employeecontractId, effectiveAcceptanceDate);
    for (var timereport : timereports) {
      acceptTimereport(timereport.getId(), authorizedUser.getLoginSign());
    }

    // set new acceptance date in employee contract
    employeecontractService.updateReportReleaseData(employeecontractId, employeecontract.getReportReleaseDate(), effectiveAcceptanceDate);

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
   * <p>Die Befunde der Tage sind nach Datum sortiert. Geprüft werden alle offenen Buchungen bis
   * {@code releaseDate}, denn die gibt die Freigabe frei — auch eine, die vor dem Zeitraum liegt.
   *
   * @param employeeContractId die id von {@code contract}; mit ihr fragen die Ladevorgänge
   */
  ReleaseFindings collectReleaseFindings(long employeeContractId, Employeecontract contract, LocalDate releaseDate) {
    if (releaseDate == null
        || releaseDate.isBefore(contract.getValidFrom())
        || (contract.getValidUntil() != null && releaseDate.isAfter(contract.getValidUntil()))) {
      return ReleaseFindings.periodWide(RL_RELEASE_DATE_INVALID);
    }
    if (contract.getReportAcceptanceDate() != null && releaseDate.isBefore(contract.getReportAcceptanceDate())) {
      return ReleaseFindings.periodWide(RL_RELEASE_DATE_BEFORE_ACCEPTANCE);
    }
    var period = releasePeriod(contract, releaseDate);
    if (period.isEmpty()) {
      return ReleaseFindings.periodWide(RL_NOTHING_TO_RELEASE);
    }

    final List<Pair<LocalDate, ServiceFeedbackMessage>> errors = new ArrayList<>();

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
          validateBeginOfWorkingDay(date, extendedTimeReportsByDate, workingDays).ifPresent(error -> errors.add(Pair.of(date, error)));
          validateBreakTime(date, extendedTimeReportsByDate, workingDays).ifPresent(error -> errors.add(Pair.of(date, error)));
          validateRestTime(date, extendedTimeReportsByDate, workingDays).ifPresent(error -> errors.add(Pair.of(date, error)));
          validateWorkingDayLength(date, extendedTimeReportsByDate).ifPresent(error -> errors.add(Pair.of(date, error)));
        }
      }
    }

    // A day has 24 hours for everyone, whatever the working time regulations say about the person:
    // standby is exempt from the 10 hour limit above, but together with the working time it still
    // has to fit into the day (#463).
    timereports.stream()
        .collect(groupingBy(TimereportDTO::getReferenceday))
        .forEach((date, reportsOfDay) -> validateDayLength(date, reportsOfDay)
            .ifPresent(error -> errors.add(Pair.of(date, error))));

    var publicHolidays = publicholidayDAO.getPublicHolidaysBetween(begin, end)
        .stream()
        .map(Publicholiday::getRefdate)
        .collect(Collectors.toSet());

    // check if all working days have been booked correctly; only open bookings count here,
    // because only they get released (the rule leaves that choice to its caller)
    var bookedDays = timereports.stream().map(TimereportDTO::getReferenceday).collect(Collectors.toSet());
    var daysWithoutBooking = UnbookedWorkingDays.between(begin, end, contract, bookedDays, workingDays, publicHolidays);
    daysWithoutBooking.forEach(date -> errors.add(Pair.of(date, ServiceFeedbackMessage.error(WD_NO_TIMEREPORT, date))));

    var dayFindings = errors.stream().sorted(Comparator.comparing(Pair::getFirst)).toList();
    return new ReleaseFindings(List.of(), dayFindings, daysWithoutBooking);
  }

  /**
   * Was die Prüfung vor der Freigabe findet (#760).
   *
   * @param periodFindings     Befunde über den ganzen Zeitraum; gibt es welche, sind die Tage nicht
   *                           geprüft
   * @param dayFindings        Befunde einzelner Tage, nach Datum sortiert
   * @param daysWithoutBooking die Arbeitstage ohne offene Buchung, aufsteigend — die Tage der
   *                           Befunde {@code WD_NO_TIMEREPORT}
   */
  record ReleaseFindings(List<ServiceFeedbackMessage> periodFindings,
                         List<Pair<LocalDate, ServiceFeedbackMessage>> dayFindings,
                         List<LocalDate> daysWithoutBooking) {

    static ReleaseFindings periodWide(ErrorCode errorCode) {
      return new ReleaseFindings(List.of(ServiceFeedbackMessage.error(errorCode)), List.of(), List.of());
    }

    boolean isEmpty() {
      return periodFindings.isEmpty() && dayFindings.isEmpty();
    }

    /** Alle Befunde: die über den Zeitraum zuerst, dann die der Tage nach Datum. */
    List<ServiceFeedbackMessage> all() {
      var all = new ArrayList<>(periodFindings);
      dayFindings.forEach(finding -> all.add(finding.getSecond()));
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

  private void validateForAcceptance(long employeeContractId, LocalDate acceptanceDate) {
    var contract = employeecontractDAO.getEmployeecontractById(employeeContractId);
    if (acceptanceDate == null
        || acceptanceDate.isBefore(contract.getValidFrom())
        || (contract.getValidUntil() != null && acceptanceDate.isAfter(contract.getValidUntil()))) {
      throw new BusinessRuleException(RL_ACCEPTANCE_DATE_INVALID);
    }
    if (contract.getReportReleaseDate() != null && acceptanceDate.isAfter(contract.getReportReleaseDate())) {
      throw new BusinessRuleException(RL_ACCEPTANCE_DATE_AFTER_RELEASE);
    }
    if (!authorizedUser.isAdmin()
        && contract.getReportAcceptanceDate() != null
        && acceptanceDate.isBefore(contract.getReportAcceptanceDate())) {
      throw new BusinessRuleException(RL_ACCEPTANCE_DATE_MOVED_BACKWARDS);
    }
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
