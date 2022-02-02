package org.tb.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.action.LoginRequiredAction;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Parent action class for the actions of an admin who is correctly logged in.
 * Child action classes will implement method 'executeAuthenticated'.
 *
 * @author oda
 */
public abstract class AdminRequiredAction<F extends ActionForm> extends LoginRequiredAction<F> {

    @Override
    protected final ActionForward executeAuthenticated(ActionMapping mapping, F form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (request.getSession().getAttribute("admin") != null) {
            return executeAdminAuthenticated(mapping, form, request, response);
        } else {
            return mapping.findForward("login");
        }
    }

    protected abstract ActionForward executeAdminAuthenticated(ActionMapping mapping, F form, HttpServletRequest request, HttpServletResponse response) throws Exception;

}
