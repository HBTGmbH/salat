package org.tb.web.action.admin;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.persistence.EmployeeAlreadyExistsException;
import org.tb.persistence.EmployeeDAO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * action class for adding/registering an employee in the system
 *
 * @author oda
 */
public class AddEmployeeAction extends Action {

    private EmployeeDAO employeeDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        try {
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            boolean error = false;
            if (username == null) {
                request.setAttribute("errorMessage", "username angeben");
                error = true;
            } else if (password == null) {
                request.setAttribute("errorMessage", "password angeben");
                error = true;
            }
            if (error) return mapping.findForward("error");
            employeeDAO.registerEmployee(username, password);
            return mapping.findForward("success");
        } catch (EmployeeAlreadyExistsException e) {
            request.setAttribute("errorMessage", e.getClass().getName() + ": " + e.getMessage());
            return mapping.findForward("error");
        }
    }

}
