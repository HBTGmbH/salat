package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.persistence.EmployeeDAO;
import org.tb.web.action.LoginRequiredAction;

/**
 * action class for showing all employees
 * 
 * @author oda
 *
 */
public class ShowEmployeeAction extends LoginRequiredAction {

	
	private EmployeeDAO employeeDAO;

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {

		request.getSession().setAttribute("employees", employeeDAO.getEmployees());			
		
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
