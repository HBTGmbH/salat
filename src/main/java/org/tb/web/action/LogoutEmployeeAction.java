package org.tb.web.action;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Action class for the logout of an employee
 *
 * @author oda
 */
public class LogoutEmployeeAction extends LoginRequiredAction {

    private EmployeeDAO employeeDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (loginEmployee != null) {
            employeeDAO.save(loginEmployee, loginEmployee);
        }

        request.getSession().invalidate();
        request.getSession().removeAttribute("currentEmployee");
        request.getSession().removeAttribute("currentOrder");
        request.getSession().removeAttribute("employees");
        request.getSession().removeAttribute("currentEmployee");
        request.getSession().removeAttribute("report");
        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
