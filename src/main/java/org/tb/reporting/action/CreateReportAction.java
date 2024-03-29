package org.tb.reporting.action;

import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class CreateReportAction extends LoginRequiredAction<CreateEditDeleteReportForm> {

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, CreateEditDeleteReportForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        form.setMode("create");
        request.setAttribute("createEditReport_authorizedToStore", true);
        return mapping.findForward("success");
    }

}
