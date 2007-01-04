package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.web.action.LoginRequiredAction;

/**
 * action class for showing all employee contracts
 * 
 * @author oda
 *
 */
public class ShowEmployeecontractAction extends LoginRequiredAction {

	
	private EmployeecontractDAO employeecontractDAO;

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}



	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {

		request.getSession().setAttribute("employeecontracts", employeecontractDAO.getEmployeeContracts());			
		
		if (request.getParameter("task") != null) {
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				// back to main menu
				return mapping.findForward("backtomenu");
			} else {
				// forward to show employee contracts jsp
				return mapping.findForward("success");
			}
		} else {						
			// forward to show employee contracts jsp
			return mapping.findForward("success");
		}
	}

}
