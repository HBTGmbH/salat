package org.tb.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * Action class for the logout of an employee
 * 
 * @author oda
 *
 */
public class LogoutEmployeeAction extends LoginRequiredAction {

	@Override
	protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
		request.getSession().invalidate();
		request.getSession().removeAttribute("currentEmployee");
		request.getSession().removeAttribute("currentOrder");
		request.getSession().removeAttribute("employees");
		request.getSession().removeAttribute("currentEmployee");
		request.getSession().removeAttribute("report");
		return mapping.findForward("success");
	}

}
