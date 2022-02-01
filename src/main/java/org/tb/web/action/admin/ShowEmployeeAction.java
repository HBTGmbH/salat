package org.tb.web.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.persistence.EmployeeDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowEmployeeForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * action class for showing all employees
 *
 * @author oda
 */
public class ShowEmployeeAction extends LoginRequiredAction<ShowEmployeeForm> {

    private EmployeeDAO employeeDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

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

        request.getSession().setAttribute("employees", employeeDAO.getEmployeesByFilter(filter));

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
