package org.tb.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parent action class for the actions of an employee who is correctly logged in.
 * Child action classes will implement method 'executeAuthenticated'.
 * 
 * @author oda
 *
 */
public abstract class LoginRequiredAction extends Action {
	private static final Logger LOG = LoggerFactory.getLogger(LoginRequiredAction.class);

	@Override
	public final ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
		if(request.getSession().getAttribute("errors") != null) {
			request.getSession().removeAttribute("errors");
		}
		if(request.getSession().getAttribute("loginEmployee") != null) {
	    	LOG.trace("entering {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
	    	try {
	    		return executeAuthenticated(mapping, form, request, response);
	    	} finally {
		    	LOG.trace("leaving {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
	    	}
		} else {
			return mapping.findForward("login");
		}
	}
	
	/**
	 * To be implemented by child classes.
	 * 
	 * @param mapping
	 * @param form
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	protected abstract ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception;
	
}
