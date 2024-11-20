package org.tb.employee.viewhelper;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

/**
 * Helper class for employee handling which does not directly deal with persistence
 *
 * @author oda
 */
public class EmployeeViewHelper {

    /**
     * finds current employee and corresponding contract
     */
    public Employeecontract getAndInitCurrentEmployee(
        HttpServletRequest request,
        EmployeeService employeeService,
        EmployeecontractService employeecontractService) {

        var currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        var loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");

        if (currentEmployeeContract == null) {
            currentEmployeeContract = loginEmployeeContract;
        }


        var employeeOptionList = employeeService.getAllEmployees();
        var employeeWithContractList = employeeService.getEmployeesWithContracts();
        var employeeContracts = employeecontractService.getViewableEmployeeContractsValidAt(today());

        request.getSession().setAttribute("employeeswithcontract", employeeWithContractList);
        request.getSession().setAttribute("employees", employeeOptionList);
        request.getSession().setAttribute("employeecontracts", employeeContracts);
        request.getSession().setAttribute("currentEmployee", currentEmployeeContract.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", currentEmployeeContract.getEmployee().getId());
        request.getSession().setAttribute("currentOrder", "ALL ORDERS");

        return currentEmployeeContract;
    }

}
