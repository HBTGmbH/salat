package org.tb.dailyreport.service;

import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static org.tb.common.ErrorCode.TR_CLOSED_TIME_REPORT_REQ_ADMIN;
import static org.tb.common.ErrorCode.TR_COMMITTED_TIME_REPORT_REQ_MANAGER;
import static org.tb.common.ErrorCode.TR_DURATION_HOURS_INVALID;
import static org.tb.common.ErrorCode.TR_DURATION_INVALID;
import static org.tb.common.ErrorCode.TR_DURATION_MINUTES_INVALID;
import static org.tb.common.ErrorCode.TR_DURATION_OVERTIME_COMPENSATION_INVALID;
import static org.tb.common.ErrorCode.TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE;
import static org.tb.common.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.common.ErrorCode.TR_EMPLOYEE_ORDER_INVALID_REF_DATE;
import static org.tb.common.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;
import static org.tb.common.ErrorCode.TR_MONTH_BUDGET_EXCEEDED;
import static org.tb.common.ErrorCode.TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;
import static org.tb.common.ErrorCode.TR_REFERENCE_DAY_NULL;
import static org.tb.common.ErrorCode.TR_SEQUENCE_NUMBER_ALREADY_SET;
import static org.tb.common.ErrorCode.TR_SUBORDER_COMMENT_MANDATORY;
import static org.tb.common.ErrorCode.TR_TASK_DESCRIPTION_INVALID_LENGTH;
import static org.tb.common.ErrorCode.TR_TIME_REPORT_NOT_FOUND;
import static org.tb.common.ErrorCode.TR_TOTAL_BUDGET_EXCEEDED;
import static org.tb.common.ErrorCode.TR_YEAR_BUDGET_EXCEEDED;
import static org.tb.common.ErrorCode.TR_YEAR_OUT_OF_RANGE;
import static org.tb.common.GlobalConstants.COMMENT_MAX_LENGTH;
import static org.tb.common.GlobalConstants.DEBITHOURS_UNIT_MONTH;
import static org.tb.common.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;
import static org.tb.common.GlobalConstants.DEBITHOURS_UNIT_YEAR;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.common.GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.util.DateUtils.getFirstDay;
import static org.tb.common.util.DateUtils.getLastDay;
import static org.tb.common.util.DateUtils.getYear;
import static org.tb.common.util.DateUtils.getYearMonth;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.AuthorizedUser;
import org.tb.common.BusinessRuleChecks;
import org.tb.common.DataValidation;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.ReferencedayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.OvertimeService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;

