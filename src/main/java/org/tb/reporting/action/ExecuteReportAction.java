package org.tb.reporting.action;

import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.reporting.service.ReportingService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExecuteReportAction extends LoginRequiredAction<ExecuteReportForm> {

    private final ReportingService reportingService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, ExecuteReportForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        var reportDefinition = reportingService.getReportDefinition(form.getReportId());

        if("setParameters".equals(request.getParameter("task"))) {
            var reportResult = reportingService.execute(form.getReportId(), getParameterMap(form));
            request.getSession().setAttribute("report", reportDefinition);
            request.getSession().setAttribute("reportParameters", nonEmpty(form.getParameters()));
            request.getSession().setAttribute("reportResult", reportResult);
            return mapping.findForward("showReportResult");
        } else if("export".equals(request.getParameter("task"))) {
            // TODO task export
            return mapping.findForward("showReportResult");
        } else {
            if(reportDefinition.getSql().indexOf(':') >= 0) {
                // show parameters dialog
                request.getSession().setAttribute("report", reportDefinition);
                return mapping.findForward("showReportParameters");
            } else {
                var reportResult = reportingService.execute(form.getReportId(), new HashMap<>());
                request.getSession().setAttribute("report", reportDefinition);
                request.getSession().setAttribute("reportResult", reportResult);
                return mapping.findForward("showReportResult");
            }
        }

    }

    private List<ExecuteReportForm.ReportParameter> nonEmpty(List<ExecuteReportForm.ReportParameter> parameters) {
        return parameters.stream().filter(p -> p.getName() != null && !p.getName().isBlank()).toList();
    }

    private Map<String, Object> getParameterMap(ExecuteReportForm form) {
        Map<String, Object> result = new HashMap<>();
        for (ExecuteReportForm.ReportParameter parameter : nonEmpty(form.getParameters())) {
            switch(parameter.getType()) {
                case "string" -> result.put(parameter.getName(), parameter.getValue());
                case "date" -> result.put(parameter.getName(), DateUtils.parse(parameter.getValue()));
                case "number" -> result.put(parameter.getName(), parameter.getValue());
            }
        }
        return result;
    }

}
