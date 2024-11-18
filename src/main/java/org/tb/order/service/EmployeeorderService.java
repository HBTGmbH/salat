package org.tb.order.service;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.GlobalConstants;
import org.tb.dailyreport.persistence.VacationDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeorderService {

  private final EmployeecontractDAO employeecontractDAO;
  private final EmployeeorderDAO employeeorderDAO;
  private final SuborderDAO suborderDAO;
  private final VacationDAO vacationDAO;

  public void generateMissingStandardOrders(long employeecontractId) {
    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(employeecontractId);

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

          employeeorderDAO.save(employeeorder);
          log.info(
              "Created standard order for order {} and employee {}.",
              suborder.getCompleteOrderSign(),
              employeecontract.getEmployee().getSign()
          );
        }
      }
    }
  }

  public void adjustValidity(Employeeorder employeeorder, LocalDate fromDate, LocalDate untilDate) {
    boolean changed = false;
    if (employeeorder.getFromDate().isBefore(fromDate)) {
      employeeorder.setFromDate(fromDate);
      changed = true;
    }
    if (employeeorder.getUntilDate() != null && employeeorder.getUntilDate().isBefore(fromDate)) {
      employeeorder.setUntilDate(fromDate);
      changed = true;
    }
    if (untilDate != null) {
      if (employeeorder.getFromDate().isAfter(untilDate)) {
        employeeorder.setFromDate(untilDate);
        changed = true;
      }
      if (employeeorder.getUntilDate() == null || employeeorder.getUntilDate().isAfter(untilDate)) {
        employeeorder.setUntilDate(untilDate);
        changed = true;
      }
    }
    if (changed) {
      employeeorderDAO.save(employeeorder);
    }
  }

  public Employeeorder getEmployeeorderForEmployeecontractValidAt(long employeecontractId, long suborderId, LocalDate validAt) {
    return employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(employeecontractId, suborderId, validAt);
  }

  public List<Employeeorder> getEmployeeordersForEmployeecontractAndValidAt(long employeecontractId, LocalDate validAt) {
    return employeeorderDAO.getEmployeeordersByEmployeeContractIdAndValidAt(employeecontractId, validAt);
  }
}
