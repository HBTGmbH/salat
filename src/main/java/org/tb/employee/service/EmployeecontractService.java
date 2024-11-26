package org.tb.employee.service;

import static org.tb.common.exception.ServiceFeedbackMessage.error;
import static org.tb.common.exception.ErrorCode.EC_OVERLAPS;
import static org.tb.common.exception.ErrorCode.EC_SUPERVISOR_INVALID;
import static org.tb.common.exception.ErrorCode.EC_UPDATE_GOT_VETO;
import static org.tb.common.util.DateUtils.getCurrentYear;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DataValidationUtils;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Overtime;
import org.tb.employee.event.EmployeecontractDeleteEvent;
import org.tb.employee.event.EmployeecontractUpdateEvent;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.persistence.EmployeecontractRepository;
import org.tb.employee.persistence.OvertimeDAO;
import org.tb.employee.persistence.VacationDAO;
import org.tb.employee.persistence.VacationRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeecontractService {

  private final EmployeecontractDAO employeecontractDAO;
  private final EmployeeDAO employeeDAO;
  private final OvertimeDAO overtimeDAO;
  private final VacationDAO vacationDAO;
  private final EmployeecontractRepository employeecontractRepository;
  private final VacationRepository vacationRepository;
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

  public List<ServiceFeedbackMessage> createEmployeecontract(
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
    var messages = createOrUpdate(employeecontract, validFrom,
        validUntil,
        supervisorId,
        taskDescription,
        freelancer,
        hide,
        dailyWorkingTime,
        vacationEntitlement);

    if(messages.stream().anyMatch(ServiceFeedbackMessage::isError)) {
      return messages;
    }

    if(initialOvertime != null && !initialOvertime.isZero() ) {
      Overtime overtime = new Overtime();
      overtime.setComment("initial overtime");
      overtime.setEmployeecontract(employeecontract);
      overtime.setTime(initialOvertime);
      overtimeDAO.save(overtime);
    }

    return List.of();
  }

  public List<ServiceFeedbackMessage> updateEmployeecontract(
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
    var messages = createOrUpdate(employeecontract, validFrom,
        validUntil,
        supervisorId,
        taskDescription,
        freelancer,
        hide,
        dailyWorkingTime,
        vacationEntitlement);
    return messages;
  }

  private List<ServiceFeedbackMessage> createOrUpdate(
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

    if(!employeecontract.isNew()) {
      var event = new EmployeecontractUpdateEvent(employeecontract);
      eventPublisher.publishEvent(event);
      if(event.isVetoed()) {
        var messages = event.getMessages().stream().map(ServiceFeedbackMessage::toString).collect(Collectors.joining("\n"));
        log.info("Could not update employee contract for {} ({}).\nVeto messages:\n{}", employeecontract.getEmployee().getSign(), employeecontract.getTimeString(), messages);
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(EC_UPDATE_GOT_VETO, employeecontract.getEmployee().getSign()));
        allMessages.addAll(event.getMessages());
        return allMessages;
      }
    }

    adjustVacations(employeecontract, vacationEntitlement);
    employeecontractRepository.save(employeecontract);
    return List.of();
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

  public List<ServiceFeedbackMessage> deleteEmployeeContractById(long employeeContractId) {
    Employeecontract ec = getEmployeecontractById(employeeContractId);

    if (ec != null) {

      var event = new EmployeecontractDeleteEvent(employeeContractId);
      eventPublisher.publishEvent(event);

      if(event.isVetoed()) {
        return event.getMessages();
      }

      // if ok for deletion, check for overtime and vacation entries and
      // delete them successively (cannot yet be done via web application)

      var overtimes = overtimeDAO.getOvertimesByEmployeeContractId(employeeContractId);
      overtimes.stream()
          .map(AuditedEntity::getId)
          .forEach(overtimeDAO::deleteOvertimeById);

      vacationRepository.findByEmployeecontractId(employeeContractId).stream()
          .map(AuditedEntity::getId)
          .forEach(vacationRepository::deleteById);

      // finally, go for deletion of employeecontract
      employeecontractRepository.delete(ec);
    }
    return List.of();
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
