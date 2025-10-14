package org.tb.reporting.action;

import static java.lang.String.join;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.reporting.action.ExecuteReportForm.ReportParameter;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.service.ExcelExportService;
import org.tb.reporting.service.ReportingService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteReportAction extends LoginRequiredAction<ExecuteReportForm> {

    private final ReportingService reportingService;
    private final ExcelExportService excelExportService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping, ExecuteReportForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        var reportDefinition = reportingService.getReportDefinition(form.getReportId());

        if ("setParameters".equals(request.getParameter("task"))) {
          var queryParams = renderQueryParams(form.getParameters());
          return new ActionForward("ExecuteReport?reportId=" + form.getReportId() + queryParams, true);
        } else if ("export".equals(request.getParameter("task"))) {
            var reportResult = (ReportResult) request.getSession().getAttribute("reportResult");
            try (ServletOutputStream out = response.getOutputStream()) {
                var bytes = excelExportService.exportToExcel(reportResult);
                response.setHeader("Content-disposition", "attachment; filename=" + createFileName(reportDefinition));
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setContentLength(bytes.length);
                out.write(bytes);
            }
            return RESPONSE_COMPLETED;
        } else {
            var parametersFromRequest = nonEmpty(getParametersFromRequest(request, reportDefinition.getSql()));
            var missingParameters = getMissingParameters(parametersFromRequest, reportDefinition.getSql());

            if (!missingParameters.isEmpty()) {
                // show parameters dialog if query parameters are missing
                form.initParameters(parametersFromRequest, missingParameters);
                request.getSession().setAttribute("report", reportDefinition);
                return mapping.findForward("showReportParameters");
            } else {
                var reportResult = reportingService.execute(form.getReportId(), getParameterMap(parametersFromRequest));
                request.getSession().setAttribute("report", reportDefinition);
                request.getSession().setAttribute("reportResult", reportResult);
                return mapping.findForward("showReportResult");
            }
        }

    }

  private String renderQueryParams(List<ReportParameter> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return "";
    }
    var result = new StringBuilder();
    for (ReportParameter parameter : nonEmpty(parameters)) {
      if (parameter.getValue() != null && !parameter.getValue().isBlank()) {
        result.append("&").append(parameter.getName()).append("=");
        if (!"string".equals(parameter.getType())) {
          result.append(parameter.getType()).append(",");
        }
        result.append(parameter.getValue());
      }
    }
    return result.toString();
  }

  private static String createFileName(ReportDefinition reportDefinition) {
        var fileName = "report-" + reportDefinition.getName() +
                       "-erzeugt-" + DateUtils.formatDateTime(DateUtils.now(), "dd-MM-yy-HHmm") +
                       ".xlsx";
        var sanitizedFileName = fileName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
        return sanitizedFileName;
    }

    private static List<ReportParameter> nonEmpty(List<ReportParameter> parameters) {
        return parameters.stream().filter(p -> p.getName() != null && !p.getName().isBlank()).toList();
    }

    private static Map<String, Object> getParameterMap(List<ReportParameter> parameters) {
        var result = new HashMap<String, Object>();
        for (ReportParameter parameter : nonEmpty(parameters)) {
            switch (parameter.getType()) {
                case "date" -> {
                    if(parameter.getValue().equals("TODAY") || parameter.getValue().equals("HEUTE")) {
                        result.put(parameter.getName(), today());
                    } else {
                        result.put(parameter.getName(), DateUtils.parse(parameter.getValue()));
                    }
                }
                default -> result.put(parameter.getName(), parameter.getValue());
            }
        }
        return result;
    }

    private static ReportParameter toReportParameter(String key, String value) {
        if (value.indexOf(',') > 0) {
            var parts = value.split(",", 2);
            return ReportParameter.builder().name(key).type(parts[0].trim()).value(parts[1].trim()).build();
        }
        return ReportParameter.builder().name(key).type("string").value(value.trim()).build();
    }

    static Set<String> getMissingParameters(List<ReportParameter> parameters, String query) {
        if (query == null){
            return emptySet();
        }

        var parameterNames = parameters.stream().map(ReportParameter::getName).toList();
        return Pattern.compile(":\\w+")
                .matcher(query)
                .results()
                .map(MatchResult::group)
                .map(queryParameter->queryParameter.substring(1))
                .filter(not(parameterNames::contains))
                .collect(toSet());
    }

    static List<ReportParameter> getParametersFromRequest(HttpServletRequest request, String query) {
        if (query == null){
            return emptyList();
        }

        return request.getParameterMap()
                .entrySet()
                .stream()
                .filter(parameter -> query.contains(":" + parameter.getKey()))
                .map(parameter -> toReportParameter(parameter.getKey(), join(",", parameter.getValue())))
                .toList();
    }

}
