package org.tb.service;

import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static org.tb.GlobalConstants.COMMENT_MAX_LENGTH;
import static org.tb.GlobalConstants.DEBITHOURS_UNIT_MONTH;
import static org.tb.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;
import static org.tb.GlobalConstants.DEBITHOURS_UNIT_YEAR;
import static org.tb.GlobalConstants.MAX_COSTS;
import static org.tb.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.GlobalConstants.SORT_OF_REPORT_WORK;
import static org.tb.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.util.DateUtils.getFirstDay;
import static org.tb.util.DateUtils.getLastDay;
import static org.tb.util.DateUtils.getYear;
import static org.tb.util.DateUtils.getYearMonth;

import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Referenceday;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.exception.AuthorizationException;
import org.tb.exception.InvalidDataException;
import org.tb.exception.LogicException;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
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

  public void createTimereports(AuthorizedUser authorizedUser, long employeeContractId, long employeeOrderId, Date referenceDay, String taskDescription,
      boolean trainingFlag, int durationHours, int durationMinutes, String sortOfReport, double costs, int numberOfSerialDays)
  throws AuthorizationException, InvalidDataException, LogicException {

    Timereport timereportTemplate = new Timereport();
    validateParametersAndFillTimereport(employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, durationHours,
        durationMinutes, sortOfReport, costs, timereportTemplate);

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

  public void updateTimereport(AuthorizedUser authorizedUser, long timereportId, long employeeContractId, long employeeOrderId, Date referenceDay, String taskDescription,
      boolean trainingFlag, int durationHours, int durationMinutes, String sortOfReport, double costs) {
    Timereport timereport = timereportDAO.getTimereportById(timereportId);
    DataValidation.notNull(timereport, "timereportId must match a timereport");
    validateParametersAndFillTimereport(employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, durationHours,
        durationMinutes, sortOfReport, costs, timereport);
    checkAndSaveTimereports(authorizedUser, Collections.singletonList(timereport));
  }

  private void checkAndSaveTimereports(AuthorizedUser authorizedUser, List<Timereport> timereports) {
    timereports.forEach(t -> log.info("checking Timereport {}", t.getTimeReportAsString()));

    checkAuthorization(timereports, authorizedUser);
    validateTimeReportingBusinessRules(timereports);
    validateContractBusinessRules(timereports);
    validateOrderBusinessRules(timereports);
    validateEmployeeorderBudget(timereports);

    timereports.forEach(t -> log.info("Saving Timereport {}", t.getTimeReportAsString()));
    // FIXME implement save after old code removed from salat
  }

  private void validateParametersAndFillTimereport(long employeeContractId, long employeeOrderId, Date referenceDay, String taskDescription,
      boolean trainingFlag, int durationHours, int durationMinutes, String sortOfReport, double costs,
      Timereport timereport) {
    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);
    DataValidation.notNull(employeecontract, "employeeContractById must match an employee contract");
    Employeeorder employeeorder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
    DataValidation.notNull(employeeorder, "employeeOrderId must match an employee order");
    DataValidation.notNull(referenceDay, "reference day must not be null");
    Referenceday referenceday = referencedayDAO.getOrAddReferenceday(referenceDay);
    DataValidation.lengthIsInRange(taskDescription, 0, COMMENT_MAX_LENGTH, "taskDescription out of valid length range");
    DataValidation.isTrue(durationHours >= 0, "durationHours must be 0 at minimum");
    DataValidation.isTrue(durationMinutes >= 0, "durationMinutes must be 0 at minimum");
    DataValidation.isTrue(durationHours > 0 || durationMinutes > 0, "At least one of durationHours and durationMinutes must be greater than 0");
    DataValidation.isTrue(SORT_OF_REPORT_WORK.equals(sortOfReport), "sortOfReport must be " + SORT_OF_REPORT_WORK);
    DataValidation.isInRange(costs, 0.0, MAX_COSTS, "costs out of valid range");

    timereport.setEmployeecontract(employeecontract);
    timereport.setEmployeeorder(employeeorder);
    timereport.setSuborder(employeeorder.getSuborder());
    timereport.setReferenceday(referenceday);
    timereport.setTaskdescription(taskDescription.trim());
    timereport.setTraining(trainingFlag);
    timereport.setDurationhours(durationHours);
    timereport.setDurationminutes(durationMinutes);
    timereport.setSortofreport(sortOfReport);
    timereport.setCosts(costs);
  }

  /**
   * shifts a timereport by days
   */
  public void shiftDays(long timereportId, int amountDays, AuthorizedUser authorizedUser) {
    Timereport timereport = timereportDAO.getTimereportById(timereportId);
    Referenceday referenceday = timereport.getReferenceday();
    Date shiftedDate = DateUtils.addDays(referenceday.getRefdate(), amountDays);
    updateTimereport(authorizedUser,
        timereport.getId(),
        timereport.getEmployeecontract().getId(),
        timereport.getEmployeeorder().getId(),
        shiftedDate,
        timereport.getTaskdescription(),
        TRUE.equals(timereport.getTraining()),
        timereport.getDurationhours(),
        timereport.getDurationminutes(),
        timereport.getSortofreport(),
        timereport.getCosts());
  }

  /**
   * deletes many timereports at once
   *
   * @param timereportIds ids of the timereports
   */
  public void deleteTimereports(List<Long> timereportIds, AuthorizedUser authorizedUser) {
    List<Timereport> timereports = timereportIds.stream().map(timereportDAO::getTimereportById)
        .collect(Collectors.toList());
    checkAuthorization(timereports, authorizedUser);
    timereports.stream()
        .map(Timereport::getId)
        .forEach(timereportDAO::deleteTimereportById);
  }

  private void setSequencenumber(Timereport timereport) {
    BusinessRuleChecks.isTrue(timereport.getSequencenumber() == 0, "sequencenumber already set on timereport");
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
    Date acceptanceDate = timereport.getEmployeecontract().getReportAcceptanceDate();
    Date releaseDate = timereport.getEmployeecontract().getReportReleaseDate();

    if(acceptanceDate != null && !acceptanceDate.before(timereport.getReferenceday().getRefdate())) {
      // timereports created within the period of accepted reports will automatically get the closed status
      timereport.setStatus(TIMEREPORT_STATUS_CLOSED);
    } else if(releaseDate != null && !releaseDate.before(timereport.getReferenceday().getRefdate())) {
      // timereports created within the period of released reports will automatically get the committed status
      timereport.setStatus(TIMEREPORT_STATUS_COMMITED);
    } else {
      timereport.setStatus(TIMEREPORT_STATUS_OPEN);
    }
  }

  private Referenceday getNextWorkableDay(Referenceday referenceday) {
    Referenceday nextWorkableDay = null;
    Date day = referenceday.getRefdate();
    do {
      Date nextDay = DateUtils.addDays(day, 1);
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
        throw new AuthorizationException("closed time reports can only be saved by admins.");
      }
      if(TIMEREPORT_STATUS_COMMITED.equals(t.getStatus()) &&
          !authorizedUser.isManager() &&
          !authorizedUser.isAdmin()) {
        throw new AuthorizationException("committed time reports can only be saved by admins and managers.");
      }
      if(TIMEREPORT_STATUS_OPEN.equals(t.getStatus()) &&
              authorizedUser.getEmployeeId() != t.getEmployeecontract().getEmployee().getId()) {
        throw new AuthorizationException("open time reports can only be saved by the employee herself.");
      }
    });
  }

  private void validateEmployeeorderBudget(List<Timereport> timereports) throws LogicException {
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
              "debit minutes of employee order exceeded for month " + yearMonth);
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
              "debit minutes of employee order exceeded for year " + year);
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
            "debit minutes of employee order exceeded (total)");
        break;
      default:
        throw new IllegalStateException(
            "employeeorder has unsupported debit hours unit: "
            + employeeorder.getDebithoursunit()
        );
    }
  }

  private void validateOrderBusinessRules(List<Timereport> timereports) throws LogicException {
    // one timereport exists at least and all share the same data
    Timereport timereport = timereports.get(0);
    Date refdate = timereport.getReferenceday().getRefdate();
    Suborder suborder = timereport.getSuborder();
    Employeeorder employeeorder = timereport.getEmployeeorder();
    if(TRUE.equals(suborder.getCommentnecessary())) {
      BusinessRuleChecks.notEmpty(timereport.getTaskdescription(), "taskDescription must not be empty to meet the requirements of the related suborder");
    }
    BusinessRuleChecks.isTrue(
        employeeorder.isValidAt(refdate),
        "referenceday must fit to the employee order's date validity - check also suborder and customer order"
    );
  }

  private void validateContractBusinessRules(List<Timereport> timereports) throws LogicException {
    // one timereport exists at least and all share the same data
    Timereport timereport = timereports.get(0);
    Date refdate = timereport.getReferenceday().getRefdate();
    BusinessRuleChecks.isTrue(timereport.getEmployeecontract().isValidAt(refdate),
        "employee contract must be valid for the reference day of the time report");
  }

  private void validateTimeReportingBusinessRules(List<Timereport> timereports) {
    timereports.forEach(timereport -> {
      Year reportedYear = getYear(timereport.getReferenceday().getRefdate());
      // check date range (must be in current, previous or next year)
      BusinessRuleChecks.isTrue(Math.abs(DateUtils.getCurrentYear() - reportedYear.getValue()) <= 1,
          "Time reports must be modified only in the current, the previous or the next year");
    });
  }

}
