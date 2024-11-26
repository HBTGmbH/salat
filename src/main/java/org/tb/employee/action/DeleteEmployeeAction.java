package org.tb.employee.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.exception.ErrorCodeException;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;

/**
 * action class for deleting an employee
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class DeleteEmployeeAction extends LoginRequiredAction<ActionForm> {

    private final EmployeeService employeeService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

        if ((GenericValidator.isBlankOrNull(request.getParameter("emId"))) ||
                (!GenericValidator.isLong(request.getParameter("emId"))))
            return mapping.getInputForward();

        long emId = Long.parseLong(request.getParameter("emId"));
        Employee em = employeeService.getEmployeeById(emId);
        if (em == null) {
            return mapping.getInputForward();
        }

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (Objects.equals(em.getId(), loginEmployee.getId())) {
            ActionMessages errors = new ActionMessages();
            errors.add(null, new ActionMessage("form.employee.error.delete.isloginemployee"));
            saveErrors(request, errors);
            return mapping.getInputForward();
        }

        try {
            employeeService.deleteEmployeeById(emId);
        } catch(ErrorCodeException e) {
            addToErrors(request, e);
            return mapping.getInputForward();
        }

        String filter = null;

        if (request.getSession().getAttribute("employeeFilter") != null) {
            filter = (String) request.getSession().getAttribute("employeeFilter");
        }

        request.getSession().setAttribute("employees", employeeService.getEmployeesByFilter(filter));

        // set current employee back to loginEmployee to make sure that current employee is not the
        // one just deleted...
        request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());

        // back to employee display jsp
        return mapping.getInputForward();
    }

}
