package org.tb.order.service;

import static java.lang.Boolean.TRUE;
import static java.time.Year.parse;
import static org.tb.common.exception.ServiceFeedbackMessage.error;
import static org.tb.common.util.DateUtils.today;
import static org.tb.order.command.GetTimereportMinutesCommandEvent.OrderType.EMPLOYEE;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.LocalDateRange;
import org.tb.common.GlobalConstants;
import org.tb.common.command.CommandPublisher;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.VetoedException;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.event.EmployeecontractChangedEvent;
import org.tb.employee.event.EmployeecontractDeleteEvent;
import org.tb.employee.event.EmployeecontractUpdateEvent;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.command.GetTimereportMinutesCommandEvent;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.event.EmployeeorderDeleteEvent;
import org.tb.order.event.EmployeeorderUpdateEvent;
import org.tb.order.event.SuborderDeleteEvent;
import org.tb.order.event.SuborderUpdateEvent;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.EmployeeorderRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class EmployeeorderService {

  private final ApplicationEventPublisher eventPublisher;
  private final CommandPublisher commandPublisher;
  private final EmployeeorderDAO employeeorderDAO;
  private final SuborderService suborderService;
  private final EmployeeorderRepository employeeorderRepository;
  private final EmployeecontractService employeecontractService;

  @Authorized(requiresManager = true)
  public void create(Employeeorder employeeorder) {
    createOrUpdate(employeeorder, employeeorder.getFromDate(), employeeorder.getUntilDate());
  }

  @Authorized(requiresManager = true)
  public void update(Employeeorder employeeorder) {
    createOrUpdate(employeeorder, employeeorder.getFromDate(), employeeorder.getUntilDate());
  }

  private void generateMissingStandardOrders(long employeecontractId) {
    Employeecontract contract = employeecontractService.getEmployeecontractById(employeecontractId);
    List<Employeecontract> futureContracts = employeecontractService.getFutureContracts(employeecontractId);
    var contracts = new HashSet<Employeecontract>(futureContracts);
    contracts.add(contract);

    for(Employeecontract employeecontract : contracts) {
      generateMissingStandardOrders(employeecontract);
    }
  }

  private void generateMissingStandardOrders(Employeecontract employeecontract) {
    if(employeecontract.getFreelancer() == TRUE) return;

    List<Suborder> standardSuborders = suborderService.getStandardSuborders();
    if (standardSuborders != null && !standardSuborders.isEmpty()) {
      // test if employeeorder exists
      for (Suborder suborder : standardSuborders) {

        var contractValidity = employeecontract.getValidity();
        var orderValidity = suborder.getValidity();
        var effectiveValidity = contractValidity.intersection(orderValidity);

        // check if effective validity has at least a single day - otherwise creation makes no sense - skip it!
        if(effectiveValidity == null) {
          continue;
        }

        // check if effective validity is not in the past (before date of accepted time reports) - else SKIP IT!!!
        var acceptanceDate = employeecontract.getReportAcceptanceDate();
        if(acceptanceDate != null && effectiveValidity.isBefore(acceptanceDate)) {
          continue;
        }

        boolean employeeorderPresent = employeeorderDAO.getEmployeeorderCount(employeecontract.getId(), suborder.getId()) > 0;
        if (!employeeorderPresent) {

          // skip vacation orders that do not match the contract
          if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)) {
            var year = parse(suborder.getSign());
            if(!contractValidity.overlaps(year)) {
              continue; // skip creation
            }
          }

          Employeeorder employeeorder = new Employeeorder();
          employeeorder.setFromDate(effectiveValidity.getFrom());
          employeeorder.setUntilDate(effectiveValidity.getUntil());
          employeeorder.setEmployeecontract(employeecontract);
          employeeorder.setSign(" ");
          employeeorder.setSuborder(suborder);

          // calculate effective vacation entitlement and set budget accordingly
          if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)) {
            var vacationOrderYear = parse(suborder.getSign());
            var vacationBudget = employeecontractService.getEffectiveVacationEntitlement(employeecontract.getId(), vacationOrderYear); // calculate real entitlement
            employeeorder.setDebithours(vacationBudget);
            employeeorder.setDebithoursunit(GlobalConstants.DEBITHOURS_UNIT_TOTALTIME);
          }

          createOrUpdate(employeeorder, effectiveValidity.getFrom(), effectiveValidity.getUntil());
          log.info(
              "Created standard order for order {} and employee {} and contract {}.",
              suborder.getCompleteOrderSign(),
              employeecontract.getEmployee().getSign(),
              employeecontract.getId()
          );
        }
      }
    }
  }

  @Authorized(requiresManager = true)
  public void deleteEmployeeorderById(long employeeOrderId) {
    var employeeorder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
    var event = new EmployeeorderDeleteEvent(employeeOrderId);
    try {
      eventPublisher.publishEvent(event);
    } catch(VetoedException e) {
      // adding context to the veto to make it easier to understand the complete picture
      var allMessages = new ArrayList<ServiceFeedbackMessage>();
      allMessages.add(error(
          ErrorCode.EO_DELETE_GOT_VETO,
          employeeorder.getSuborder().getCompleteOrderSign(),
          employeeorder.getEmployeecontract().getEmployee().getSign()
      ));
      allMessages.addAll(e.getMessages());
      event.veto(allMessages);
    }
    employeeorderRepository.deleteById(employeeOrderId);
  }

  @EventListener
  void onEmployeecontractUpdate(EmployeecontractUpdateEvent event) {
    var employeecontract = event.getDomainObject();
    var newValidity = employeecontract.getValidity();

    // adjust employeeorders
    List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractId(employeecontract.getId());
    for (Employeeorder employeeorder : employeeorders) {
      var existingValidity = employeeorder.getValidity();
      var updating = existingValidity.overlaps(newValidity);
      if(updating) {
        if(!newValidity.contains(existingValidity)) {
          // new validity does not contain the validity of the employee order, thus we need to reduce it to fit in
          adjustValidity(employeeorder.getId(), newValidity);
          // ensure correct reduction of vacation budget
          if(isVacationOrder(employeeorder)) {
            adjustVacationBudget(employeeorder);
          }
        }
      } else {
        deleteEmployeeorderById(employeeorder.getId());
      }
    }
  }

  private boolean isVacationOrder(Employeeorder employeeorder) {
    return employeeorder.getSuborder().getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION);
  }

  private void adjustVacationBudget(Employeeorder employeeorder) {
    var year = parse(employeeorder.getSuborder().getSign());
    var budget = employeecontractService.getEffectiveVacationEntitlement(employeeorder.getEmployeecontract().getId(), year);
    employeeorder.setDebithours(budget);
    createOrUpdate(employeeorder, employeeorder.getFromDate(), employeeorder.getUntilDate());
  }

  public List<Employeeorder> getEmployeeordersByFilters(Boolean showInvalid, String filter, long employeeContractId,
      Long customerOrderId) {
    return employeeorderDAO.getEmployeeordersByFilters(showInvalid, filter, employeeContractId, customerOrderId);
  }

  public List<Employeeorder> getEmployeeordersByFilters(Boolean showInvalid, String filter, Long employeeContractId, Long customerOrderId, Long suborderId) {
    return employeeorderDAO.getEmployeeordersByFilters(showInvalid, filter, employeeContractId, customerOrderId, suborderId);
  }

  public List<Employeeorder> getEmployeeOrdersByEmployeeContractIdAndSuborderId(long employeeContractId,
      long suborderId) {
    return employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(employeeContractId, suborderId);
  }

  public Employeeorder getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(long employeecontractId,
      long suborderId, LocalDate date) {
    return employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(employeecontractId, suborderId, date);
  }

  public Employeeorder getEmployeeorderForEmployeecontractValidAt(long employeecontractId, long suborderId, LocalDate validAt) {
    return employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(employeecontractId, suborderId, validAt);
  }

  public List<Employeeorder> getEmployeeordersForEmployeecontractAndValidAt(long employeecontractId, LocalDate validAt) {
    return employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(employeecontractId, validAt);
  }

  public List<Employeeorder> getEmployeeordersByCustomerorderIdAndEmployeeContractId(long customerorderId, long employeeContractId) {
    return employeeorderDAO.getEmployeeordersByOrderIdAndEmployeeContractId(customerorderId, employeeContractId);
  }

  public List<Employeeorder> getEmployeeOrderByEmployeeContractIdAndSuborderIdAndValidAt(long employeeContractId,
      long suborderSignId, LocalDate validAt) {
    return employeeorderDAO
        .getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(employeeContractId, suborderSignId, validAt)
        .stream()
        .filter(eo -> eo.isValidAt(validAt))
        .toList();
  }

  public Employeeorder getEmployeeorderById(Long employeeOrderId) {
    return employeeorderDAO.getEmployeeorderById(employeeOrderId);
  }

  public List<Employeeorder> getVacationEmployeeOrders(long employeecontractId) {
    return employeeorderDAO.getVacationEmployeeOrdersByEmployeeContractIdAndDate(employeecontractId, today());
  }

  public List<Employeeorder> getAllEmployeeOrders() {
    return employeeorderDAO.getEmployeeorders();
  }

  @EventListener
  void onEmployeecontractDelete(EmployeecontractDeleteEvent event) {
    var employeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractId(event.getId());
    for (Employeeorder employeeorder : employeeorders) {
      deleteEmployeeorderById(employeeorder.getId());
    }
  }

  @EventListener
  void onSuborderUpdate(SuborderUpdateEvent event) {
    var suborder = event.getDomainObject();
    var newValidity = suborder.getValidity();

    // adjust employeeorders
    List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersBySuborderId(suborder.getId());
    for (Employeeorder employeeorder : employeeorders) {
      var existingValidity = employeeorder.getValidity();
      var updating = existingValidity.overlaps(newValidity);
      if(updating) {
        adjustValidity(employeeorder.getId(), newValidity);
      } else {
        deleteEmployeeorderById(employeeorder.getId());
      }
    }
  }

  @EventListener
  void onSuborderDelete(SuborderDeleteEvent event) {
    var employeeorders = employeeorderDAO.getEmployeeOrdersBySuborderId(event.getId());
    for (Employeeorder employeeorder : employeeorders) {
      deleteEmployeeorderById(employeeorder.getId());
    }
  }

  @EventListener
  void onEmployeecontractChanged(EmployeecontractChangedEvent event) {
    generateMissingStandardOrders(event.getEmployeecontractId());
  }

  public Duration getTotalDuration(long employeeorderId) {
    var command = GetTimereportMinutesCommandEvent.builder()
        .orderType(EMPLOYEE)
        .orderIds(List.of(employeeorderId))
        .build();
    commandPublisher.publish(command);
    return command.getResult().getOrDefault(employeeorderId, Duration.ZERO);
  }

  private void adjustValidity(long employeeorderId, LocalDateRange newValidity) {
    var employeeorder = employeeorderDAO.getEmployeeorderById(employeeorderId);
    var existingValidity = employeeorder.getValidity();
    var resultingValidity = existingValidity.intersection(newValidity);
    var newFrom = resultingValidity.getFrom();
    var newUntil = resultingValidity.getUntil();
    createOrUpdate(employeeorder, newFrom, newUntil);
  }

  // TODO improve method arguments to reflect all details of an employee order
  private void createOrUpdate(Employeeorder employeeorder, LocalDate from, LocalDate until) {
    employeeorder.setFromDate(from);
    employeeorder.setUntilDate(until);

    if(!employeeorder.isNew()) {
      EmployeeorderUpdateEvent event = new EmployeeorderUpdateEvent(employeeorder);
      try {
        eventPublisher.publishEvent(event);
      } catch(VetoedException e) {
        // adding context to the veto to make it easier to understand the complete picture
        var allMessages = new ArrayList<ServiceFeedbackMessage>();
        allMessages.add(error(
            ErrorCode.EO_UPDATE_GOT_VETO,
            employeeorder.getSuborder().getCompleteOrderSign(),
            employeeorder.getEmployeecontract().getEmployee().getSign()
        ));
        allMessages.addAll(e.getMessages());
        event.veto(allMessages);
      }
    }

    employeeorderRepository.save(employeeorder);
  }

}
