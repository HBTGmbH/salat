package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.web.action.LoginRequiredAction;

/**
 * action class for showing all employee orders
 * 
 * @author oda
 *
 */
public class ShowEmployeeorderAction extends LoginRequiredAction {

	
	private EmployeeorderDAO employeeorderDAO;

	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}


	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {

		request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeorders());			
		
		if (request.getParameter("task") != null) {
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				// back to main menu
				return mapping.findForward("backtomenu");
			} else {
				// forward to show employee orders jsp
				return mapping.findForward("success");
			}
		} else {	
			// forward to show employee orders jsp
			return mapping.findForward("success");
		}
	}

}
