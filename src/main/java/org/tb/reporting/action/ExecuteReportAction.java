package org.tb.reporting.action;

import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.service.ReportingService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;

@Component
@RequiredArgsConstructor
public class ExecuteReportAction extends LoginRequiredAction<ExecuteReportForm> {

    private final ReportingService reportingService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, ExecuteReportForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        var reportDefinition = reportingService.getReportDefinition(form.getReportId());
        // TODO task export
        if(reportDefinition.getSql().indexOf(':') >= 0) {
            // show parameters dialog
            return mapping.findForward("showReportParameters");
        } else {
            var reportResult = reportingService.execute(form.getReportId(), new HashMap<>());
            request.getSession().setAttribute("report", reportDefinition);
            request.getSession().setAttribute("reportResult", reportResult);
            return mapping.findForward("showReportResult");
        }
    }

}
