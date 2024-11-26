package org.tb.order.service;

import static org.tb.common.exception.ServiceFeedbackMessage.error;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.DateRange;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.VetoedException;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.event.EmployeecontractDeleteEvent;
import org.tb.employee.event.EmployeecontractUpdateEvent;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.persistence.VacationDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.event.EmployeeorderDeleteEvent;
import org.tb.order.event.EmployeeorderUpdateEvent;
import org.tb.order.event.SuborderDeleteEvent;
import org.tb.order.event.SuborderUpdateEvent;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.EmployeeorderRepository;
import org.tb.order.persistence.SuborderDAO;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeorderService {

  private final ApplicationEventPublisher eventPublisher;
  private final EmployeecontractDAO employeecontractDAO;
  private final EmployeeorderDAO employeeorderDAO;
  private final SuborderDAO suborderDAO;
  private final VacationDAO vacationDAO;
  private final EmployeeorderRepository employeeorderRepository;

  public void create(Employeeorder employeeorder) {
    createOrUpdate(employeeorder, employeeorder.getFromDate(), employeeorder.getUntilDate());
  }

  public void update(Employeeorder employeeorder) {
    createOrUpdate(employeeorder, employeeorder.getFromDate(), employeeorder.getUntilDate());
  }

  public void generateMissingStandardOrders(long employeecontractId) {
    Employeecontract employeecontract = employeecontractDAO.getEmployeecontractById(employeecontractId);

    List<Suborder> standardSuborders = suborderDAO.getStandardSuborders();
    if (standardSuborders != null && !standardSuborders.isEmpty()) {
      // test if employeeorder exists
      for (Suborder suborder : standardSuborders) {

        LocalDate contractValidFrom = employeecontract.getValidFrom();
        LocalDate contractValidUntil = employeecontract.getValidUntil();
        LocalDate orderValidFrom = suborder.getFromDate();
        LocalDate orderValidUntil = suborder.getUntilDate();

        // calculate effective time period
        LocalDate effectiveFromDate = ObjectUtils.max(contractValidFrom, orderValidFrom);
        LocalDate effectiveUntilDate;
        if (contractValidUntil == null && orderValidUntil == null) {
          effectiveUntilDate = null;
        } else if (contractValidUntil == null) {
          effectiveUntilDate = orderValidUntil;
        } else if (orderValidUntil == null) {
          effectiveUntilDate = contractValidUntil;
        } else {
          effectiveUntilDate = ObjectUtils.min(contractValidUntil, orderValidUntil);
        }

        // check if effective validity has at least a single day - otherwise creation makes no sense - skip it!
        if(effectiveUntilDate != null && effectiveFromDate.isAfter(effectiveUntilDate)) {
          continue;
        }

        // check if effective validity is not in the past (before date of accepted time reports) - else SKIP IT!!!
        var acceptanceDate = employeecontract.getReportAcceptanceDate();
        if(effectiveUntilDate != null && acceptanceDate != null && effectiveUntilDate.isAfter(acceptanceDate)) {
          continue;
        }

        boolean employeeorderPresent = employeeorderDAO.countEmployeeorders(employeecontract.getId(), suborder.getId()) > 0;
        if (!employeeorderPresent) {

          // skip vacation orders that do not match the contract
          if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)) {
            int vacationOrderYear = Year.parse(suborder.getSign()).getValue();
            if(vacationOrderYear < contractValidFrom.getYear()) {
              continue; // skip creation
            }
          }

          Employeeorder employeeorder = new Employeeorder();
          employeeorder.setFromDate(effectiveFromDate);
          employeeorder.setUntilDate(effectiveUntilDate);
          employeeorder.setEmployeecontract(employeecontract);
          employeeorder.setSign(" ");
          employeeorder.setSuborder(suborder);

          // calculate effective vacation entitlement and set budget accordingly
          if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)) {
            int vacationOrderYear = Year.parse(suborder.getSign()).getValue();
            var vacation = employeecontract.getVacation(vacationOrderYear);
            if(vacation.isEmpty()) {
              vacationDAO.addNewVacation(employeecontract, vacationOrderYear, employeecontract.getVacationEntitlement());
            }
            var vacationBudget = employeecontract.getEffectiveVacationEntitlement(vacationOrderYear); // calculate real entitlement
            employeeorder.setDebithours(vacationBudget);
            employeeorder.setDebithoursunit(GlobalConstants.DEBITHOURS_UNIT_TOTALTIME);
          }

          createOrUpdate(employeeorder, effectiveFromDate, effectiveUntilDate);
          log.info(
              "Created standard order for order {} and employee {}.",
              suborder.getCompleteOrderSign(),
              employeecontract.getEmployee().getSign()
          );
        }
      }
    }
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
        adjustValidity(employeeorder.getId(), newValidity);
      } else {
        deleteEmployeeorderById(employeeorder.getId());
      }
    }
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

  private void adjustValidity(long employeeorderId, DateRange newValidity) {
    var employeeorder = employeeorderDAO.getEmployeeorderById(employeeorderId);
    var existingValidity = employeeorder.getValidity();
    var resultingValidity = existingValidity.intersection(newValidity);
    var newFrom = existingValidity.isInfiniteFrom() ? null : resultingValidity.getFrom();
    var newUntil = existingValidity.isInfiniteUntil() ? null : resultingValidity.getUntil();
    createOrUpdate(employeeorder, newFrom, newUntil);
  }

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
      ));allMessages.addAll(e.getMessages());
      event.veto(allMessages);
    }
    employeeorderRepository.deleteById(employeeOrderId);
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

  public List<Employeeorder> getEmployeeOrdersBySuborderId(Long suborderId) {
    return employeeorderDAO.getEmployeeOrdersBySuborderId(suborderId);
  }

  public Employeeorder getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(long employeecontractId,
      long suborderId, LocalDate date) {
    return employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(employeecontractId, suborderId, date);
  }
}
