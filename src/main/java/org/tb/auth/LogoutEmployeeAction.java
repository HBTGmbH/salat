package org.tb.auth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;

/**
 * Action class for the logout of an employee
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class LogoutEmployeeAction extends LoginRequiredAction<ActionForm> {

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        request.getSession().removeAttribute("currentEmployee");
        request.getSession().removeAttribute("currentOrder");
        request.getSession().removeAttribute("employees");
        request.getSession().removeAttribute("currentEmployee");
        request.getSession().removeAttribute("report");
        request.getSession().invalidate();
        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
