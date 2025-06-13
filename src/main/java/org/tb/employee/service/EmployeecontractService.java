package org.tb.employee.service;

import static org.tb.common.exception.ErrorCode.EC_CONFLICT_RESOLUTION_GOT_VETO;
import static org.tb.common.exception.ErrorCode.EC_OVERLAPS;
import static org.tb.common.exception.ErrorCode.EC_SUPERVISOR_INVALID;
import static org.tb.common.exception.ErrorCode.EC_UNRESOLVABLE_CONFLICT_TOO_MANY_OVERLAPS;
import static org.tb.common.exception.ErrorCode.EC_UNRESOLVABLE_CONFLICT_VALIDITY_SPLIT;
import static org.tb.common.exception.ErrorCode.EC_UPDATE_GOT_VETO;
import static org.tb.common.exception.ServiceFeedbackMessage.error;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.LocalDateRange;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;
import org.tb.common.util.DataValidationUtils;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employee_;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Employeecontract_;
import org.tb.employee.domain.Overtime;
import org.tb.employee.domain.Vacation;
import org.tb.employee.event.EmployeeDeleteEvent;
import org.tb.employee.event.EmployeecontractConflictResolutionEvent;
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
@Authorized
public class EmployeecontractService {

  private final ApplicationEventPublisher eventPublisher;
  private final EmployeecontractDAO employeecontractDAO;
  private final EmployeeDAO employeeDAO;
  private final EmployeecontractRepository employeecontractRepository;
  private final VacationRepository vacationRepository;
  private final OvertimeRepository overtimeRepository;

  @Authorized(requiresManager = true)
  public ContractStoredInfo createEmployeecontract(
      long employeeId,
      LocalDate validFrom,
      LocalDate validUntil,
      long supervisorId,
      String taskDescription,
      boolean freelancer,
      boolean hide,
      Duration dailyWorkingTime,
      int vacationEntitlement,
      Duration initialOvertime,
      boolean resolveConflicts
  ) throws AuthorizationException, InvalidDataException, BusinessRuleException {

    var employeecontract = new Employeecontract();
    employeecontract.setOvertimeStatic(Duration.ZERO);
    Employee theEmployee = employeeDAO.getEmployeeById(employeeId);
    employeecontract.setEmployee(theEmployee);
    var info = createOrUpdate(employeecontract, validFrom,
        validUntil,
        supervisorId,
        taskDescription,
        freelancer,
        hide,
        dailyWorkingTime,
        vacationEntitlement,
        resolveConflicts);

    if(initialOvertime != null && !initialOvertime.isZero() ) {
      Overtime overtime = new Overtime();
      overtime.setComment("initial overtime");
      overtime.setEmployeecontract(employeecontract);
      overtime.setTime(initialOvertime);
      create(overtime);
    }

    createVacation(employeecontract.getId(), Year.now(), vacationEntitlement);
    return info;
  }

  @Authorized(requiresManager = true)
  public ContractStoredInfo updateEmployeecontract(
      long employeecontractId,
      LocalDate validFrom,
      LocalDate validUntil,
      long supervisorId,
      String taskDescription,
      boolean freelancer,
      boolean hide,
      Duration dailyWorkingTime,
      int vacationEntitlement,
      boolean resolveConflicts
  ) throws AuthorizationException, InvalidDataException, BusinessRuleException {

    var employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    return createOrUpdate(employeecontract, validFrom,
        validUntil,
        supervisorId,
        taskDescription,
        freelancer,
        hide,
        dailyWorkingTime,
        vacationEntitlement,
        resolveConflicts);
  }

