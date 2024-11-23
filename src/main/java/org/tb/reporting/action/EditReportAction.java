package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.reporting.auth.ReportAuthorization;
import org.tb.reporting.service.ReportingService;

@Component
@RequiredArgsConstructor
public class EditReportAction extends LoginRequiredAction<CreateEditDeleteReportForm> {

    private final ReportingService reportingService;
    private final ReportAuthorization reportAuthorization;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, CreateEditDeleteReportForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        form.setMode("edit");
        var reportDefinition = reportingService.getReportDefinition(form.getReportId());
        form.setName(reportDefinition.getName());
        form.setSql(reportDefinition.getSql());
        boolean authorizedToStore = reportAuthorization.isAuthorized(reportDefinition, AccessLevel.WRITE);
        request.setAttribute("createEditReport_authorizedToStore", authorizedToStore);
        return mapping.findForward("success");
    }

}
