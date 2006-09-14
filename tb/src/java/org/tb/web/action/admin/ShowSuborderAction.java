package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;

/**
 * action class for showing all suborders
 * 
 * @author oda
 *
 */
public class ShowSuborderAction extends LoginRequiredAction {

	
	private SuborderDAO suborderDAO;

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}


	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {

		request.getSession().setAttribute("suborders", suborderDAO.getSuborders());			
		
		if (request.getParameter("task") != null) {
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				// back to main menu
				return mapping.findForward("backtomenu");
			} else {
				// forward to show suborders jsp
				return mapping.findForward("success");
			}
		} else {		
			// forward to show suborders jsp
			return mapping.findForward("success");
		}
	}

}