  private ContractStoredInfo createOrUpdate(
      Employeecontract employeecontract,
      LocalDate validFrom,
      LocalDate validUntil,
      long supervisorId,
      String taskDescription,
      boolean freelancer,
      boolean hide,
      Duration dailyWorkingTime,
      int vacationEntitlement,
      boolean resolveConflicts) {

    List<String> logs = new ArrayList<>();

    var throwResolvableConflicts = !resolveConflicts; // throw always if not resolving any conflicts
    var valid = validateEmployeecontractBusinessRules(employeecontract, validFrom, validUntil, supervisorId, throwResolvableConflicts);

    employeecontract.setValidUntil(validFrom);
    employeecontract.setValidFrom(validFrom);
    employeecontract.setValidUntil(validUntil);

    employeecontract.setSupervisor(employeeDAO.getEmployeeById(supervisorId));
    employeecontract.setTaskDescription(taskDescription);
    employeecontract.setFreelancer(freelancer);
    employeecontract.setHide(hide);
    employeecontract.setDailyWorkingTime(dailyWorkingTime);

    adjustVacations(employeecontract, vacationEntitlement);

    if(!valid) {

      // get the conflicting employee contract
      var overlappingContracts = getOverlapping(employeecontract);
      var conflictingEmployeecontract = overlappingContracts.getFirst();
      logs.add("Konflikt mit altem Vertrag %s erkannt. Automatische Auflösung angefordert...".formatted(conflictingEmployeecontract.getValidity()));

      // set the conflicting employee contract to invalid - it ends one day before
      var conflictingValidity = conflictingEmployeecontract.getValidity();
      var resolvedValidities = conflictingValidity.minus(employeecontract.getValidity());
      if(resolvedValidities.size() > 1) {
        throw new BusinessRuleException(EC_UNRESOLVABLE_CONFLICT_VALIDITY_SPLIT, resolvedValidities.size());
      }
      LocalDateRange resolvedValidity = resolvedValidities.getFirst();
      conflictingEmployeecontract.setValidFrom(resolvedValidity.getFrom());
      conflictingEmployeecontract.setValidUntil(resolvedValidity.getUntil());
      logs.add("Alten Vertrag angepasst von %s nach %s.".formatted(conflictingValidity, resolvedValidity));

      // update release and acceptance dates
      if(conflictingEmployeecontract.getReportReleaseDate() != null) {
        conflictingEmployeecontract.setReportReleaseDate(
            DateUtils.min(conflictingEmployeecontract.getReportReleaseDate(), conflictingEmployeecontract.getValidUntil())
        );
        logs.add("Freigabedatum im alten Vertrag angepasst: " + DateUtils.format(conflictingEmployeecontract.getReportReleaseDate()));
      }
      if(conflictingEmployeecontract.getReportAcceptanceDate() != null) {
        conflictingEmployeecontract.setReportAcceptanceDate(
            DateUtils.min(conflictingEmployeecontract.getReportAcceptanceDate(), conflictingEmployeecontract.getValidUntil())
        );
        logs.add("Abnahmedatum im alten Vertrag angepasst: " + DateUtils.format(conflictingEmployeecontract.getReportAcceptanceDate()));
      }

      // save contracts to ensure id is set before resolving conflicts (other parts in this software rely on this)
      employeecontractRepository.save(employeecontract);
      employeecontractRepository.save(conflictingEmployeecontract);

      var event = new EmployeecontractConflictResolutionEvent(employeecontract, conflictingEmployeecontract);
      try {
        eventPublisher.publishEvent(event);
        logs.addAll(event.getEventLog());
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(
            EC_CONFLICT_RESOLUTION_GOT_VETO
        ));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }

      var updateEvent = new EmployeecontractUpdateEvent(conflictingEmployeecontract);
      try {
        eventPublisher.publishEvent(updateEvent);
        logs.addAll(updateEvent.getEventLog());
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(
            EC_CONFLICT_RESOLUTION_GOT_VETO
        ));
        allMessages.addAll(e.getMessages());
        updateEvent.veto(allMessages);
      }
    }

    if(!employeecontract.isNew()) {
      var event = new EmployeecontractUpdateEvent(employeecontract);
      try {
        eventPublisher.publishEvent(event);
        logs.addAll(event.getEventLog());
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

    var info = new ContractStoredInfo(employeecontract.getId());
    info.addLogs(logs);
    return info;
  }

  private Vacation createVacation(long employeecontractId, Year year, int vacationEntitlement) {
    var employeecontract = getEmployeecontractById(employeecontractId);
    var vacation = new Vacation();
    vacation.setEmployeecontract(employeecontract);
    vacation.setYear(year.getValue());
    vacation.setEntitlement(vacationEntitlement);
    vacation.setUsed(0);
    employeecontract.getVacations().add(vacation);
    vacationRepository.save(vacation);
    return vacation;
  }

  public Duration getEffectiveVacationEntitlement(long employeecontractId, Year year) {
    var employeecontract = getEmployeecontractById(employeecontractId);
    var vacation = vacationRepository
        .findByEmployeecontractIdAndYear(employeecontractId, year.getValue())
        .orElseGet(() -> createVacation(employeecontractId, year, employeecontract.getVacationEntitlement()));
    return vacation.getEffectiveEntitlement();
  }

  private void adjustVacations(Employeecontract employeecontract, int vacationEntitlement) {
    employeecontract.getVacations().stream().forEach(v -> {
      if(!v.getEntitlement().equals(vacationEntitlement)) {
        v.setEntitlement(vacationEntitlement);
      }
    });
  }

  private boolean validateEmployeecontractBusinessRules(Employeecontract employeecontract, LocalDate validFrom,
      LocalDate validUntil, long supervisorId, boolean throwResolvableConflicts) {
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
    List<Employeecontract> overlapping = getOverlapping(employeecontract);
    if(!overlapping.isEmpty()) {
      if(throwResolvableConflicts) {
        throw new BusinessRuleException(EC_OVERLAPS);
      }
      // only one overlapping contract can be resolved
      if(overlapping.size() > 1) {
        throw new BusinessRuleException(EC_UNRESOLVABLE_CONFLICT_TOO_MANY_OVERLAPS, overlapping.size());
      }
      return false;
    }
    return true;
  }

  private List<Employeecontract> getOverlapping(Employeecontract employeecontract) {
    List<Employeecontract> allEmployeecontracts = employeecontractDAO.getEmployeeContractsByEmployeeId(
        employeecontract.getEmployee().getId());
    List<Employeecontract> overlapping = allEmployeecontracts
        .stream()
        .filter(otherEmployeecontract -> !Objects.equals(otherEmployeecontract.getId(), employeecontract.getId()))
        .filter(ec -> ec.overlaps(employeecontract))
        .toList();
    return overlapping;
  }

  @Authorized(requiresManager = true)
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
    var employeecontract = overtime.getEmployeecontract();
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
    var contract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, today());
    if(contract != null) {
      return Optional.of(contract);
    }

    // fallback find next future contract
    Specification<Employeecontract> spec = (root, query, builder) -> {
      var validFromInFuture = builder.greaterThan(root.get(Employeecontract_.validFrom), today());
      var employee = root.join(Employeecontract_.employee);
      var employeeIdMatches = builder.equal(employee.get(Employee_.id), employeeId);
      return builder.and(validFromInFuture, employeeIdMatches);
    };
    return employeecontractRepository.findOne(spec);
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

  public List<Employeecontract> getFutureContracts(long employeecontractId) {
    var employeecontract = getEmployeecontractById(employeecontractId);
    if(employeecontract != null) {
      Specification<Employeecontract> spec = (root, query, builder) -> {
        var equalEmployeeId = builder.equal(root.get(Employeecontract_.employee).get(Employee_.id), employeecontract.getEmployee().getId());
        var greaterValidFrom = builder.greaterThan(root.get(Employeecontract_.validFrom), employeecontract.getValidFrom());
        return builder.and(equalEmployeeId, greaterValidFrom);
      };
      return employeecontractRepository.findAll(spec);
    }
    return List.of();
  }

  @Getter
  @RequiredArgsConstructor
  public static class ContractStoredInfo {
    private final long id;
    private List<String> log = new ArrayList<>();

    public void addLog(String logEntry) {
      log.add(logEntry);
    }

    public void addLogs(List<String> logs) {
      log.addAll(logs);
    }

  }

}
