package org.tb.dailyreport.viewhelper;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

/**
 * Helper class for employee handling which does not directly deal with persistence
 *
 * @author oda
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EmployeeViewHelper {

  private final EmployeeService employeeService;
  private final EmployeecontractService employeecontractService;
  private final AuthorizedEmployee authorizedEmployee;

  /**
   * finds current employee and corresponding contract
   */
  public Employeecontract getAndInitCurrentEmployee(HttpSession session) {

    var currentEmployeeContract = (Employeecontract) session.getAttribute("currentEmployeeContract");
    var loginEmployeeContract = employeecontractService.getCurrentContract(authorizedEmployee.getEmployeeId())
        .orElse(null);
    var employeeOptionList = employeeService.getAllEmployees();
    var employeeWithContractList = employeeService.getEmployeesWithContracts();
    var employeeContracts = employeecontractService.getViewableEmployeeContractsValidAt(today());

    if (currentEmployeeContract == null) {
      currentEmployeeContract = loginEmployeeContract;
    }

    if (currentEmployeeContract != null) {
      session.setAttribute("currentEmployee", currentEmployeeContract.getEmployee().getName());
      session.setAttribute("currentEmployeeId", currentEmployeeContract.getEmployee().getId());
    }

    session.setAttribute("employeeswithcontract", employeeWithContractList);
    session.setAttribute("employees", employeeOptionList);
    session.setAttribute("employeecontracts", employeeContracts);
    session.setAttribute("currentOrder", "ALL ORDERS");

    return currentEmployeeContract;
  }

}
