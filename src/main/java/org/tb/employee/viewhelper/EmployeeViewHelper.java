package org.tb.employee.viewhelper;

import jakarta.servlet.http.HttpServletRequest;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;

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
        EmployeeDAO employeeDAO,
        EmployeecontractDAO employeecontractDAO) {

        var currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        var loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");

        if (currentEmployeeContract == null) {
            currentEmployeeContract = loginEmployeeContract;
        }


        var employeeOptionList = employeeDAO.getEmployees();
        var employeeWithContractList = employeeDAO.getEmployeesWithContracts();
        var employeeContracts = employeecontractDAO.getViewableEmployeeContractsForAuthorizedUser(DateUtils.today());

        request.getSession().setAttribute("employeeswithcontract", employeeWithContractList);
        request.getSession().setAttribute("employees", employeeOptionList);
        request.getSession().setAttribute("employeecontracts", employeeContracts);
        request.getSession().setAttribute("currentEmployee", currentEmployeeContract.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", currentEmployeeContract.getEmployee().getId());
        request.getSession().setAttribute("currentOrder", "ALL ORDERS");

        return currentEmployeeContract;
    }

}
