package org.tb.dailyreport.service;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.tb.common.GlobalConstants.REST_PERIOD_IN_MINUTES;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.GlobalConstants.WORKDAY_MAX_LENGTH_ALLOWED_IN_MINUTES;
import static org.tb.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.WD_LENGTH_TOO_LONG;
import static org.tb.common.exception.ErrorCode.WD_NO_TIMEREPORT;
import static org.tb.common.util.DateUtils.min;
import static org.tb.common.util.DateUtils.now;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.WORKED;
import static org.tb.dailyreport.service.TimereportService.isRelevantForWorkingTimeValidation;
import static org.tb.dailyreport.service.TimereportService.noTimeReportsFound;
import static org.tb.dailyreport.service.TimereportService.validateBeginOfWorkingDay;
import static org.tb.dailyreport.service.TimereportService.validateBreakTime;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.service.SimpleMailService;
import org.tb.common.service.SimpleMailService.MailContact;
import org.tb.common.util.DataValidationUtils;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReleaseService {

  private final EmployeecontractDAO employeecontractDAO;
  private final TimereportDAO timereportDAO;
  private final TimereportRepository timereportRepository;
  private final PublicholidayDAO publicholidayDAO;
  private final OvertimeService overtimeService;
  private final AuthorizedUser authorizedUser;
  private final WorkingdayDAO workingdayDAO;
  private final SimpleMailService simpleMailService;
  private final EmployeeService employeeService;
  private final EmployeecontractService employeecontractService;
  private final TimereportService timereportService;

  public void releaseTimereports(long employeecontractId, LocalDate releaseDate) {
    validateForRelease(employeecontractId, releaseDate);

    // set status in timereports
    var timereports = timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(
        employeecontractId,
        releaseDate
    );
    for (var timereport : timereports) {
      releaseTimereport(timereport.getId(), authorizedUser.getSign());
    }

    // store new release date in employee contract
    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    employeecontractService.updateReportReleaseData(employeecontractId, releaseDate, employeecontract.getReportAcceptanceDate());

    sendTimeReportsReleasedMail(employeecontract);
  }

  public void acceptTimereports(long employeecontractId, LocalDate acceptanceDate) {
    // set status in timereports
    var timereports = timereportDAO.getCommitedTimereportsByEmployeeContractIdBeforeDate(employeecontractId, acceptanceDate);
    for (var timereport : timereports) {
      acceptTimereport(timereport.getId(), authorizedUser.getSign());
    }

    // set new acceptance date in employee contract
    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    employeecontractService.updateReportReleaseData(employeecontractId, employeecontract.getReportReleaseDate(), acceptanceDate);

    //compute overtimeStatic and set it in employee contract
    overtimeService.updateOvertimeStatic(employeecontract.getId());
  }

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

      // recompute overtimeStatic and set it in employeecontract
      Duration overtimeStatic = Duration.ZERO;
      // if employee contract had been accepted before, we need to recalculate
      if(employeecontract.getReportAcceptanceDate() != null) {
        var otStatic = overtimeService.calculateOvertime(employeecontract.getId(), employeecontract.getValidFrom(), employeecontract.getReportAcceptanceDate());
        if(otStatic.isPresent()) {
          overtimeStatic = otStatic.get();
        }
      }

      // update accordingly
      employeecontractService.updateReportReleaseData(employeecontractId, releaseDate, acceptanceDate);
      employeecontractService.updateOvertimeStatic(employeecontractId, overtimeStatic);

    }
  }

  @VisibleForTesting
  protected void validateForRelease(Long employeeContractId, LocalDate releaseDate) {
    final List<Pair<LocalDate, ServiceFeedbackMessage>> errors = new ArrayList<>();

    var contract = employeecontractDAO.getEmployeecontractById(employeeContractId);

    var currentReleaseDate = contract.getReportReleaseDate();
    var begin = currentReleaseDate != null ? currentReleaseDate.plusDays(1) : contract.getValidFrom();
    var end = releaseDate;

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

    var publicHolidays = publicholidayDAO.getPublicHolidaysBetween(begin, end)
        .stream()
        .map(Publicholiday::getRefdate)
        .collect(Collectors.toSet());

    final var timeReportsByDate = timereports
        .stream()
        .collect(groupingBy(TimereportDTO::getReferenceday));

    // check if all working days have been booked correctly
    begin.datesUntil(end.plusDays(1))
        .filter(date -> date.getDayOfWeek() != SATURDAY && date.getDayOfWeek() != SUNDAY)
        .filter(date -> !publicHolidays.contains(date))
        .map(date -> {
          Optional<Pair<LocalDate, ServiceFeedbackMessage>> result = empty();
          var workingDay = workingDays.get(date);
          var workingDayType = WORKED; // this is the default
          if(workingDay != null) workingDayType = workingDay.getType();
          if(workingDayType != NOT_WORKED && noTimeReportsFound(timeReportsByDate, date)) {
            result = of(Pair.of(date, ServiceFeedbackMessage.error(WD_NO_TIMEREPORT, date)));
          }
          return result;
        })
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(errors::add);

    var messages = errors.stream().sorted(Comparator.comparing(Pair::getFirst)).map(Pair::getSecond).toList();
    if(!messages.isEmpty()) {
      throw new BusinessRuleException(messages);
    }
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
    var sender = employeeService.getEmployeeById(authorizedUser.getEmployeeId());

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

    simpleMailService.sendEmail(
        subject,
        message.toString(),
        new MailContact(sender.getName(), sender.getEmailAddress()),
        new MailContact(recipient.getName(), recipient.getEmailAddress())
    );
  }

  public void sendAcceptanceReminderMail(long employeecontracttoAcceptId) {

    var employeeContract = employeecontractService.getEmployeecontractById(employeecontracttoAcceptId);
    var coworker = employeeContract.getEmployee();
    var recipient = employeeContract.getSupervisor();
    var sender = employeeService.getEmployeeById(authorizedUser.getEmployeeId());

    String subject = "SALAT: Erinnerung SALAT-Freigabe abnehmen";
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

    simpleMailService.sendEmail(
        subject,
        message.toString(),
        new MailContact(sender.getName(), sender.getEmailAddress()),
        new MailContact(recipient.getName(), recipient.getEmailAddress())
    );
  }

  private void sendTimeReportsReleasedMail(Employeecontract releasedEmployeeContract) {
    var sender = releasedEmployeeContract.getEmployee();
    var recipient = releasedEmployeeContract.getSupervisor();

    String subject = "SALAT: Buchungen durch " + sender.getSign() + " freigegeben";
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

    simpleMailService.sendEmail(
        subject,
        message.toString(),
        new MailContact(sender.getName(), sender.getEmailAddress()),
        new MailContact(recipient.getName(), recipient.getEmailAddress())
    );
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
