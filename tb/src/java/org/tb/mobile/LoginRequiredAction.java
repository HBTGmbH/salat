package org.tb.mobile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public abstract class LoginRequiredAction extends Action {
    
    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Long employeeId = (Long) request.getSession().getAttribute("employeeId");
        if (employeeId == null) {
            return mapping.findForward("loginRequiredError");
        }
        
        return doSecureExecute(mapping, form, request, response);
    }
    
    protected abstract ActionForward doSecureExecute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception;
    
}
