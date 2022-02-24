package org.tb.employee;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;

/**
 * action class for creating a new employee
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class CreateEmployeeAction extends LoginRequiredAction<AddEmployeeForm> {

    private final EmployeeDAO employeeDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeForm employeeForm, HttpServletRequest request, HttpServletResponse response) {
        // get list of existing employees
        List<Employee> employees = employeeDAO.getEmployees();

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
