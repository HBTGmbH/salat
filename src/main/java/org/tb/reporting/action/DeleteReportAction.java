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
public class DeleteReportAction extends LoginRequiredAction<CreateEditDeleteReportForm> {

    private final ReportingService reportingService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, CreateEditDeleteReportForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        reportingService.deleteReportDefinition(form.getReportId());
        request.getSession().setAttribute("reportDescriptions", reportingService.getReportDefinitions());
        return mapping.findForward("success");
    }

}
