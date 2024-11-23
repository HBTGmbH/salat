package org.tb.employee.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.LoginRequiredAction;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;

/**
 * action class for showing all employees
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class ShowEmployeeAction extends LoginRequiredAction<ShowEmployeeForm> {

    private final EmployeeService employeeService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        ShowEmployeeForm employeeForm, HttpServletRequest request,
        HttpServletResponse response) {
        String filter = null;

        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("refresh"))) {
            filter = employeeForm.getFilter();
            request.getSession().setAttribute("employeeFilter", filter);

        } else {
            if (request.getSession().getAttribute("employeeFilter") != null) {
                filter = (String) request.getSession().getAttribute("employeeFilter");
                employeeForm.setFilter(filter);
            }
        }

        var employees = employeeService.getEmployeesByFilter(filter);
        employees.sort(Comparator.comparing(Employee::getLastname).thenComparing(Employee::getFirstname));
        request.getSession().setAttribute("employees", employees);

        if (request.getParameter("task") != null) {
            if (request.getParameter("task").equalsIgnoreCase("back")) {
                // back to main menu
                return mapping.findForward("backtomenu");
            } else {
                // forward to show employees jsp
                return mapping.findForward("success");
            }
        } else {
            // forward to show employees jsp
            return mapping.findForward("success");
        }
    }

}
