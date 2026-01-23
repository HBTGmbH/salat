package org.tb.reporting.controller;

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Set.of;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.AccessLevel;
import org.tb.common.util.DateTimeUtils;
import org.tb.reporting.auth.ReportAuthorization;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportParameter;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.service.ExcelExportService;
import org.tb.reporting.service.ReportService;

@Slf4j
@Controller
@RequestMapping("/reporting/reports")
@RequiredArgsConstructor
@SessionAttributes("reportResult")
public class ReportController {

  private final ReportService reportService;
  private final ReportAuthorization reportAuthorization;
  private final ExcelExportService excelExportService;

  @GetMapping
  public String list(@RequestParam(value = "filter", required = false) String filter, Model model, SessionStatus status) {
    status.setComplete(); // remove old report result

    var reports = reportService.getReportDefinitionsByFilter(filter);
    Map<Long, Boolean> mayEdit = new HashMap<>();
    Map<Long, Boolean> mayDelete = new HashMap<>();
    for (ReportDefinition r : reports) {
      mayEdit.put(r.getId(), reportAuthorization.isAuthorized(r, AccessLevel.WRITE));
      mayDelete.put(r.getId(), reportAuthorization.isAuthorized(r, AccessLevel.DELETE));
    }
    model.addAttribute("pageTitle", "Reports");
    model.addAttribute("reports", reports);
    model.addAttribute("mayEdit", mayEdit);
    model.addAttribute("mayDelete", mayDelete);
    model.addAttribute("filter", filter);
    return "reporting/reports-list";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/create")
  public String createForm(@RequestParam(value = "filter", required = false) String filter, Model model) {
    model.addAttribute("pageTitle", "Create Report");
    model.addAttribute("report", new ReportForm());
    model.addAttribute("isEdit", false);
    model.addAttribute("filter", filter);
    return "reporting/report-form";
  }

  @GetMapping("/edit")
  @PreAuthorize("hasRole('MANAGER')")
  public String editForm(@RequestParam("id") Long id,
                         @RequestParam(value = "filter", required = false) String filter,
                         Model model) {
    var rd = reportService.getReportDefinition(id);
    var form = new ReportForm();
    form.setId(rd.getId());
    form.setName(rd.getName());
    form.setSql(rd.getSql());
    model.addAttribute("pageTitle", "Edit Report");
    model.addAttribute("report", form);
    model.addAttribute("isEdit", true);
    model.addAttribute("reportAuthorizations", reportAuthorization.getAuthorizations(rd));
    model.addAttribute("filter", filter);
    return "reporting/report-form";
  }

  @PostMapping("/store")
  @PreAuthorize("hasRole('MANAGER')")
  public String store(@ModelAttribute("report") ReportForm form,
                      BindingResult bindingResult,
                      Model model,
                      RedirectAttributes redirectAttributes,
                      @RequestParam(value = "filter", required = false) String filter) {

    if (form.getName() == null || form.getName().isBlank()) {
      bindingResult.rejectValue("name", "error.name", "Name is required");
    }
    if (form.getSql() == null || form.getSql().isBlank()) {
      bindingResult.rejectValue("sql", "error.sql", "SQL is required");
    }

    if (bindingResult.hasErrors()) {
      model.addAttribute("pageTitle", form.getId() != null ? "Edit Report" : "Create Report");
      model.addAttribute("isEdit", form.getId() != null);
      model.addAttribute("filter", filter);
      return "reporting/report-form";
    }

    if (form.getId() == null) {
      reportService.create(form.getName(), form.getSql());
      redirectAttributes.addFlashAttribute("toastSuccess", "Report created successfully");
    } else {
      reportService.update(form.getId(), form.getName(), form.getSql());
      redirectAttributes.addFlashAttribute("toastSuccess", "Report updated successfully");
    }

    return filter == null || filter.isBlank()
        ? "redirect:/reporting/reports"
        : "redirect:/reporting/reports?filter=" + filter;
  }

  @PostMapping("/delete")
  @PreAuthorize("hasRole('MANAGER')")
  public String delete(@RequestParam("id") Long id,
                       @RequestParam(value = "filter", required = false) String filter,
                       RedirectAttributes redirectAttributes) {
    reportService.deleteReportDefinition(id);
    redirectAttributes.addFlashAttribute("toastSuccess", "Report deleted successfully");
    return filter == null || filter.isBlank()
        ? "redirect:/reporting/reports"
        : "redirect:/reporting/reports?filter=" + filter;
  }

  @GetMapping("/execute")
  public String execute(@RequestParam("id") Long id,
                        @RequestParam Map<String, String> allParams,
                        Model model,
                        SessionStatus status) {
    var reportDefinition = reportService.getReportDefinition(id);

    var parametersFromRequest = nonEmpty(getParametersFromRequest(allParams, reportDefinition.getSql()));
    var missingParameters = getMissingParameters(parametersFromRequest, reportDefinition.getSql());

    if (!missingParameters.isEmpty()) {
      status.setComplete();
      var paramForm = new ExecuteForm();
      paramForm.setReportId(id);
      paramForm.initParameters(parametersFromRequest, missingParameters);
      model.addAttribute("pageTitle", "Execute Report");
      model.addAttribute("report", reportDefinition);
      model.addAttribute("execute", paramForm);
      model.addAttribute("missingParameters", missingParameters);
      model.addAttribute("filter", allParams.get("filter"));
      return "reporting/report-parameters";
    } else {
      ReportResult reportResult = reportService.execute(id, parametersFromRequest);
      // Ergebnis in HTTP-Session ablegen, damit andere Endpunkte (z.B. Export) darauf zugreifen können
      model.addAttribute("pageTitle", "Report Result");
      model.addAttribute("report", reportDefinition);
      model.addAttribute("reportResult", reportResult);
      model.addAttribute("params", parametersFromRequest);
      model.addAttribute("filter", allParams.get("filter"));
      return "reporting/report-result";
    }
  }

  @PostMapping("/execute")
  public String executeWithForm(@ModelAttribute("execute") ExecuteForm form,
                                @RequestParam(value = "filter", required = false) String filter) {
    String queryParams = renderQueryParams(form.getParameters());
    String filterParam = (filter == null || filter.isBlank()) ? "" : "&filter=" + filter;
    return "redirect:/reporting/reports/execute?id=" + form.getReportId() + queryParams + filterParam;
  }

  @PostMapping("/export")
  public void export(@RequestParam("id") Long id,
                     @ModelAttribute("reportResult") ReportResult reportResult,
                     HttpServletResponse response) throws IOException {
    var reportDefinition = reportService.getReportDefinition(id);
    var bytes = excelExportService.exportToExcel(reportResult);
    var fileName = createFileName(reportDefinition, reportResult.getParameters());
    response.setHeader("Content-disposition", "attachment; filename=" + fileName);
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setContentLength(bytes.length);
    response.getOutputStream().write(bytes);
  }

  private static String renderQueryParams(List<ReportParameter> parameters) {
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

  @VisibleForTesting
  static String createFileName(ReportDefinition reportDefinition, List<ReportParameter> parameters) {
    var dateTime = DateTimeUtils.now().truncatedTo(SECONDS).toString();
    var fileName = "report-" + reportDefinition.getName() + "-" + toString(parameters) + "-" + dateTime + ".xlsx";
    return normalizeToFileName(fileName);
  }

  private static String normalizeToFileName(String fileName) {
    // may produce long ___ sequences
    System.out.println(fileName);
    var withoutSpecialChars = fileName.replaceAll("[^a-zA-Z0-9-_,\\.\\[\\]]", "_").trim();
    // reduce ___ sequences to _
    String result;
    String reduced = withoutSpecialChars;
    do {
      result = reduced;
      reduced = result.replace("__", "_");
    } while (reduced.length() != result.length());
    result = result.replace("-_", "-").replace("_-", "-").replace(",_", "-");
    return result;
  }

  private static String toString(List<ReportParameter> parameters) {
    return parameters.stream().map(ReportParameter::getValue).toList().toString();
  }

  static List<ReportParameter> nonEmpty(List<ReportParameter> parameters) {
    return parameters.stream().filter(p -> p.getName() != null && !p.getName().isBlank()).toList();
  }

  private static ReportParameter toReportParameter(String key, String value) {
    if (value.indexOf(',') > 0) {
      var parts = value.split(",", 2);
      return ReportParameter.builder().name(key).type(parts[0].trim()).value(parts[1].trim()).build();
    }
    return ReportParameter.builder().name(key).type("string").value(value.trim()).build();
  }

  static List<ReportParameter> getParametersFromRequest(Map<String, String> params, String query) {
    if (query == null) {
      return emptyList();
    }

    return params.entrySet().stream()
        .filter(e -> query.contains(":" + e.getKey()))
        .map(e -> toReportParameter(e.getKey(), e.getValue()))
        .toList();
  }

  static java.util.Set<String> getMissingParameters(List<ReportParameter> parameters, String query) {
    if (query == null){
      return emptySet();
    }
    var parameterNames = parameters.stream().map(ReportParameter::getName).toList();
    return Pattern.compile(":\\w+")
        .matcher(query)
        .results()
        .map(MatchResult::group)
        .map(qp -> qp.substring(1))
        .filter(not(parameterNames::contains))
        .collect(toSet());
  }

  // Form classes
  @Setter
  @Getter
  public static class ReportForm {
    private Long id;
    private String name;
    private String sql;

  }

  @Setter
  @Getter
  public static class ExecuteForm {

    private Long reportId;

    private List<ReportParameter> parameters = new ArrayList<>();

    public void initParameters(List<ReportParameter> preset, java.util.Set<String> missingParameterNames) {
      var list = new java.util.ArrayList<ReportParameter>();
      if (preset != null) list.addAll(preset);

      // add placeholders for missing parameter names not yet present
      var existingNames = list.stream()
          .map(ReportParameter::getName)
          .filter(n -> n != null && !n.isBlank())
          .collect(toSet());

      if (missingParameterNames != null) {
        for (String name : missingParameterNames) {
          if (!existingNames.contains(name)) {
            var p = new ReportParameter();
            p.setName(name);
            p.setType(getType(name));
            p.setValue(getValue(name));
            list.add(p);
          }
        }
      }

      this.parameters = list;
    }

    private String getType(String parameterName) {
      if(of("jahr", "monat", "year", "month").contains(parameterName.toLowerCase())) {
        return "number";
      }
      if(of("datum", "date", "from", "to", "von", "bis").contains(parameterName.toLowerCase())) {
        return "date";
      }
      return "string";
    }

    private String getValue(String parameterName) {
      if(of("jahr", "year").contains(parameterName.toLowerCase())) {
        return Year.now().toString();
      }
      if(of("monat", "month").contains(parameterName.toLowerCase())) {
        return Integer.toString(YearMonth.now().getMonthValue());
      }
      if(of("datum", "date", "from", "to", "von", "bis").contains(parameterName.toLowerCase())) {
        return LocalDate.now().toString();
      }
      return "";
    }

  }
}
