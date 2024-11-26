package org.tb.employee.service;

import static org.tb.common.exception.ErrorCode.EC_OVERLAPS;
import static org.tb.common.exception.ErrorCode.EC_SUPERVISOR_INVALID;
import static org.tb.common.exception.ErrorCode.EC_UPDATE_GOT_VETO;
import static org.tb.common.exception.ServiceFeedbackMessage.error;
import static org.tb.common.util.DateUtils.getCurrentYear;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;
import org.tb.common.util.DataValidationUtils;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Overtime;
import org.tb.employee.domain.Vacation;
import org.tb.employee.event.EmployeeDeleteEvent;
import org.tb.employee.event.EmployeecontractDeleteEvent;
import org.tb.employee.event.EmployeecontractUpdateEvent;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.persistence.EmployeecontractRepository;
import org.tb.employee.persistence.OvertimeRepository;
import org.tb.employee.persistence.VacationRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeecontractService {

  private final ApplicationEventPublisher eventPublisher;
  private final EmployeecontractDAO employeecontractDAO;
  private final EmployeeDAO employeeDAO;
  private final EmployeecontractRepository employeecontractRepository;
  private final VacationRepository vacationRepository;
  private final OvertimeRepository overtimeRepository;

  public long createEmployeecontract(
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
      create(overtime);
    }

    createVacation(employeecontract.getId(), getCurrentYear(), vacationEntitlement);
    return employeecontract.getId();
  }

  public void updateEmployeecontract(
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

    employeecontract.setValidFrom(validFrom);
    employeecontract.setValidUntil(validUntil);

    employeecontract.setSupervisor(employeeDAO.getEmployeeById(supervisorId));
    employeecontract.setTaskDescription(taskDescription);
    employeecontract.setFreelancer(freelancer);
    employeecontract.setHide(hide);
    employeecontract.setDailyWorkingTime(dailyWorkingTime);

    adjustVacations(employeecontract, vacationEntitlement);

    if(!employeecontract.isNew()) {
      var event = new EmployeecontractUpdateEvent(employeecontract);
      try {
        eventPublisher.publishEvent(event);
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(
            EC_UPDATE_GOT_VETO,
            employeecontract.getEmployee().getSign()
        ));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }
    }
    employeecontractRepository.save(employeecontract);
  }

  private Vacation createVacation(long employeecontractId, int year, int vacationEntitlement) {
    var employeecontract = getEmployeecontractById(employeecontractId);
    var vacation = new Vacation();
    vacation.setEmployeecontract(employeecontract);
    vacation.setYear(year);
    vacation.setEntitlement(vacationEntitlement);
    vacation.setUsed(0);
    employeecontract.getVacations().add(vacation);
    vacationRepository.save(vacation);
    return vacation;
  }

  public Duration getEffectiveVacationEntitlement(long employeecontractId, int year) {
    var employeecontract = getEmployeecontractById(employeecontractId);
    var vacation = vacationRepository
        .findByEmployeecontractIdAndYear(employeecontractId, year)
        .orElseGet(() -> createVacation(employeecontractId, year, employeecontract.getVacationEntitlement()));
    return vacation.getEffectiveEntitlement();
  }

  private void adjustVacations(Employeecontract employeecontract, int vacationEntitlement) {
    employeecontract.getVacations().stream().forEach(v -> v.setEntitlement(vacationEntitlement));
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

  public void deleteEmployeeContractById(long employeeContractId) {
    Employeecontract ec = getEmployeecontractById(employeeContractId);

    if (ec != null) {

      var event = new EmployeecontractDeleteEvent(employeeContractId);
      try {
        eventPublisher.publishEvent(event);
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(
            ErrorCode.EC_DELETE_GOT_VETO,
            ec.getEmployee().getSign()
        ));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }

      // if ok for deletion, check for overtime and vacation entries and
      // delete them successively (cannot yet be done via web application)

      var overtimes = overtimeRepository.findAllByEmployeecontractId(employeeContractId);
      overtimes.stream()
          .map(AuditedEntity::getId)
          .forEach(overtimeRepository::deleteById);

      vacationRepository.findAllByEmployeecontractId(employeeContractId).stream()
          .map(AuditedEntity::getId)
          .forEach(vacationRepository::deleteById);

      // finally, go for deletion of employeecontract
      employeecontractRepository.delete(ec);
    }
  }

  public void updateOvertimeStatic(Long employeecontractId, Duration overtimeStaticNewValue) {
    getEmployeecontractById(employeecontractId).setOvertimeStatic(overtimeStaticNewValue);
  }

  public void updateReportReleaseData(Long employeecontractId, LocalDate releaseDate, LocalDate acceptanceDate) {
    Employeecontract employeecontract = getEmployeecontractById(employeecontractId);
    employeecontract.setReportReleaseDate(releaseDate);
    employeecontract.setReportAcceptanceDate(acceptanceDate);
  }

  public void create(Overtime overtime) {
    overtimeRepository.save(overtime);
  }

  @EventListener
  void onEmployeeDelete(EmployeeDeleteEvent event) {
    var employeecontracts = employeecontractDAO.getEmployeeContractsByEmployeeId(event.getId());
    for (var employeecontract : employeecontracts) {
      deleteEmployeeContractById(employeecontract.getId());
    }
  }

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

  public List<Overtime> getOvertimeAdjustmentsByEmployeeContractId(long employeeContractId) {
    return overtimeRepository.findAllByEmployeecontractId(employeeContractId);
  }

}
