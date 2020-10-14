package org.tb.web.action.admin;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.*;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;
import org.tb.web.action.LoginRequiredAction;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * action class for deleting an employee
 *
 * @author oda
 */
public class DeleteEmployeeAction extends LoginRequiredAction {

    private EmployeeDAO employeeDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

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
        if (em.getId() == loginEmployee.getId()) {
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
