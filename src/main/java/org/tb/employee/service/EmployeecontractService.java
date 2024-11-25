package org.tb.employee.service;

import static org.tb.common.exception.ErrorCode.EC_OVERLAPS;
import static org.tb.common.exception.ErrorCode.EC_SUPERVISOR_INVALID;
import static org.tb.common.util.DateUtils.getCurrentYear;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.tb.common.ServiceFeedbackMessage;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DataValidationUtils;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Overtime;
import org.tb.employee.event.EmployeecontractUpdatedEvent;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.persistence.OvertimeDAO;
import org.tb.employee.persistence.VacationDAO;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeecontractService {

  private final EmployeecontractDAO employeecontractDAO;
  private final EmployeeDAO employeeDAO;
  private final TimereportDAO timereportDAO;
  private final OvertimeDAO overtimeDAO;
  private final VacationDAO vacationDAO;
  private final ApplicationEventPublisher eventPublisher;

  public Employeecontract getEmployeeContractValidAt(long employeeId, LocalDate date) {
    return employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, date);
  }

  public List<Employeecontract> getViewableEmployeeContractsForAuthorizedUserValidAt(LocalDate validAt) {
    return employeecontractDAO.getViewableEmployeeContractsForAuthorizedUser(validAt);
  }

  public List<Employeecontract> getViewableEmployeeContractsValidAt(LocalDate validAt) {
    return employeecontractDAO.getViewableEmployeeContractsForAuthorizedUser(false, validAt);
  }

  public List<Employeecontract> getTimeReportableEmployeeContractsForAuthorizedUser() {
    return employeecontractDAO.getTimeReportableEmployeeContractsForAuthorizedUser();
  }

  public Employeecontract createEmployeecontract(
      long employeeId,
      LocalDate validFrom,
      LocalDate validUntil,
      long supervisorId,
      String taskDescription,
      boolean freelancer,
      boolean hide,
      Duration dailyWorkingTime,
      int vacationEntitlement,
      Duration initialOvertime
  ) throws AuthorizationException, InvalidDataException, BusinessRuleException {

    var employeecontract = new Employeecontract();
    employeecontract.setOvertimeStatic(Duration.ZERO);
    Employee theEmployee = employeeDAO.getEmployeeById(employeeId);
    employeecontract.setEmployee(theEmployee);
    createOrUpdate(employeecontract, validFrom,
        validUntil,
        supervisorId,
        taskDescription,
        freelancer,
        hide,
        dailyWorkingTime,
        vacationEntitlement);

    if(initialOvertime != null && !initialOvertime.isZero() ) {
      Overtime overtime = new Overtime();
      overtime.setComment("initial overtime");
      overtime.setEmployeecontract(employeecontract);
      overtime.setTime(initialOvertime);
      overtimeDAO.save(overtime);
    }

    return employeecontract;
  }

  public Employeecontract updateEmployeecontract(
      long employeecontractId,
      LocalDate validFrom,
      LocalDate validUntil,
      long supervisorId,
      String taskDescription,
      boolean freelancer,
      boolean hide,
      Duration dailyWorkingTime,
      int vacationEntitlement
  ) throws AuthorizationException, InvalidDataException, BusinessRuleException {

    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    createOrUpdate(employeecontract, validFrom,
        validUntil,
        supervisorId,
        taskDescription,
        freelancer,
        hide,
        dailyWorkingTime,
        vacationEntitlement);
    return employeecontract;
  }

  private void createOrUpdate(
      Employeecontract employeecontract,
      LocalDate validFrom,
      LocalDate validUntil,
      long supervisorId,
      String taskDescription,
      boolean freelancer,
      boolean hide,
      Duration dailyWorkingTime,
      int vacationEntitlement) {

    validateEmployeecontractBusinessRules(employeecontract, validFrom, validUntil, supervisorId);
    validateTimereportBusinessRules(employeecontract, validFrom, validUntil);

    employeecontract.setValidFrom(validFrom);
    employeecontract.setValidUntil(validUntil);

    employeecontract.setSupervisor(employeeDAO.getEmployeeById(supervisorId));
    employeecontract.setTaskDescription(taskDescription);
    employeecontract.setFreelancer(freelancer);
    employeecontract.setHide(hide);
    employeecontract.setDailyWorkingTime(dailyWorkingTime);

    if(!employeecontract.isNew()) {
      var event = new EmployeecontractUpdatedEvent(employeecontract);
      eventPublisher.publishEvent(event);
      if(event.isVetoed()) {
        var messages = event.getMessages().stream().map(ServiceFeedbackMessage::toString).collect(Collectors.joining("\n"));
        log.info("Could not update employee contract:\n{}\nVeto messages:\n{}", employeecontract.toString(), messages);
        throw new BusinessRuleException(ErrorCode.EC_VETOED, messages);
      }
    }

    adjustVacations(employeecontract, vacationEntitlement);
    employeecontractDAO.save(employeecontract);
  }

  private void adjustVacations(Employeecontract employeecontract, int vacationEntitlement) {
    // FIXME calculate vacation entitlement based on existing algorithm
    if(employeecontract.getVacations().isEmpty()) {
      // if necessary, add new vacation for current year
      vacationDAO.addNewVacation(employeecontract, getCurrentYear(), vacationEntitlement);
    } else {
      employeecontract.getVacations()
          .stream()
          .forEach(v -> v.setEntitlement(vacationEntitlement));
    }
  }

  private void validateTimereportBusinessRules(Employeecontract employeecontract, LocalDate validFrom,
      LocalDate validUntil) {
    if(employeecontract.isNew()) return; // fresh new contract dont have to mind time report rules

    // no time reports may exist outside the validity of the employee contract
    timereportDAO.getTimereportsByEmployeeContractIdInvalidForDates(validFrom, validUntil, employeecontract.getId())
        .stream().findAny().ifPresent((timereport) -> {
          throw new BusinessRuleException(ErrorCode.EC_TIME_REPORTS_OUTSIDE_VALIDITY);
        });
  }

  private void validateEmployeecontractBusinessRules(Employeecontract employeecontract, LocalDate validFrom,
      LocalDate validUntil, long supervisorId) {
    DataValidationUtils.validDateRange(validFrom, validUntil, ErrorCode.EC_INVALID_DATE_RANGE);

    if(employeecontract.getEmployee().getId().equals(supervisorId)) {
      throw new BusinessRuleException(EC_SUPERVISOR_INVALID);
    }
    if(employeeDAO.getEmployeeById(supervisorId) == null) {
      throw new BusinessRuleException(EC_SUPERVISOR_INVALID);
    }

    // ensure no overlapping employee contracts
    employeecontract.setValidFrom(validFrom);
    employeecontract.setValidUntil(validUntil);
    List<Employeecontract> allEmployeecontracts = employeecontractDAO.getEmployeeContractsByEmployeeId(employeecontract.getEmployee().getId());
    for (Employeecontract compareEmployeeecontract : allEmployeecontracts) {
      if (!Objects.equals(compareEmployeeecontract.getId(), employeecontract.getId())) {
        if(employeecontract.overlaps(compareEmployeeecontract)) {
          throw new BusinessRuleException(EC_OVERLAPS);
        }
      }
    }

  }

  public Optional<Employeecontract> getCurrentContract(long employeeId) {
    return Optional.ofNullable(employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, today()));
  }

  public Employeecontract getEmployeecontractById(long employeeContractId) {
    return employeecontractDAO.getEmployeecontractById(employeeContractId);
  }

  public List<Employeecontract> getTeamContracts(long teamManagerEmployeeId) {
    return employeecontractDAO.getTeamContracts(teamManagerEmployeeId);
  }

  public List<Employeecontract> getAllEmployeeContracts() {
    return employeecontractDAO.getEmployeeContracts();
  }

  public boolean deleteEmployeeContractById(long employeeContractId) {
    return employeecontractDAO.deleteEmployeeContractById(employeeContractId);
  }

  public List<Employeecontract> getEmployeeContractsByFilters(Boolean showInvalid, String filter,
      Long filterEmployeeId) {
    return employeecontractDAO.getEmployeeContractsByFilters(showInvalid, filter, filterEmployeeId);
  }

  public Employeecontract getEmployeeContractWithVacationsById(long employeeContractId) {
    return employeecontractDAO.getEmployeeContractByIdInitializeEager(employeeContractId);
  }

  public List<Employeecontract> getAllVisibleEmployeeContractsValidAtOrderedByFirstname(LocalDate validAt) {
    return employeecontractDAO.getAllVisibleEmployeeContractsValidAtOrderedByFirstname(validAt);
  }

  public List<Overtime> getOvertimesByEmployeeContractId(long employeeContractId) {
    return overtimeDAO.getOvertimesByEmployeeContractId(employeeContractId);
  }

  public void save(Overtime overtime) {
    overtimeDAO.save(overtime);
  }
}
