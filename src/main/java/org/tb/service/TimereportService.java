package org.tb.service;

import static java.lang.Boolean.TRUE;
import static java.util.Calendar.SATURDAY;
import static java.util.Calendar.SUNDAY;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static org.tb.ErrorCode.TR_CLOSED_TIME_REPORT_REQ_ADMIN;
import static org.tb.ErrorCode.TR_COMMITTED_TIME_REPORT_REQ_MANAGER;
import static org.tb.ErrorCode.TR_DURATION_HOURS_INVALID;
import static org.tb.ErrorCode.TR_DURATION_INVALID;
import static org.tb.ErrorCode.TR_DURATION_MINUTES_INVALID;
import static org.tb.ErrorCode.TR_DURATION_OVERTIME_COMPENSATION_INVALID;
import static org.tb.ErrorCode.TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE;
import static org.tb.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.ErrorCode.TR_EMPLOYEE_ORDER_INVALID_REF_DATE;
import static org.tb.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;
import static org.tb.ErrorCode.TR_MONTH_BUDGET_EXCEEDED;
import static org.tb.ErrorCode.TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;
import static org.tb.ErrorCode.TR_REFERENCE_DAY_NULL;
import static org.tb.ErrorCode.TR_SEQUENCE_NUMBER_ALREADY_SET;
import static org.tb.ErrorCode.TR_SUBORDER_COMMENT_MANDATORY;
import static org.tb.ErrorCode.TR_TASK_DESCRIPTION_INVALID_LENGTH;
import static org.tb.ErrorCode.TR_TIME_REPORT_NOT_FOUND;
import static org.tb.ErrorCode.TR_TOTAL_BUDGET_EXCEEDED;
import static org.tb.ErrorCode.TR_YEAR_BUDGET_EXCEEDED;
import static org.tb.ErrorCode.TR_YEAR_OUT_OF_RANGE;
import static org.tb.GlobalConstants.COMMENT_MAX_LENGTH;
import static org.tb.GlobalConstants.DEBITHOURS_UNIT_MONTH;
import static org.tb.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;
import static org.tb.GlobalConstants.DEBITHOURS_UNIT_YEAR;
import static org.tb.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION;
import static org.tb.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.util.DateUtils.getFirstDay;
import static org.tb.util.DateUtils.getLastDay;
import static org.tb.util.DateUtils.getYear;
import static org.tb.util.DateUtils.getYearMonth;
import static org.tb.util.DateUtils.max;
import static org.tb.util.DateUtils.min;

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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tb.bdom.AuthorizedUser;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Overtime;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Referenceday;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.exception.AuthorizationException;
import org.tb.exception.BusinessRuleException;
import org.tb.exception.InvalidDataException;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.ReferencedayDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.DateUtils;

@Slf4j
@Component
@Transactional
@Setter(onMethod_ = { @Autowired })
public class TimereportService {

  private EmployeecontractDAO employeecontractDAO;
  private ReferencedayDAO referencedayDAO;
  private EmployeeorderDAO employeeorderDAO;
  private TimereportDAO timereportDAO;
  private PublicholidayDAO publicholidayDAO;
  private OvertimeDAO overtimeDAO;

