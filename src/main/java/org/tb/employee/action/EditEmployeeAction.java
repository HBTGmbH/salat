package org.tb.employee.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;

/**
 * action class for editing an employee
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class EditEmployeeAction extends LoginRequiredAction<AddEmployeeForm> {

    private final EmployeeService employeeService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeForm employeeForm, HttpServletRequest request, HttpServletResponse response) {
        long emId = Long.parseLong(request.getParameter("emId"));
        Employee em = employeeService.getEmployeeById(emId);
        request.getSession().setAttribute("emId", em.getId());

        // fill the form with properties of employee to be edited
        setFormEntries(request, employeeForm, em);

        // forward to employee add/edit form
        return mapping.findForward("success");
    }

    /**
     * fills employee form with properties of given employee
     */
    private void setFormEntries(HttpServletRequest request, AddEmployeeForm employeeForm, Employee em) {
        employeeForm.setFirstname(em.getFirstname());
        employeeForm.setLastname(em.getLastname());
        employeeForm.setLoginname(em.getLoginname());
        employeeForm.setSign(em.getSign());
        employeeForm.setStatus(em.getStatus());
        employeeForm.setGender(Character.toString(em.getGender()));

        request.getSession().setAttribute("employeeStatus", em.getStatus());
        request.getSession().setAttribute("gender", Character.toString(em.getGender()));
    }

}
