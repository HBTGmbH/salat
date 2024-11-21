package org.tb.employee.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;

/**
 * action class for creating a new employee
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class CreateEmployeeAction extends LoginRequiredAction<AddEmployeeForm> {

    private final EmployeeService employeeService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeForm employeeForm, HttpServletRequest request, HttpServletResponse response) {
        // get list of existing employees
        List<Employee> employees = employeeService.getAllEmployees();

        // reset/init form entries
        employeeForm.reset(mapping, request);

        // set relevant attributes
        request.getSession().setAttribute("gender", "m");
        request.getSession().setAttribute("employeeStatus", "Employee");
        request.getSession().setAttribute("employees", employees);

        // make sure, no emId still exists in session
        request.getSession().removeAttribute("emId");

        // forward to form jsp
        return mapping.findForward("success");
    }

}
