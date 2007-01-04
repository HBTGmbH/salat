package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.persistence.CustomerorderDAO;
import org.tb.web.action.LoginRequiredAction;

/**
 * action class for showing all customer orders
 * 
 * @author oda
 *
 */
public class ShowCustomerorderAction extends LoginRequiredAction {

	
	private CustomerorderDAO customerorderDAO;

	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}


	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {

		request.getSession().setAttribute("customerorders", customerorderDAO.getCustomerorders());			
		
		if (request.getParameter("task") != null) {
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				// back to main menu
				return mapping.findForward("backtomenu");
			} else {
				// forward to show customer orders jsp
				return mapping.findForward("success");
			}
		} else {	
			// forward to show customer orders jsp
			return mapping.findForward("success");
		}
	}

}
