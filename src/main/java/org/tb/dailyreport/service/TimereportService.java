package org.tb.dailyreport.service;

import static java.lang.Boolean.TRUE;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toMap;
import static org.tb.common.GlobalConstants.BREAK_MINUTES_AFTER_NINE_HOURS;
import static org.tb.common.GlobalConstants.BREAK_MINUTES_AFTER_SIX_HOURS;
import static org.tb.common.GlobalConstants.COMMENT_MAX_LENGTH;
import static org.tb.common.GlobalConstants.DEBITHOURS_UNIT_MONTH;
import static org.tb.common.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;
import static org.tb.common.GlobalConstants.DEBITHOURS_UNIT_YEAR;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.common.GlobalConstants.NINE_HOURS_IN_MINUTES;
import static org.tb.common.GlobalConstants.SIX_HOURS_IN_MINUTES;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.exception.ServiceFeedbackMessage.error;
import static org.tb.common.exception.ErrorCode.TR_CLOSED_TIME_REPORT_REQ_ADMIN;
import static org.tb.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_NOT_SELF;
import static org.tb.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_REQ_MANAGER;
import static org.tb.common.exception.ErrorCode.TR_DURATION_HOURS_INVALID;
import static org.tb.common.exception.ErrorCode.TR_DURATION_INVALID;
import static org.tb.common.exception.ErrorCode.TR_DURATION_MINUTES_INVALID;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_INVALID_REF_DATE;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.TR_MONTH_BUDGET_EXCEEDED;
import static org.tb.common.exception.ErrorCode.TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;
import static org.tb.common.exception.ErrorCode.TR_REFERENCE_DAY_NULL;
import static org.tb.common.exception.ErrorCode.TR_SEQUENCE_NUMBER_ALREADY_SET;
import static org.tb.common.exception.ErrorCode.TR_SUBORDER_COMMENT_MANDATORY;
import static org.tb.common.exception.ErrorCode.TR_TASK_DESCRIPTION_INVALID_LENGTH;
import static org.tb.common.exception.ErrorCode.TR_TIMEREPORTS_EXIST_CANNOT_DELETE_OR_UPDATE_EMPLOYEE_ORDER;
import static org.tb.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.TR_TOTAL_BUDGET_EXCEEDED;
import static org.tb.common.exception.ErrorCode.TR_WORKING_DAY_NOT_WORKED;
import static org.tb.common.exception.ErrorCode.TR_WORKING_DAY_START_NULL;
import static org.tb.common.exception.ErrorCode.TR_YEAR_BUDGET_EXCEEDED;
import static org.tb.common.exception.ErrorCode.TR_YEAR_OUT_OF_RANGE;
import static org.tb.common.exception.ErrorCode.WD_BEGIN_TIME_MISSING;
import static org.tb.common.exception.ErrorCode.WD_BREAK_TOO_SHORT_6;
import static org.tb.common.exception.ErrorCode.WD_BREAK_TOO_SHORT_9;
import static org.tb.common.util.DateUtils.getFirstDay;
import static org.tb.common.util.DateUtils.getLastDay;
import static org.tb.common.util.DateUtils.getYear;
import static org.tb.common.util.DateUtils.getYearMonth;
import static org.tb.common.util.UrlUtils.absoluteUrl;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;

import jakarta.servlet.ServletContext;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.util.MessageResources;
import org.springframework.context.event.EventListener;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.AuthorizedUser;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.Warning;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.BusinessRuleCheckUtils;
import org.tb.common.util.DataValidationUtils;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.ReferencedayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.event.EmployeeorderDeleteEvent;
import org.tb.order.event.EmployeeorderUpdateEvent;
import org.tb.order.persistence.EmployeeorderDAO;