@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class TimereportService {

  private final EmployeecontractDAO employeecontractDAO;
  private final ReferencedayDAO referencedayDAO;
  private final EmployeeorderDAO employeeorderDAO;
  private final TimereportDAO timereportDAO;
  private final TimereportRepository timereportRepository;
  private final PublicholidayDAO publicholidayDAO;
  private final OvertimeService overtimeService;

  public void createTimereports(AuthorizedUser authorizedUser, long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, long durationHours, long durationMinutes, int numberOfSerialDays)
  throws AuthorizationException, InvalidDataException, BusinessRuleException {

    Timereport timereportTemplate = new Timereport();
    validateParametersAndFillTimereport(employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, durationHours,
        durationMinutes, timereportTemplate);

    // create a timereport for every serial day requested - in most cases this is 1
    List<Timereport> timereportsToSave = new ArrayList<>();
    for (int i = 0; i < numberOfSerialDays; i++) {
      Timereport timereport = timereportTemplate.getTwin();
      setSequencenumber(timereport);
      setStatus(timereport);
      timereportsToSave.add(timereport);
      // increment to next workable day
      Referenceday nextWorkableDay = getNextWorkableDay(timereportTemplate.getReferenceday());
      timereportTemplate.setReferenceday(nextWorkableDay);
    }

    checkAndSaveTimereports(authorizedUser, timereportsToSave);
  }

  public void updateTimereport(AuthorizedUser authorizedUser, long timereportId, long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, long durationHours, long durationMinutes)
      throws AuthorizationException, InvalidDataException, BusinessRuleException {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidation.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    validateParametersAndFillTimereport(employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, durationHours,
        durationMinutes, timereport);
    checkAndSaveTimereports(authorizedUser, Collections.singletonList(timereport));
  }

  /**
   * shifts a timereport by days
   */
  public void shiftDays(long timereportId, int amountDays, AuthorizedUser authorizedUser)
      throws AuthorizationException, InvalidDataException, BusinessRuleException {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidation.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    Referenceday referenceday = timereport.getReferenceday();
    LocalDate shiftedDate = DateUtils.addDays(referenceday.getRefdate(), amountDays);
    updateTimereport(authorizedUser,
        timereport.getId(),
        timereport.getEmployeecontract().getId(),
        timereport.getEmployeeorder().getId(),
        shiftedDate,
        timereport.getTaskdescription(),
        TRUE.equals(timereport.getTraining()),
        timereport.getDurationhours(),
        timereport.getDurationminutes());
  }

  public boolean deleteTimereport(long timereportId, AuthorizedUser authorizedUser)
      throws AuthorizationException, InvalidDataException, BusinessRuleException {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidation.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    checkAuthorization(Collections.singletonList(timereport), authorizedUser);
    timereportDAO.deleteTimereportById(timereportId);
    return true;
  }

  /**
   * deletes many timereports at once
   *
   * @param timereportIds ids of the timereports
   */
  public void deleteTimereports(List<Long> timereportIds, AuthorizedUser authorizedUser)
      throws AuthorizationException, InvalidDataException, BusinessRuleException{
    List<Timereport> timereports = timereportIds.stream().map(timereportRepository::findById)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
    checkAuthorization(timereports, authorizedUser);
    timereports.stream()
        .map(Timereport::getId)
        .forEach(timereportDAO::deleteTimereportById);
  }

  public void releaseTimereport(long timereportId, String releasedBy) {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidation.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_COMMITED);
    timereport.setReleasedby(releasedBy);
    timereport.setReleased(DateUtils.now());
    timereportDAO.save(timereport);
  }

  public void acceptTimereport(long timereportId, String acceptedBy) {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidation.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_CLOSED);
    timereport.setAcceptedby(acceptedBy);
    timereport.setAccepted(DateUtils.now());
    timereportDAO.save(timereport);
  }

  public void reopenTimereport(long timereportId) {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidation.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereport.setReleasedby(null);
    timereport.setReleased(null);
    timereport.setAcceptedby(null);
    timereport.setAccepted(null);
    timereportDAO.save(timereport);
  }

  private void checkAndSaveTimereports(AuthorizedUser authorizedUser, List<Timereport> timereports) {
    timereports.forEach(t -> log.debug("checking Timereport {}", t.getTimeReportAsString()));

    checkAuthorization(timereports, authorizedUser);
    validateTimeReportingBusinessRules(timereports);
    validateContractBusinessRules(timereports);
    validateOrderBusinessRules(timereports);
    validateEmployeeorderBudget(timereports);

    timereports.forEach(t -> {
      log.debug("Saving Timereport {}", t.getTimeReportAsString());
      timereportDAO.save(t);
    });

    // recompute overtimeStatic and store it in employeecontract if change made before release date
    LocalDate reportReleaseDate = timereports.get(0).getEmployeecontract().getReportReleaseDate();
    Optional<LocalDate> match = timereports.stream()
        .map(Timereport::getReferenceday)
        .map(Referenceday::getRefdate)
        .filter(d -> !d.isAfter(reportReleaseDate))
        .findAny();
    if(match.isPresent()) {
      Employeecontract employeecontract = timereports.get(0).getEmployeecontract();
      var overtimeStaticNew = overtimeService.calculateOvertime(
          employeecontract.getId(),
          employeecontract.getValidFrom(),
          reportReleaseDate
      );
      overtimeStaticNew.ifPresent(overtimeStaticNewValue -> {
        log.info(
            "Overtime for employeecontract {} changed from {} to {}",
            employeecontract.getId(),
            employeecontract.getOvertimeStatic(),
            overtimeStaticNewValue
        );
        employeecontract.setOvertimeStatic(overtimeStaticNewValue);
        employeecontractDAO.save(employeecontract);
      });
    }
  }

  private void validateParametersAndFillTimereport(long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, long durationHours, long durationMinutes,
      Timereport timereport) {
    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);
    DataValidation.notNull(employeecontract, TR_EMPLOYEE_CONTRACT_NOT_FOUND);
    Employeeorder employeeorder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
    DataValidation.notNull(employeeorder, TR_EMPLOYEE_ORDER_NOT_FOUND);
    DataValidation.notNull(referenceDay, TR_REFERENCE_DAY_NULL);
    Referenceday referenceday = referencedayDAO.getOrAddReferenceday(referenceDay);
    DataValidation.lengthIsInRange(taskDescription, 0, COMMENT_MAX_LENGTH, TR_TASK_DESCRIPTION_INVALID_LENGTH);
    DataValidation.isTrue(durationHours >= 0, TR_DURATION_HOURS_INVALID);
    DataValidation.isTrue(durationMinutes >= 0, TR_DURATION_MINUTES_INVALID);

    timereport.setEmployeecontract(employeecontract);
    timereport.setEmployeeorder(employeeorder);
    timereport.setSuborder(employeeorder.getSuborder());
    timereport.setReferenceday(referenceday);
    timereport.setTaskdescription(taskDescription.trim());
    timereport.setTraining(trainingFlag);
    timereport.setDurationhours((int) durationHours);
    timereport.setDurationminutes((int) durationMinutes);
  }

  private void setSequencenumber(Timereport timereport) {
    BusinessRuleChecks.isTrue(timereport.getSequencenumber() == 0, TR_SEQUENCE_NUMBER_ALREADY_SET);
    List<TimereportDTO> existingTimereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(
        timereport.getEmployeecontract().getId(),
        timereport.getReferenceday().getRefdate()
    );
    int maxSequencenumber = existingTimereports
        .stream()
        .map(TimereportDTO::getSequencenumber)
        .max(Integer::compareTo)
        .orElse(0);
    timereport.setSequencenumber(maxSequencenumber + 1);
  }

  private void setStatus(Timereport timereport) {
    LocalDate acceptanceDate = timereport.getEmployeecontract().getReportAcceptanceDate();
    LocalDate releaseDate = timereport.getEmployeecontract().getReportReleaseDate();

    if(acceptanceDate != null && !acceptanceDate.isBefore(timereport.getReferenceday().getRefdate())) {
      // timereports created within the period of accepted reports will automatically get the closed status
      timereport.setStatus(TIMEREPORT_STATUS_CLOSED);
    } else if(releaseDate != null && !releaseDate.isBefore(timereport.getReferenceday().getRefdate())) {
      // timereports created within the period of released reports will automatically get the committed status
      timereport.setStatus(TIMEREPORT_STATUS_COMMITED);
    } else {
      timereport.setStatus(TIMEREPORT_STATUS_OPEN);
    }
  }

  private Referenceday getNextWorkableDay(Referenceday referenceday) {
    Referenceday nextWorkableDay = null;
    LocalDate day = referenceday.getRefdate();
    do {
      LocalDate nextDay = DateUtils.addDays(day, 1);
      if(DateUtils.isWeekday(nextDay)) {
        Optional<Publicholiday> publicHoliday = publicholidayDAO.getPublicHoliday(nextDay);
        if(!publicHoliday.isPresent()) {
          // we have found a weekday that is not a public holiday, hooray!
          nextWorkableDay = referencedayDAO.getOrAddReferenceday(nextDay);
        }
      }
      day = nextDay; // prepare next iteration
    } while(nextWorkableDay == null);
    return nextWorkableDay;
  }

  private void checkAuthorization(List<Timereport> timereports, AuthorizedUser authorizedUser) throws AuthorizationException {
    // authorization is based on the status
    timereports.forEach(t -> {
      if(TIMEREPORT_STATUS_CLOSED.equals(t.getStatus()) &&
          !authorizedUser.isAdmin()) {
        throw new AuthorizationException(TR_CLOSED_TIME_REPORT_REQ_ADMIN);
      }
      if(TIMEREPORT_STATUS_COMMITED.equals(t.getStatus()) &&
          !authorizedUser.isManager() &&
          !authorizedUser.isAdmin()) {
        throw new AuthorizationException(TR_COMMITTED_TIME_REPORT_REQ_MANAGER);
      }
      if(TIMEREPORT_STATUS_OPEN.equals(t.getStatus()) &&
          !authorizedUser.isAdmin() &&
          !Objects.equals(authorizedUser.getEmployeeId(), t.getEmployeecontract().getEmployee().getId())) {
        throw new AuthorizationException(TR_OPEN_TIME_REPORT_REQ_EMPLOYEE);
      }
    });
  }

  private void validateEmployeeorderBudget(List<Timereport> timereports) throws BusinessRuleException {
    // one timereport exists at least and all share the same suborder & employeeorder
    Employeeorder employeeorder = timereports.get(0).getEmployeeorder();
    if(employeeorder.getDebithoursunit() == null) {
      return; // no budget defined!
    }
    long debitMinutesTemp = employeeorder.getDebithours().toMinutes();
    // increase debit minutes if timereport exists (update case) by the time of that timereport
    // because this time is read from the database query, too. This is a trick to circumvent this special case.
    if(timereports.size() == 1 && !timereports.get(0).isNew()) {
      Timereport timereport = timereports.get(0);
      debitMinutesTemp += timereport.getDurationhours() * MINUTES_PER_HOUR + timereport.getDurationminutes();
    }
    final long debitMinutes = debitMinutesTemp;
    switch(employeeorder.getDebithoursunit()) {
      case DEBITHOURS_UNIT_MONTH:
        Map<YearMonth, Integer> minutesPerYearMonth = timereports.stream()
            .collect(groupingBy(t -> getYearMonth(t.getReferenceday().getRefdate()),
                summingInt(t -> t.getDurationhours() * MINUTES_PER_HOUR + t.getDurationminutes())));
        minutesPerYearMonth.forEach((yearMonth, minutesSum) -> {
          long alreadyReportedMinutes = timereportDAO.getTotalDurationMinutesForEmployeeOrder(
              employeeorder.getId(),
              getFirstDay(yearMonth),
              getLastDay(yearMonth)
          );
          BusinessRuleChecks.isTrue(alreadyReportedMinutes + minutesSum <= debitMinutes,
              TR_MONTH_BUDGET_EXCEEDED);
        });
        break;
      case DEBITHOURS_UNIT_YEAR:
        Map<Year, Integer> minutesPerYear = timereports.stream()
            .collect(groupingBy(t -> getYear(t.getReferenceday().getRefdate()),
                summingInt(t -> t.getDurationhours() * MINUTES_PER_HOUR + t.getDurationminutes())));
        minutesPerYear.forEach((year, minutesSum) -> {
          long alreadyReportedMinutes = timereportDAO.getTotalDurationMinutesForEmployeeOrder(
              employeeorder.getId(),
              getFirstDay(year),
              getLastDay(year)
          );
          BusinessRuleChecks.isTrue(alreadyReportedMinutes + minutesSum <= debitMinutes,
              TR_YEAR_BUDGET_EXCEEDED);
        });
        break;
      case DEBITHOURS_UNIT_TOTALTIME:
        int minutesSum = timereports.stream()
            .map(t -> t.getDurationhours() * MINUTES_PER_HOUR + t.getDurationminutes())
            .mapToInt(Integer::intValue)
            .sum();
        long alreadyReportedMinutes = timereportDAO
            .getTotalDurationMinutesForEmployeeOrder(employeeorder.getId());
        BusinessRuleChecks.isTrue(alreadyReportedMinutes + minutesSum <= debitMinutes,
            TR_TOTAL_BUDGET_EXCEEDED);
        break;
      default:
        throw new IllegalStateException(
            "employeeorder has unsupported debit hours unit: "
            + employeeorder.getDebithoursunit()
        );
    }
  }

  private void validateOrderBusinessRules(List<Timereport> timereports) throws BusinessRuleException {
    // one timereport exists at least and all share the same data
    Timereport timereport = timereports.get(0);
    LocalDate refdate = timereport.getReferenceday().getRefdate();
    Suborder suborder = timereport.getSuborder();
    Employeeorder employeeorder = timereport.getEmployeeorder();
    if(TRUE.equals(suborder.getCommentnecessary())) {
      BusinessRuleChecks.notEmpty(timereport.getTaskdescription(), TR_SUBORDER_COMMENT_MANDATORY);
    }
    BusinessRuleChecks.isTrue(
        employeeorder.isValidAt(refdate),
        TR_EMPLOYEE_ORDER_INVALID_REF_DATE
    );
  }

  private void validateContractBusinessRules(List<Timereport> timereports) throws BusinessRuleException {
    // one timereport exists at least and all share the same data
    Timereport timereport = timereports.get(0);
    LocalDate refdate = timereport.getReferenceday().getRefdate();
    BusinessRuleChecks.isTrue(timereport.getEmployeecontract().isValidAt(refdate),
        TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE);
  }

  private void validateTimeReportingBusinessRules(List<Timereport> timereports) {
    timereports.forEach(timereport -> {
      Year reportedYear = getYear(timereport.getReferenceday().getRefdate());
      // check date range (must be in current, previous or next year)
      BusinessRuleChecks.isTrue(Math.abs(DateUtils.getCurrentYear() - reportedYear.getValue()) <= 1,
          TR_YEAR_OUT_OF_RANGE);

      Integer durationHours = timereport.getDurationhours();
      Integer durationMinutes = timereport.getDurationminutes();
      if(timereport.getSuborder().getSign().equals(SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
        DataValidation.isTrue(durationHours == 0 && durationMinutes == 0, TR_DURATION_OVERTIME_COMPENSATION_INVALID);
      } else {
        DataValidation.isTrue(durationHours > 0 || durationMinutes > 0, TR_DURATION_INVALID);
      }
    });
  }

}
