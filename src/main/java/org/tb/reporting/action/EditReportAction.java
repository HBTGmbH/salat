package org.tb.reporting.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.reporting.service.ReportingService;

@Component
@RequiredArgsConstructor
public class EditReportAction extends LoginRequiredAction<CreateEditDeleteReportForm> {

    private final ReportingService reportingService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, CreateEditDeleteReportForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        form.setMode("edit");
        var reportDefinition = reportingService.getReportDefinition(form.getReportId());
        form.setName(reportDefinition.getName());
        form.setSql(reportDefinition.getSql());
        return mapping.findForward("success");
    }

}