@Slf4j
@Service
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
  private final WorkingdayDAO workingdayDAO;
  private final EmployeecontractService employeecontractService;
  private final ServletContext servletContext;

  public TimereportDTO getTimereportById(long id) {
    return timereportDAO.getTimereportById(id);
  }

  public List<TimereportDTO> getTimereportsForEmployeecontractAndDate(long employeecontractId, LocalDate date) {
    return timereportDAO.getTimereportsByDateAndEmployeeContractId(employeecontractId, date);
  }

  public List<TimereportDTO> getTimereportsByDates(LocalDate beginDate, LocalDate endDate) {
    return timereportDAO.getTimereportsByDates(beginDate, endDate);
  }

  public List<TimereportDTO> getTimereportsByDatesAndEmployeeContractId(long employeecontractId, LocalDate beginDate,
      LocalDate endDate) {
    return timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeecontractId, beginDate, endDate);
  }

  public List<TimereportDTO> getTimereportsByDatesAndCustomerOrderId(LocalDate beginDate, LocalDate endDate,
      long orderId) {
    return timereportDAO.getTimereportsByDatesAndCustomerOrderId(beginDate, endDate, orderId);
  }

  public List<TimereportDTO> getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(long employeecontractId, LocalDate beginDate,
      LocalDate endDate, long orderId) {
    return timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(employeecontractId, beginDate, endDate, orderId);
  }

  public List<TimereportDTO> getTimereportsByDatesAndSuborderId(LocalDate beginDate, LocalDate endDate,
      long suborderId) {
    return timereportDAO.getTimereportsByDatesAndSuborderId(beginDate, endDate, suborderId);
  }

  public List<TimereportDTO> getTimereportsByDatesAndEmployeeContractIdAndSuborderId(long employeecontractId, LocalDate beginDate,
      LocalDate endDate, long suborderId) {
    return timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndSuborderId(employeecontractId, beginDate, endDate, suborderId);
  }

  public List<TimereportDTO> getTimereportsByDate(LocalDate date) {
    return timereportDAO.getTimereportsByDate(date);
  }

  public List<TimereportDTO> getTimereportsByDateAndEmployeeContractId(long employeeContractId, LocalDate date) {
    return timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContractId, date);
  }

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
    DataValidationUtils.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
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
    DataValidationUtils.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
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
    DataValidationUtils.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    checkAuthorization(Collections.singletonList(timereport), authorizedUser);
    timereportDAO.deleteTimereportById(timereportId);
    return true;
  }

  public void deleteTimeReports(LocalDate date, long employeeOrderId, AuthorizedUser authorizedUser) {
    var timeReports = timereportRepository.findAllByEmployeeorderIdAndReferencedayRefdate(employeeOrderId, date);
    checkAuthorization(timeReports, authorizedUser);
    timeReports.forEach(timeReport -> timereportDAO.deleteTimereportById(timeReport.getId()));
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

  public long getTotalDurationMinutesForSuborderAndEmployeeContract(long soId, long ecId) {
    return timereportRepository.getReportedMinutesForSuborderAndEmployeeContract(soId, ecId).orElse(0L);
  }

  /**
   * Gets the sum of all duration minutes WITH considering the hours.
   */
  public long getTotalDurationMinutesForSuborders(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return 0;
    return timereportRepository.getReportedMinutesForSuborders(ids).orElse(0L);
  }

  /**
   * Gets the sum of all duration minutes within a range of time WITH considering the hours.
   */
  public long getTotalDurationMinutesForSuborder(long soId, LocalDate fromDate, LocalDate untilDate) {
    return timereportRepository.getReportedMinutesForSuborderAndBetween(soId, fromDate, untilDate).orElse(0L);
  }

  /**
   * Gets the sum of all duration minutes WITH consideration of the hours.
   */
  public long getTotalDurationMinutesForCustomerOrder(long coId) {
    return timereportRepository.getReportedMinutesForCustomerorder(coId).orElse(0L);
  }

  /**
   * Gets the sum of all duration minutes WITH considering the hours.
   */
  public long getTotalDurationMinutesForEmployeeOrder(long eoId) {
    return timereportRepository.getReportedMinutesForEmployeeorder(eoId).orElse(0L);
  }

  /**
   * Gets a list of all {@link TimereportDTO}s that fulfill following criteria:
   * 1) associated to the given employee contract
   * 2) refdate out of range of the employee contract
   *
   * @return Returns a {@link List} with all {@link TimereportDTO}s, that fulfill the criteria.
   */
  public List<TimereportDTO> getTimereportsOutOfRangeForEmployeeContract(long employeecontractId) {
    return timereportDAO.getTimereportsOutOfRangeForEmployeeContract(employeecontractId);
  }

  /**
   * Gets a list of all {@link TimereportDTO}s that fulfill following criteria:
   * 1) associated to the given employee contract
   * 2) refdate out of range of the associated employee order
   *
   * @return Returns a {@link List} with all {@link TimereportDTO}s, that fulfill the criteria.
   */
  public List<TimereportDTO> getTimereportsOutOfRangeForEmployeeOrder(long employeecontractId) {
    return timereportDAO.getTimereportsOutOfRangeForEmployeeOrder(employeecontractId);
  }

  /**
   * Gets a list of all {@link TimereportDTO}s, that have no duration and are associated to the given ecId.
   */
  public List<TimereportDTO> getTimereportsWithoutDurationForEmployeeContractId(long employeecontractId, LocalDate releaseDate) {
    return timereportDAO.getTimereportsWithoutDurationForEmployeeContractId(employeecontractId, releaseDate);
  }

  private void checkAndSaveTimereports(AuthorizedUser authorizedUser, List<Timereport> timereports) {
    timereports.forEach(t -> log.debug("checking Timereport {}", t.getTimeReportAsString()));

    checkAuthorization(timereports, authorizedUser);
    validateWorkingDayBusinessRules(timereports);
    validateTimeReportingBusinessRules(timereports);
    validateContractBusinessRules(timereports);
    validateOrderBusinessRules(timereports);
    validateEmployeeorderBudget(timereports);

    timereports.forEach(t -> {
      log.debug("Saving Timereport {}", t.getTimeReportAsString());
      timereportDAO.save(t);
    });

    // recompute overtimeStatic and store it in employeecontract if change made before acceptance date
    LocalDate reportAcceptanceDate = timereports.getFirst().getEmployeecontract().getReportAcceptanceDate();
    if(reportAcceptanceDate != null) {
      Optional<LocalDate> match = timereports.stream()
              .map(Timereport::getReferenceday)
              .map(Referenceday::getRefdate)
              .filter(d -> !d.isAfter(reportAcceptanceDate))
              .findAny();
      if(match.isPresent()) {
        Employeecontract employeecontract = timereports.getFirst().getEmployeecontract();
        var overtimeStaticNew = overtimeService.calculateOvertime(
                employeecontract.getId(),
                employeecontract.getValidFrom(),
                reportAcceptanceDate
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
  }

  private void validateParametersAndFillTimereport(long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, long durationHours, long durationMinutes,
      Timereport timereport) {
    Employeecontract employeecontract = employeecontractDAO.getEmployeecontractById(employeeContractId);
    DataValidationUtils.notNull(employeecontract, TR_EMPLOYEE_CONTRACT_NOT_FOUND);
    Employeeorder employeeorder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
    DataValidationUtils.notNull(employeeorder, TR_EMPLOYEE_ORDER_NOT_FOUND);
    DataValidationUtils.notNull(referenceDay, TR_REFERENCE_DAY_NULL);
    Referenceday referenceday = referencedayDAO.getOrAddReferenceday(referenceDay);
    DataValidationUtils.lengthIsInRange(taskDescription, 0, COMMENT_MAX_LENGTH, TR_TASK_DESCRIPTION_INVALID_LENGTH);
    DataValidationUtils.isTrue(durationHours >= 0, TR_DURATION_HOURS_INVALID);
    DataValidationUtils.isTrue(durationMinutes >= 0, TR_DURATION_MINUTES_INVALID);

    timereport.setEmployeecontract(employeecontract);
    timereport.setEmployeeorder(employeeorder);
    timereport.setSuborder(employeeorder.getSuborder());
    timereport.setReferenceday(referenceday);
    timereport.setTaskdescription(taskDescription.trim());
    timereport.setTraining(trainingFlag);
    timereport.setDurationhours((int) durationHours);
    timereport.setDurationminutes((int) durationMinutes);
  }

  boolean needsWorkingHoursLawValidation(long employeeContractId) {
    Employeecontract contract = employeecontractDAO.getEmployeecontractById(employeeContractId);
    return contract != null && !contract.getEmployee().isRestricted();
  }

  private void setSequencenumber(Timereport timereport) {
    BusinessRuleCheckUtils.isTrue(timereport.getSequencenumber() == 0, TR_SEQUENCE_NUMBER_ALREADY_SET);
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
        if(publicHoliday.isEmpty()) {
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
      if(TIMEREPORT_STATUS_COMMITED.equals(t.getStatus()) &&
          Objects.equals(authorizedUser.getEmployeeId(), t.getEmployeecontract().getEmployee().getId())) {
        throw new AuthorizationException(TR_COMMITTED_TIME_REPORT_NOT_SELF);
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
    Employeeorder employeeorder = timereports.getFirst().getEmployeeorder();
    if(employeeorder.getDebithoursunit() == null) {
      return; // no budget defined!
    }
    final long debitMinutes = getDebitMinutes(timereports, employeeorder);
    switch(employeeorder.getDebithoursunit()) {
      case DEBITHOURS_UNIT_MONTH:
        Map<YearMonth, Long> minutesPerYearMonth = timereports.stream()
            .collect(groupingBy(t -> getYearMonth(t.getReferenceday().getRefdate()),
                summingLong(t -> t.getDurationhours() * MINUTES_PER_HOUR + t.getDurationminutes())));
        minutesPerYearMonth.forEach((yearMonth, minutesSum) -> {
          long alreadyReportedMinutes = timereportDAO.getTotalDurationMinutesForEmployeeOrder(
              employeeorder.getId(),
              getFirstDay(yearMonth),
              getLastDay(yearMonth)
          );
          BusinessRuleCheckUtils.isTrue(alreadyReportedMinutes + minutesSum <= debitMinutes,
              TR_MONTH_BUDGET_EXCEEDED);
        });
        break;
      case DEBITHOURS_UNIT_YEAR:
        Map<Year, Long> minutesPerYear = timereports.stream()
            .collect(groupingBy(t -> getYear(t.getReferenceday().getRefdate()),
                summingLong(t -> t.getDurationhours() * MINUTES_PER_HOUR + t.getDurationminutes())));
        minutesPerYear.forEach((year, minutesSum) -> {
          long alreadyReportedMinutes = timereportDAO.getTotalDurationMinutesForEmployeeOrder(
              employeeorder.getId(),
              getFirstDay(year),
              getLastDay(year)
          );
          BusinessRuleCheckUtils.isTrue(alreadyReportedMinutes + minutesSum <= debitMinutes,
              TR_YEAR_BUDGET_EXCEEDED);
        });
        break;
      case DEBITHOURS_UNIT_TOTALTIME:
        long minutesSum = timereports.stream()
            .mapToLong(t -> t.getDurationhours() * MINUTES_PER_HOUR + t.getDurationminutes())
            .sum();
        long alreadyReportedMinutes = timereportDAO
            .getTotalDurationMinutesForEmployeeOrder(employeeorder.getId());
        BusinessRuleCheckUtils.isTrue(alreadyReportedMinutes + minutesSum <= debitMinutes,
            TR_TOTAL_BUDGET_EXCEEDED);
        break;
      default:
        throw new IllegalStateException(
            "employeeorder has unsupported debit hours unit: "
            + employeeorder.getDebithoursunit()
        );
    }
  }

  private static long getDebitMinutes(List<Timereport> timereports, Employeeorder employeeorder) {
    long debitMinutesTemp = employeeorder.getDebithours().toMinutes();
    // increase debit minutes if timereport exists (update case) by the time of that timereport
    // because this time is read from the database query, too. This is a trick to circumvent this special case.
    if(timereports.size() == 1 && !timereports.getFirst().isNew()) {
      Timereport timereport = timereports.getFirst();
      debitMinutesTemp += timereport.getDurationhours() * MINUTES_PER_HOUR + timereport.getDurationminutes();
    }
    final long debitMinutes = debitMinutesTemp;
    return debitMinutes;
  }

  private void validateOrderBusinessRules(List<Timereport> timereports) throws BusinessRuleException {
    // one timereport exists at least and all share the same data
    Timereport timereport = timereports.getFirst();
    LocalDate refdate = timereport.getReferenceday().getRefdate();
    Suborder suborder = timereport.getSuborder();
    Employeeorder employeeorder = timereport.getEmployeeorder();
    if(TRUE.equals(suborder.getCommentnecessary())) {
      BusinessRuleCheckUtils.notEmpty(timereport.getTaskdescription(), TR_SUBORDER_COMMENT_MANDATORY);
    }
    BusinessRuleCheckUtils.isTrue(
        employeeorder.isValidAt(refdate),
        TR_EMPLOYEE_ORDER_INVALID_REF_DATE
    );
  }

  private void validateContractBusinessRules(List<Timereport> timereports) throws BusinessRuleException {
    // one timereport exists at least and all share the same data
    Timereport timereport = timereports.getFirst();
    LocalDate refdate = timereport.getReferenceday().getRefdate();
    BusinessRuleCheckUtils.isTrue(timereport.getEmployeecontract().isValidAt(refdate),
        TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE);
  }

  private void validateWorkingDayBusinessRules(List<Timereport> timereports) {
    timereports.forEach(timereport -> {
      var employeeContractId = timereport.getEmployeecontract().getId();
      var workingDay = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(
          timereport.getReferenceday().getRefdate(),
          employeeContractId
      );

      // check begin and break rules
      OrderType orderType = timereport.getSuborder().getEffectiveOrderType();
      if (needsWorkingHoursLawValidation(employeeContractId) && isRelevantForWorkingTimeValidation(orderType)) {
        DataValidationUtils.notNull(workingDay, TR_WORKING_DAY_START_NULL);
        DataValidationUtils.notNull(workingDay.getStartOfWorkingDay(), TR_WORKING_DAY_START_NULL);
      }

      // if any time is reported the type of the working day must not be NOT_WORKED for that day
      boolean notWorked = workingDay != null && workingDay.getType() == NOT_WORKED;
      BusinessRuleCheckUtils.isFalse(notWorked, TR_WORKING_DAY_NOT_WORKED);
    });
  }

  private void validateTimeReportingBusinessRules(List<Timereport> timereports) {
    timereports.forEach(timereport -> {
      Year reportedYear = getYear(timereport.getReferenceday().getRefdate());
      // check date range (must be in current, previous or next year)
      BusinessRuleCheckUtils.isTrue(Math.abs(DateUtils.getCurrentYear() - reportedYear.getValue()) <= 1,
          TR_YEAR_OUT_OF_RANGE);

      Integer durationHours = timereport.getDurationhours();
      Integer durationMinutes = timereport.getDurationminutes();
      DataValidationUtils.isTrue(durationHours > 0 || durationMinutes > 0, TR_DURATION_INVALID);
    });
  }

  public Map<LocalDate, ServiceFeedbackMessage> validateBreakTimes(long employeeContractId, LocalDate begin, LocalDate end) {
    if(!needsWorkingHoursLawValidation(employeeContractId)) {
      return Map.of(); // restricted/external users are not in scope of regulations by law
    }

    var timeReports = timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeeContractId, begin, end);
    var timeReportsByDate = timeReports.stream().collect(groupingBy(TimereportDTO::getReferenceday));
    var workingDays = workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, begin, end).stream().collect(toMap(Workingday::getRefday, identity()));

    Set<LocalDate> dates = timeReportsByDate.keySet();
    var errors = dates
        .stream()
        .map(date -> Pair.of(date, validateBreakTime(date, timeReportsByDate, workingDays)))
        .filter(pair -> pair.getSecond().isPresent())
        .collect(toMap(pair -> pair.getFirst(), pair -> pair.getSecond().get()));
    return errors;
  }

  public Map<LocalDate, ServiceFeedbackMessage> validateBeginOfWorkingDays(long employeeContractId, LocalDate begin, LocalDate end) {
    if(!needsWorkingHoursLawValidation(employeeContractId)) {
      return Map.of(); // restricted/external users are not in scope of regulations by law
    }

    var timeReports = timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeeContractId, begin, end);
    var timeReportsByDate = timeReports.stream().collect(groupingBy(TimereportDTO::getReferenceday));
    var workingDays = workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, begin, end).stream().collect(toMap(Workingday::getRefday, identity()));

    Set<LocalDate> dates = timeReportsByDate.keySet();
    var errors = dates
        .stream()
        .map(date -> Pair.of(date, validateBeginOfWorkingDay(date, timeReportsByDate, workingDays)))
        .filter(pair -> pair.getSecond().isPresent())
        .collect(toMap(pair -> pair.getFirst(), pair -> pair.getSecond().get()));
    return errors;
  }

  static boolean isRelevantForWorkingTimeValidation(OrderType orderType) {
    return orderType != null && orderType == OrderType.STANDARD;
  }

  public List<Warning> createTimeReportWarnings(long employeecontractId, MessageResources resources, Locale locale) {

    var employeecontract = employeecontractService.getEmployeecontractById(employeecontractId);

    // warnings
    List<Warning> warnings = new ArrayList<>();

    // timereport warning
    List<TimereportDTO> timereports = getTimereportsOutOfRangeForEmployeeContract(employeecontract.getId());
    for (TimereportDTO timereport : timereports) {
      Warning warning = new Warning();
      warning.setSort(resources.getMessage(locale, "main.info.warning.timereportnotinrange"));
      warning.setText(timereport.getTimeReportAsString());
      warnings.add(warning);
    }

    // timereport warning 2
    timereports = getTimereportsOutOfRangeForEmployeeOrder(employeecontract.getId());
    for (TimereportDTO timereport : timereports) {
      Warning warning = new Warning();
      warning.setSort(resources.getMessage(locale, "main.info.warning.timereportnotinrangeforeo"));
      warning.setText(timereport.getTimeReportAsString() + " " + timereport.getEmployeeOrderAsString());
      warnings.add(warning);
    }

    // timereport warning 3: no duration
    timereports = getTimereportsWithoutDurationForEmployeeContractId(employeecontract.getId(), employeecontract.getReportReleaseDate());
    for (TimereportDTO timereport : timereports) {
      Warning warning = new Warning();
      warning.setSort(resources.getMessage(locale, "main.info.warning.timereport.noduration"));
      warning.setText(timereport.getTimeReportAsString());
      warning.setLink(absoluteUrl("/do/EditDailyReport?trId=" + timereport.getId(), servletContext));
      warnings.add(warning);
    }

    return warnings;
  }

  @EventListener
  void onEmployeeorderUpdate(EmployeeorderUpdateEvent event) {
    var from = event.getDomainObject().getFromDate();
    var until = event.getDomainObject().getEffectiveUntilDate();
    var timereports = timereportDAO.getTimereportsByEmployeeorderIdInvalidForDates(event.getDomainObject().getId(), from, until);
    if(!timereports.isEmpty()) {
      var errors = timereports.stream()
          .map(tr -> error(
              TR_TIMEREPORTS_EXIST_CANNOT_DELETE_OR_UPDATE_EMPLOYEE_ORDER,
              tr.getCompleteOrderSign(),
              tr.getEmployeeSign(),
              tr.getReferenceday()
          ))
          .toList();
      event.veto(errors);
    }
  }

  @EventListener
  void onEmployeeorderDelete(EmployeeorderDeleteEvent event) {
    var employeeorderId = event.getId();
    List<TimereportDTO> timereports = timereportDAO.getTimereportsByEmployeeOrderId(employeeorderId);
    if(!timereports.isEmpty()) {
      var errors = timereports.stream()
          .map(tr -> error(
              TR_TIMEREPORTS_EXIST_CANNOT_DELETE_OR_UPDATE_EMPLOYEE_ORDER,
              tr.getCompleteOrderSign(),
              tr.getEmployeeSign(),
              tr.getReferenceday()
          ))
          .toList();
      event.veto(errors);
    }
  }

  // utility methods

  static Optional<ServiceFeedbackMessage> validateBeginOfWorkingDay(LocalDate date,
      Map<LocalDate, List<TimereportDTO>> timereports,
      Map<LocalDate, Workingday> workingDays) {
    if(!timereports.containsKey(date)) return empty();
    Duration workDurationSum = timereports.get(date).stream()
        .filter(timeReport -> isRelevantForWorkingTimeValidation(timeReport.getOrderType()))
        .map(TimereportDTO::getDuration)
        .reduce(Duration.ZERO, Duration::plus);
    if(!workDurationSum.isPositive()) {
      return empty(); // not worked = no validation
    }
    Workingday workingDay = workingDays.get(date);
    if (workingDay == null || workingDay.getStartOfWorkingDay() == null) {
      return of(error(WD_BEGIN_TIME_MISSING, date));
    }
    return empty();
  }

  static Optional<ServiceFeedbackMessage> validateBreakTime(LocalDate date,
      Map<LocalDate, List<TimereportDTO>> timeReports,
      Map<LocalDate, Workingday> workingDays) {
    if(!timeReports.containsKey(date)) return empty();
    Workingday workingDay = workingDays.get(date);
    Duration workDurationSum = timeReports.get(date).stream()
        .filter(timeReport -> isRelevantForWorkingTimeValidation(timeReport.getOrderType()))
        .map(TimereportDTO::getDuration)
        .reduce(Duration.ZERO, Duration::plus);
    if(!workDurationSum.isPositive()) return empty(); // not worked = no validation
    boolean notEnoughBreaksAfter9Hours = workingDay == null || workingDay.getBreakLengthInMinutes() < BREAK_MINUTES_AFTER_NINE_HOURS;
    boolean notEnoughBreaksAfter6Hours = workingDay == null || workingDay.getBreakLengthInMinutes() < BREAK_MINUTES_AFTER_SIX_HOURS;
    if (workDurationSum.toMinutes() > NINE_HOURS_IN_MINUTES && notEnoughBreaksAfter9Hours) {
      return of(error(WD_BREAK_TOO_SHORT_9, date));
    } else if (workDurationSum.toMinutes() > SIX_HOURS_IN_MINUTES && notEnoughBreaksAfter6Hours) {
      return of(error(WD_BREAK_TOO_SHORT_6, date));
    }
    return empty();
  }

  static boolean noTimeReportsFound(Map<LocalDate, List<TimereportDTO>> timeReports, LocalDate date) {
    return !timeReports.containsKey(date);
  }

}