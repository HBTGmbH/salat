package org.tb.reporting.controller;

import static java.util.Set.of;
import static java.util.stream.Collectors.toSet;
import static org.tb.reporting.controller.ReportingUiStateKeyContributor.REPORT_FILTER;

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.AccessLevel;
import org.tb.common.util.DateUtils;
import org.tb.common.viewhelper.FilterHintViewHelper;
import org.tb.reporting.auth.ReportAuthorization;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportParameter;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.service.ExcelExportService;
import org.tb.reporting.service.ReportFileNames;
import org.tb.reporting.service.ReportParameters;
import org.tb.reporting.service.ReportService;

@Slf4j
@Controller
@RequestMapping("/reporting/reports")
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;
  private final ReportAuthorization reportAuthorization;
  private final ExcelExportService excelExportService;
  private final FilterHintViewHelper filterHintViewHelper;

  @GetMapping
  public String list(@RequestParam(value = "fReportFilter", required = false) String fReportFilter, Model model) {
    var reports = reportService.getReportDefinitionsByFilter(fReportFilter);
    Map<Long, Boolean> mayEdit = new HashMap<>();
    Map<Long, Boolean> mayDelete = new HashMap<>();
    for (ReportDefinition r : reports) {
      mayEdit.put(r.getId(), reportAuthorization.isAuthorized(r, AccessLevel.WRITE));
      mayDelete.put(r.getId(), reportAuthorization.isAuthorized(r, AccessLevel.DELETE));
    }
    model.addAttribute("pageTitle", "Reports");
    model.addAttribute("section", "reports");
    model.addAttribute("subSection", "reports");
    model.addAttribute("sectionTitle", "Reports");
    model.addAttribute("reports", reports);
    model.addAttribute("mayEdit", mayEdit);
    model.addAttribute("mayDelete", mayDelete);
    model.addAttribute("fReportFilter", fReportFilter);
    return "reporting/reports-list";
  }

  @PreAuthorize("hasAnyRole('MANAGER','PEOPLE_LEAD')")
  @GetMapping("/create")
  public String createForm(Model model) {
    model.addAttribute("pageTitle", "Create Report");
    model.addAttribute("section", "reports");
    model.addAttribute("subSection", "reports");
    model.addAttribute("sectionTitle", "Reports");
    model.addAttribute("report", new ReportForm());
    model.addAttribute("isEdit", false);
    return "reporting/report-form";
  }

  @GetMapping("/edit")
  @PreAuthorize("hasAnyRole('MANAGER','PEOPLE_LEAD')")
  public String editForm(@RequestParam("id") Long id, Model model) {
    var rd = reportService.getReportDefinition(id);
    if(rd == null) throw new ErrorResponseException(HttpStatus.NOT_FOUND);
    var form = new ReportForm();
    form.setId(rd.getId());
    form.setName(rd.getName());
    form.setSql(rd.getSql());
    model.addAttribute("pageTitle", "Edit Report");
    model.addAttribute("section", "reports");
    model.addAttribute("subSection", "reports");
    model.addAttribute("sectionTitle", "Reports");
    model.addAttribute("report", form);
    model.addAttribute("isEdit", true);
    model.addAttribute("reportAuthorizations", reportAuthorization.getAuthorizations(rd));
    return "reporting/report-form";
  }

  @PostMapping("/store")
  @PreAuthorize("hasAnyRole('MANAGER','PEOPLE_LEAD')")
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
      model.addAttribute("section", "reports");
      model.addAttribute("subSection", "reports");
      model.addAttribute("sectionTitle", "Reports");
      model.addAttribute("isEdit", form.getId() != null);
      return "reporting/report-form";
    }

    // The filter stays as the user left it (ADR-0023); where it hides the saved report, the
    // message says so instead of the list silently not showing it.
    if (form.getId() == null) {
      reportService.create(form.getName(), form.getSql());
      filterHintViewHelper.addSuccess(redirectAttributes, "Report created successfully", REPORT_FILTER);
    } else {
      reportService.update(form.getId(), form.getName(), form.getSql());
      filterHintViewHelper.addSuccess(redirectAttributes, "Report updated successfully", REPORT_FILTER);
    }

    return "redirect:/reporting/reports";
  }

  @PostMapping("/delete")
  @PreAuthorize("hasAnyRole('MANAGER','PEOPLE_LEAD')")
  public String delete(@RequestParam("id") Long id, RedirectAttributes redirectAttributes) {
    reportService.deleteReportDefinition(id);
    redirectAttributes.addFlashAttribute("toastSuccess", "Report deleted successfully");
    return "redirect:/reporting/reports";
  }

  @GetMapping("/execute")
  public String execute(@RequestParam("id") Long id,
                        @RequestParam Map<String, String> allParams,
                        Model model) {
    var rd = reportService.getReportDefinition(id);
    if(rd == null) throw new ErrorResponseException(HttpStatus.NOT_FOUND);

    var parametersFromRequest = ReportParameters.nonEmpty(ReportParameters.fromRequest(allParams, rd.getSql()));
    var missingParameters = ReportParameters.missing(parametersFromRequest, rd.getSql());

    if (!missingParameters.isEmpty()) {
      var paramForm = new ExecuteForm();
      paramForm.setReportId(id);
      paramForm.initParameters(parametersFromRequest, missingParameters);
      model.addAttribute("pageTitle", "Execute Report");
      model.addAttribute("section", "reports");
      model.addAttribute("subSection", "reports");
      model.addAttribute("sectionTitle", "Reports");
      model.addAttribute("report", rd);
      model.addAttribute("execute", paramForm);
      model.addAttribute("missingParameters", missingParameters);
      return "reporting/report-parameters";
    } else {
      ReportResult reportResult = reportService.execute(id, parametersFromRequest);
      model.addAttribute("pageTitle", "Report Result");
      model.addAttribute("section", "reports");
      model.addAttribute("subSection", "reports");
      model.addAttribute("sectionTitle", "Reports");
      model.addAttribute("report", rd);
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
                     @RequestParam Map<String, String> allParams,
                     HttpServletResponse response) throws IOException {
    var rd = reportService.getReportDefinition(id);
    if(rd == null) throw new ErrorResponseException(HttpStatus.NOT_FOUND);
    var parameters = ReportParameters.nonEmpty(ReportParameters.fromRequest(allParams, rd.getSql()));
    var reportResult = reportService.execute(id, parameters);
    var bytes = excelExportService.exportToExcel(reportResult);
    var fileName = ReportFileNames.create(rd, reportResult.getParameters(), "xlsx");
    response.setHeader("Content-disposition", "attachment; filename=" + fileName);
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setContentLength(bytes.length);
    response.getOutputStream().write(bytes);
  }

  @VisibleForTesting
  static String renderQueryParams(List<ReportParameter> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return "";
    }
    var result = new StringBuilder();
    for (ReportParameter parameter : ReportParameters.nonEmpty(parameters)) {
      if (parameter.getValue() != null && !parameter.getValue().isBlank()) {
        result.append("&").append(parameter.getName()).append("=");
        if (!"string".equals(parameter.getType())) {
          result.append(parameter.getType()).append(",");
        }
        result.append(URLEncoder.encode(parameter.getValue(), StandardCharsets.UTF_8));
      }
    }
    return result.toString();
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
        return Year.from(DateUtils.today()).toString();
      }
      if(of("monat", "month").contains(parameterName.toLowerCase())) {
        return Integer.toString(YearMonth.from(DateUtils.today()).getMonthValue());
      }
      if(of("datum", "date", "from", "to", "von", "bis").contains(parameterName.toLowerCase())) {
        return DateUtils.today().toString();
      }
      return "";
    }

  }
}
