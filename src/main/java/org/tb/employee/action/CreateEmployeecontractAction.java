package org.tb.employee.action;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;

/**
 * action class for creating a new employee contract
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class CreateEmployeecontractAction extends LoginRequiredAction<AddEmployeeContractForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeDAO employeeDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeContractForm employeeContractForm, HttpServletRequest request, HttpServletResponse response) {
        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        // get lists of existing employees and employee contracts
        List<Employee> employees = employeeDAO.getEmployees();
        request.getSession().setAttribute("employees", employees);

        List<Employee> employeesWithContracts = employeeDAO.getEmployeesWithValidContracts().stream()
                .filter(e -> !e.getLastname().startsWith("z_"))
                .toList();
        request.getSession().setAttribute("empWithCont", employeesWithContracts);

        List<Employeecontract> employeecontracts = employeecontractDAO.getEmployeeContracts();

        if ((employees == null) || (employees.size() <= 0)) {
            request.setAttribute("errorMessage",
                    "No employees found - please call system administrator.");
            return mapping.findForward("error");
        }

        // set relevant attributes
        request.getSession().setAttribute("employeecontracts", employeecontracts);
//		request.getSession().setAttribute("employees", employees);

        Long employeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
        if (employeeId == null || employeeId == 0 || employeeId == -1) {
            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            employeeId = loginEmployee.getId();
        }
        String currentEmployeeName = employeeDAO.getEmployeeById(employeeId).getName();
        request.getSession().setAttribute("currentEmployee", currentEmployeeName);

        // set context
        request.getSession().setAttribute("employeeContractContext", "create");

        // reset/init form entries
        employeeContractForm.reset(mapping, request);

        // use employee from overview filter
        Long filterEmployeeId = (Long) request.getSession().getAttribute("employeeContractEmployeeId");
        if (filterEmployeeId == null) {
            filterEmployeeId = -1L;
        }
        employeeContractForm.setEmployee(filterEmployeeId);

        // make sure, no ecId still exists in session
        request.getSession().removeAttribute("ecId");

        // forward to form jsp
        return mapping.findForward("success");
    }

}
