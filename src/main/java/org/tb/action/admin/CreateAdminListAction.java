package org.tb.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Action class for creating a list of admins.
 * Currently not used.
 *
 * @author oda
 */
public class CreateAdminListAction extends AdminRequiredAction<ActionForm> {

    @Override
    protected ActionForward executeAdminAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        return mapping.findForward("success");
    }

}
