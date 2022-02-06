package org.tb.service;

import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static org.tb.GlobalConstants.DEBITHOURS_UNIT_MONTH;
import static org.tb.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;
import static org.tb.GlobalConstants.DEBITHOURS_UNIT_YEAR;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@Setter(onMethod_ = { @Autowired })
public class TimereportService {

  private EmployeecontractDAO employeecontractDAO;
  private ReferencedayDAO referencedayDAO;
  private EmployeeorderDAO employeeorderDAO;
  private TimereportDAO timereportDAO;
  private PublicholidayDAO publicholidayDAO;

  @Transactional
  public void createTimereports(AuthorizedUser authorizedUser, long employeeContractId, long employeeOrderId, Date referenceDay, String taskDescription,
      boolean trainingFlag, int durationHours, int durationMinutes, String sortOfReport, double costs, int numberOfSerialDays)
  throws AuthorizationException, InvalidDataException, LogicException {

    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);
    DataValidation.notNull(employeecontract, "employeeContractById must match an employee contract");
    Employeeorder employeeorder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
    DataValidation.notNull(employeeorder, "employeeOrderId must match an employee order");
    DataValidation.notNull(referenceDay, "reference day must not be null");
    Referenceday referenceday = referencedayDAO.getOrAddReferenceday(referenceDay);
    DataValidation.notNull(taskDescription, "taskDescription must at least be an empty string");
    DataValidation.isTrue(durationHours >= 0, "durationHours must be 0 at minimum");
    DataValidation.isTrue(durationMinutes >= 0, "durationMinutes must be 0 at minimum");
    DataValidation.isTrue(durationHours > 0 || durationMinutes > 0, "At least one of durationHours and durationMinutes must be greater than 0");
    DataValidation.isTrue(SORT_OF_REPORT_WORK.equals(sortOfReport), "sortOfReport must be " + SORT_OF_REPORT_WORK);
    DataValidation.isTrue(costs >= 0.0, "costs must be greater than or equal to 0");
    DataValidation.isTrue(numberOfSerialDays >= 1, "numberOfSerialDays must be 1 at minimum");

    Timereport timereportTemplate = new Timereport();
    timereportTemplate.setEmployeecontract(employeecontract);
    timereportTemplate.setEmployeeorder(employeeorder);
    timereportTemplate.setSuborder(employeeorder.getSuborder());
    timereportTemplate.setReferenceday(referenceday);
    timereportTemplate.setTaskdescription(taskDescription.trim());
    timereportTemplate.setTraining(trainingFlag);
    timereportTemplate.setDurationhours(durationHours);
    timereportTemplate.setDurationminutes(durationMinutes);
    timereportTemplate.setSortofreport(sortOfReport);
    timereportTemplate.setCosts(costs);

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

    timereportsToSave.forEach(t -> log.info("checking Timereport {}", t.getTimeReportAsString()));

    checkAuthorization(timereportsToSave, authorizedUser);
    validateContract(timereportsToSave);
    validateOrder(timereportsToSave);
    validateEmployeeorderBudget(timereportsToSave);

    timereportsToSave.forEach(t -> log.info("Saving Timereport {}", t.getTimeReportAsString()));
  }

  @Transactional
  public void updateTimereport(AuthorizedUser authorizedUser, long timereportId, long employeeContractId, long employeeOrderId, Date referenceDay, String taskDescription,
      boolean trainingFlag, int durationHours, int durationMinutes, String sortOfReport, double costs) {
    Timereport timereport = timereportDAO.getTimereportById(timereportId);
    DataValidation.notNull(timereport, "timereportId must match a timereport");
    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);
    DataValidation.notNull(employeecontract, "employeeContractById must match an employee contract");
    Employeeorder employeeorder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
    DataValidation.notNull(employeeorder, "employeeOrderId must match an employee order");
    DataValidation.notNull(referenceDay, "reference day must not be null");
    Referenceday referenceday = referencedayDAO.getOrAddReferenceday(referenceDay);
    DataValidation.notNull(taskDescription, "taskDescription must at least be an empty string");
    DataValidation.isTrue(durationHours >= 0, "durationHours must be 0 at minimum");
    DataValidation.isTrue(durationMinutes >= 0, "durationMinutes must be 0 at minimum");
    DataValidation.isTrue(durationHours > 0 || durationMinutes > 0, "At least one of durationHours and durationMinutes must be greater than 0");
    DataValidation.isTrue(SORT_OF_REPORT_WORK.equals(sortOfReport), "sortOfReport must be " + SORT_OF_REPORT_WORK);
    DataValidation.isTrue(costs >= 0.0, "costs must be greater than or equal to 0");

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

    List<Timereport> timereportsToSave = new ArrayList<>();
    timereportsToSave.add(timereport);
    timereportsToSave.forEach(t -> log.info("checking Timereport {}", t.getTimeReportAsString()));

    checkAuthorization(timereportsToSave, authorizedUser);
    validateContract(timereportsToSave);
    validateOrder(timereportsToSave);
    validateEmployeeorderBudget(timereportsToSave);

    timereportsToSave.forEach(t -> log.info("Updating Timereport {}", t.getTimeReportAsString()));
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

  private void checkAuthorization(List<Timereport> timereportsToSave, AuthorizedUser authorizedUser) throws AuthorizationException {
    // authorization is based on the status
    timereportsToSave.forEach(t -> {
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

  // TODO validation of budgets of timereports that are edited differs as the already booked time must be substracted before
  private void validateEmployeeorderBudget(List<Timereport> timereportsToSave) throws LogicException {
    // one timereport exists at least and all share the same suborder & employeeorder
    Employeeorder employeeorder = timereportsToSave.get(0).getEmployeeorder();
    if(employeeorder.getDebithoursunit() == null) {
      return; // no budget defined!
    }
    long debitMinutesTemp = (long) (employeeorder.getDebithours() * MINUTES_PER_HOUR);
    // increase debit minutes if timereport exists (update case) by the time of that timereport
    // because this time is read from the database query, too. This is a trick to circumvent this special case.
    if(timereportsToSave.size() == 1 && timereportsToSave.get(0).getId() > 0) {
      Timereport timereport = timereportsToSave.get(0);
      debitMinutesTemp += timereport.getDurationhours() * MINUTES_PER_HOUR + timereport.getDurationminutes();
    }
    final long debitMinutes = debitMinutesTemp;
    switch(employeeorder.getDebithoursunit()) {
      case DEBITHOURS_UNIT_MONTH:
        Map<YearMonth, Integer> minutesPerYearMonth = timereportsToSave.stream()
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
        Map<Year, Integer> minutesPerYear = timereportsToSave.stream()
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
        int minutesSum = timereportsToSave.stream()
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

  private void validateOrder(List<Timereport> timereportsToSave) throws LogicException {
    // one timereport exists at least and all share the same data
    Timereport timereport = timereportsToSave.get(0);
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

  private void validateContract(List<Timereport> timereportsToSave) throws LogicException {
    // one timereport exists at least and all share the same data
    Timereport timereport = timereportsToSave.get(0);
    Date refdate = timereport.getReferenceday().getRefdate();
    BusinessRuleChecks.isTrue(timereport.getEmployeecontract().isValidAt(refdate),
        "employee contract must be valid for the reference day of the time report");
  }

}
