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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DataValidationUtils;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.VacationDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Overtime;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.persistence.OvertimeDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;

@Service
@RequiredArgsConstructor
public class EmployeecontractService {

  private final EmployeecontractDAO employeecontractDAO;
  private final EmployeeDAO employeeDAO;
  private final EmployeeorderDAO employeeorderDAO;
  private final TimereportDAO timereportDAO;
  private final OvertimeDAO overtimeDAO;
  private final VacationDAO vacationDAO;

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

    employeecontractDAO.save(employeecontract);

    adjustEmployeeOrders(employeecontract);
    adjustVacations(employeecontract, vacationEntitlement);
  }

  private void adjustVacations(Employeecontract employeecontract, int vacationEntitlement) {
    if(employeecontract.getVacations().isEmpty()) {
      // if necessary, add new vacation for current year
      vacationDAO.addNewVacation(employeecontract, getCurrentYear(), vacationEntitlement);
    } else {
      employeecontract.getVacations()
          .stream()
          .forEach(v -> v.setEntitlement(vacationEntitlement));
      employeecontractDAO.save(employeecontract);
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

  private void adjustEmployeeOrders(Employeecontract employeecontract) {
    if(employeecontract.isNew()) return; // no adjustment required - there is nothing to adjust yet

    // adjust employeeorders
    List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractId(employeecontract.getId());
    for (Employeeorder employeeorder : employeeorders) {
      boolean remove = false;
      if (employeeorder.getUntilDate() != null && employeeorder.getUntilDate().isBefore(employeecontract.getValidFrom())) {
        remove = true;
      }
      if (employeecontract.getValidUntil() != null) {
        if (employeeorder.getFromDate().isAfter(employeecontract.getValidUntil())) {
          remove = true;
        }
      }
      if(remove) {
        employeecontract.getEmployeeorders().remove(employeeorder);
        var deleted = employeeorderDAO.deleteEmployeeorderById(employeeorder.getId());
        if(!deleted) {
          throw new BusinessRuleException(ErrorCode.EC_EFFECTIVE_EMPLOYEE_ORDER_OUTSIDE_VALIDITY);
        }
      } else {
        boolean changed = false;
        if (employeeorder.getFromDate().isBefore(employeecontract.getValidFrom())) {
          employeeorder.setFromDate(employeecontract.getValidFrom());
          changed = true;
        }
        if (employeecontract.getValidUntil() != null) {
          if (employeeorder.getUntilDate() == null || employeeorder.getUntilDate().isAfter(employeecontract.getValidUntil())) {
            employeeorder.setUntilDate(employeecontract.getValidUntil());
            changed = true;
          }
        }
        if (changed) {
          employeeorderDAO.save(employeeorder);
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
