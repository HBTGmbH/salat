package org.tb.reporting.controller;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Set.of;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.tb.reporting.auth.ReportAuthorization;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportParameter;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.service.ExcelExportService;
import org.tb.reporting.service.ReportingService;

@Slf4j
@Controller
@RequestMapping("/reporting/reports")
@RequiredArgsConstructor
@SessionAttributes("reportResult")
public class ReportingController {

  private final ReportingService reportingService;
  private final ReportAuthorization reportAuthorization;
  private final ExcelExportService excelExportService;

  @GetMapping
  public String list(Model model, SessionStatus status) {
    status.setComplete(); // remove old report result

    var reports = reportingService.getReportDefinitions();
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
    return "reporting/reports-list";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/create")
  public String createForm(Model model) {
    model.addAttribute("pageTitle", "Create Report");
    model.addAttribute("report", new ReportForm());
    model.addAttribute("isEdit", false);
    return "reporting/report-form";
  }

  @GetMapping("/edit")
  @PreAuthorize("hasRole('MANAGER')")
  public String editForm(@RequestParam("id") Long id, Model model) {
    var rd = reportingService.getReportDefinition(id);
    var form = new ReportForm();
    form.setId(rd.getId());
    form.setName(rd.getName());
    form.setSql(rd.getSql());
    model.addAttribute("pageTitle", "Edit Report");
    model.addAttribute("report", form);
    model.addAttribute("isEdit", true);
    model.addAttribute("reportAuthorizations", reportAuthorization.getAuthorizations(rd));
    return "reporting/report-form";
  }

  @PostMapping("/store")
  @PreAuthorize("hasRole('MANAGER')")
  public String store(@ModelAttribute("report") ReportForm form,
                      BindingResult bindingResult,
                      Model model,
                      RedirectAttributes redirectAttributes) {

    if (form.getName() == null || form.getName().isBlank()) {
      bindingResult.rejectValue("name", "error.name", "Name is required");
    }
    if (form.getSql() == null || form.getSql().isBlank()) {
      bindingResult.rejectValue("sql", "error.sql", "SQL is required");
    }

    if (bindingResult.hasErrors()) {
      model.addAttribute("pageTitle", form.getId() != null ? "Edit Report" : "Create Report");
      model.addAttribute("isEdit", form.getId() != null);
      return "reporting/report-form";
    }

    if (form.getId() == null) {
      reportingService.create(form.getName(), form.getSql());
      redirectAttributes.addFlashAttribute("toastSuccess", "Report created successfully");
    } else {
      reportingService.update(form.getId(), form.getName(), form.getSql());
      redirectAttributes.addFlashAttribute("toastSuccess", "Report updated successfully");
    }

    return "redirect:/reporting/reports";
  }

  @PostMapping("/delete")
  @PreAuthorize("hasRole('MANAGER')")
  public String delete(@RequestParam("id") Long id, RedirectAttributes redirectAttributes) {
    reportingService.deleteReportDefinition(id);
    redirectAttributes.addFlashAttribute("toastSuccess", "Report deleted successfully");
    return "redirect:/reporting/reports";
  }

  @GetMapping("/execute")
  public String execute(@RequestParam("id") Long id,
                        @RequestParam Map<String, String> allParams,
                        Model model,
                        SessionStatus status) {
    var reportDefinition = reportingService.getReportDefinition(id);

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
      return "reporting/report-parameters";
    } else {
      ReportResult reportResult = reportingService.execute(id, parametersFromRequest);
      // Ergebnis in HTTP-Session ablegen, damit andere Endpunkte (z.B. Export) darauf zugreifen können
      model.addAttribute("pageTitle", "Report Result");
      model.addAttribute("report", reportDefinition);
      model.addAttribute("reportResult", reportResult);
      model.addAttribute("params", parametersFromRequest);
      return "reporting/report-result";
    }
  }

  @PostMapping("/execute")
  public String executeWithForm(@ModelAttribute("execute") ExecuteForm form) {
    String queryParams = renderQueryParams(form.getParameters());
    return "redirect:/reporting/reports/execute?id=" + form.getReportId() + queryParams;
  }

  @PostMapping("/export")
  public void export(@RequestParam("id") Long id,
                     @ModelAttribute("reportResult") ReportResult reportResult,
                     HttpServletResponse response) throws IOException {
    var reportDefinition = reportingService.getReportDefinition(id);
    var bytes = excelExportService.exportToExcel(reportResult);
    response.setHeader("Content-disposition", "attachment; filename=" + createFileName(reportDefinition));
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

  private static String createFileName(ReportDefinition reportDefinition) {
    var fileName = "report-" + reportDefinition.getName() + ".xlsx";
    return fileName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
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

    private List<ReportParameter> parameters = List.of(
        new ReportParameter(), new ReportParameter(), new ReportParameter(), new ReportParameter(), new ReportParameter(),
        new ReportParameter(), new ReportParameter(), new ReportParameter(), new ReportParameter(), new ReportParameter()
    );

    public void initParameters(List<ReportParameter> preset, java.util.Set<String> missingParameterNames) {
      // ensure size 5 and prefill missing params with their names
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

      while (list.size() < parameters.size()) list.add(new ReportParameter());
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
