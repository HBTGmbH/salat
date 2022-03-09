package org.tb.employee.action;

import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;

/**
 * action class for deleting an employee
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class DeleteEmployeeAction extends LoginRequiredAction<ActionForm> {

    private final EmployeeDAO employeeDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

        if ((GenericValidator.isBlankOrNull(request.getParameter("emId"))) ||
                (!GenericValidator.isLong(request.getParameter("emId"))))
            return mapping.getInputForward();

        ActionMessages errors = new ActionMessages();
        long emId = Long.parseLong(request.getParameter("emId"));
        Employee em = employeeDAO.getEmployeeById(emId);
        if (em == null) {
            return mapping.getInputForward();
        }

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (Objects.equals(em.getId(), loginEmployee.getId())) {
            errors.add(null, new ActionMessage("form.employee.error.delete.isloginemployee"));
            saveErrors(request, errors);
            return mapping.getInputForward();
        }

        boolean deleted = employeeDAO.deleteEmployeeById(emId);

        if (!deleted) {
            errors.add(null, new ActionMessage("form.employee.error.hasemployeecontract"));
        }

        saveErrors(request, errors);

        String filter = null;

        if (request.getSession().getAttribute("employeeFilter") != null) {
            filter = (String) request.getSession().getAttribute("employeeFilter");
        }

        request.getSession().setAttribute("employees", employeeDAO.getEmployeesByFilter(filter));

        // set current employee back to loginEmployee to make sure that current employee is not the
        // one just deleted...
        request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());

        // back to employee display jsp
        return mapping.getInputForward();
    }

}