  public void createTimereports(AuthorizedUser authorizedUser, long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, int durationHours, int durationMinutes, int numberOfSerialDays)
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
      boolean trainingFlag, int durationHours, int durationMinutes)
      throws AuthorizationException, InvalidDataException, BusinessRuleException {
    Timereport timereport = timereportDAO.getTimereportById(timereportId);
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
    Timereport timereport = timereportDAO.getTimereportById(timereportId);
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

  /**
   * deletes many timereports at once
   *
   * @param timereportIds ids of the timereports
   */
  public void deleteTimereports(List<Long> timereportIds, AuthorizedUser authorizedUser)
      throws AuthorizationException, InvalidDataException, BusinessRuleException{
    List<Timereport> timereports = timereportIds.stream().map(timereportDAO::getTimereportById)
        .collect(Collectors.toList());
    checkAuthorization(timereports, authorizedUser);
    timereports.stream()
        .map(Timereport::getId)
        .forEach(timereportDAO::deleteTimereportById);
  }

  public long calculateOvertimeMinutes(LocalDate start, LocalDate end, long employeecontractId, boolean useOverTimeAdjustment) {

    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(employeecontractId);

    // do not consider invalid(outside of the validity of the contract) days
    LocalDate effectiveStart = max(start, employeecontract.getValidFrom());
    LocalDate effectiveEnd = min(end, employeecontract.getValidUntil());

    if(effectiveStart.isAfter(effectiveEnd)) {
      log.warn("Cannot calculate overtime when start is after end");
      return 0;
    }

    // count work days between effective days
    long effectivePublicHolidayCount = publicholidayDAO.getPublicHolidaysBetween(effectiveStart, effectiveEnd)
        .stream()
        .map(Publicholiday::getRefdate)
        .map(DateUtils::getDayOfWeek)
        .filter(dow -> dow != SATURDAY && dow != SUNDAY)
        .count();
    long weekdays = DateUtils.getWeekdaysDistance(effectiveStart, effectiveEnd);
    long effectiveWorkDays = weekdays - effectivePublicHolidayCount;

    // calculate working time
    long dailyWorkingTimeInMinutes = (long)(employeecontract.getDailyWorkingTime() * MINUTES_PER_HOUR);
    long expectedWorkingTimeInMinutes = dailyWorkingTimeInMinutes * effectiveWorkDays;
    long actualWorkingTimeInMinutes = timereportDAO.getTotalDurationMinutesForEmployeecontract(employeecontractId, effectiveStart, effectiveEnd);
    if (useOverTimeAdjustment && start.equals(employeecontract.getValidFrom())) {
      double overtime = overtimeDAO.getOvertimesByEmployeeContractId(employeecontractId)
          .stream()
          //.filter(o -> o.getRefDate()) TODO introduce this date to allow to provide a date when the adjustment is effective
          .map(Overtime::getTime)
          .mapToDouble(Double::doubleValue)
          .sum();
      long overtimeInMinutes = (long) (overtime * MINUTES_PER_HOUR);
      actualWorkingTimeInMinutes += overtimeInMinutes;
    }

    return actualWorkingTimeInMinutes - expectedWorkingTimeInMinutes;
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
      timereportDAO.saveOrUpdate(t);
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
      long overtimeMinutes = calculateOvertimeMinutes(employeecontract.getValidFrom(),
          reportReleaseDate,
          employeecontract.getId(),
          true);
      double overtimeStaticNew = (double) overtimeMinutes / MINUTES_PER_HOUR;
      log.info("Overtime for employeecontract {} changed from {} to {}",
          employeecontract.getId(),
          employeecontract.getOvertimeStatic(),
          overtimeStaticNew);
      employeecontract.setOvertimeStatic(overtimeStaticNew); // TODO refactor to hour + minutes
    }
  }

  private void validateParametersAndFillTimereport(long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, int durationHours, int durationMinutes,
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
    timereport.setDurationhours(durationHours);
    timereport.setDurationminutes(durationMinutes);
  }

  private void setSequencenumber(Timereport timereport) {
    BusinessRuleChecks.isTrue(timereport.getSequencenumber() == 0, TR_SEQUENCE_NUMBER_ALREADY_SET);
    List<Timereport> existingTimereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(
        timereport.getEmployeecontract().getId(),
        timereport.getReferenceday().getRefdate()
    );
    int maxSequencenumber = existingTimereports
        .stream()
        .map(Timereport::getSequencenumber)
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
    long debitMinutesTemp = (long) (employeeorder.getDebithours() * MINUTES_PER_HOUR);
    // increase debit minutes if timereport exists (update case) by the time of that timereport
    // because this time is read from the database query, too. This is a trick to circumvent this special case.
    if(timereports.size() == 1 && timereports.get(0).getId() > 0) {
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
