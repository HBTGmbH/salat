package de.hbt.salat.reporting.controller;

import static java.util.stream.Collectors.toSet;
import static de.hbt.salat.reporting.controller.ReportingUiStateKeyContributor.REPORT_FILTER;
import static de.hbt.salat.reporting.controller.ReportingUiStateKeyContributor.REPORT_GROUP_FILTER;

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
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
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
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.viewhelper.FilterHintViewHelper;
import de.hbt.salat.reporting.auth.ReportAuthorization;
import de.hbt.salat.reporting.domain.ReportParameter;
import de.hbt.salat.reporting.domain.ReportResult;
import de.hbt.salat.reporting.service.ExcelExportService;
import de.hbt.salat.reporting.service.ReportFileNames;
import de.hbt.salat.reporting.service.ReportGroupService;
import de.hbt.salat.reporting.service.ReportParameters;
import de.hbt.salat.reporting.service.ReportService;

@Slf4j
@Controller
@RequestMapping("/reporting/reports")
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;
  private final ReportAuthorization reportAuthorization;
  private final ExcelExportService excelExportService;
  private final FilterHintViewHelper filterHintViewHelper;
  private final ReportGroupService reportGroupService;
  private final AuthorizedUser authorizedUser;
  private final MessageSourceAccessor messages;

  /**
   * The reports by group (#1523), one card each. A group the person sees is one with a report they may
   * run; the group filter offers those, {@link ReportService#WITHOUT_GROUP} for the reports without one.
   */
  @GetMapping
  public String list(@RequestParam(value = "fReportFilter", required = false) String fReportFilter,
                     @RequestParam(value = "fReportGroupId", required = false) Long fReportGroupId,
                     Model model) {
    var overview = reportService.getOverview(fReportFilter, fReportGroupId);
    Map<Long, Boolean> mayEdit = new HashMap<>();
    Map<Long, Boolean> mayDelete = new HashMap<>();
    overview.groups().forEach(group -> group.reports().forEach(r -> {
      mayEdit.put(r.getId(), reportAuthorization.isAuthorized(r, AccessLevel.WRITE));
      mayDelete.put(r.getId(), reportAuthorization.isAuthorized(r, AccessLevel.DELETE));
    }));
    model.addAttribute("pageTitle", "Reports");
    model.addAttribute("section", "reports");
    model.addAttribute("subSection", "reports");
    model.addAttribute("sectionTitle", "Reports");
    model.addAttribute("reportGroups", overview.groups());
    model.addAttribute("visibleGroups", overview.visibleGroups());
    model.addAttribute("withoutGroup", ReportService.WITHOUT_GROUP);
    model.addAttribute("mayEdit", mayEdit);
    model.addAttribute("mayDelete", mayDelete);
    model.addAttribute("fReportFilter", fReportFilter);
    model.addAttribute("fReportGroupId", fReportGroupId);
    // with a filter set, every group it leaves is open, whatever was collapsed before
    model.addAttribute("filtered", (fReportFilter != null && !fReportFilter.isBlank()) || fReportGroupId != null);
    model.addAttribute("mayManageGroups", authorizedUser.isManager());
    return "reporting/reports-list";
  }

  @Authorized(requiresPeopleLead = true)
  @GetMapping("/create")
  public String createForm(Model model) {
    model.addAttribute("pageTitle", "Create Report");
    model.addAttribute("section", "reports");
    model.addAttribute("subSection", "reports");
    model.addAttribute("sectionTitle", "Reports");
    var form = new ReportForm();
    model.addAttribute("report", form);
    model.addAttribute("isEdit", false);
    addGroupModel(model, form);
    return "reporting/report-form";
  }

  /**
   * The group of the form (#1523): a manager picks one, a people lead sees the personal group the report
   * goes into.
   */
  private void addGroupModel(Model model, ReportForm form) {
    boolean chooseGroup = authorizedUser.isManager();
    model.addAttribute("chooseGroup", chooseGroup);
    if (chooseGroup) {
      model.addAttribute("groups", reportGroupService.getGroups());
    } else {
      model.addAttribute("targetGroup", reportService.getTargetGroup(form.getGroupId(), form.getId()));
    }
  }

  @GetMapping("/edit")
  @Authorized(requiresPeopleLead = true)
  public String editForm(@RequestParam("id") Long id, Model model) {
    var rd = reportService.getReportDefinition(id);
    if(rd == null) throw new ErrorResponseException(HttpStatus.NOT_FOUND);
    var form = new ReportForm();
    form.setId(rd.getId());
    form.setName(rd.getName());
    form.setSql(rd.getSql());
    form.setGroupId(rd.getGroupId());
    model.addAttribute("pageTitle", "Edit Report");
    model.addAttribute("section", "reports");
    model.addAttribute("subSection", "reports");
    model.addAttribute("sectionTitle", "Reports");
    model.addAttribute("report", form);
    model.addAttribute("isEdit", true);
    model.addAttribute("reportAuthorizations", reportAuthorization.getAuthorizations(rd));
    addGroupModel(model, form);
    return "reporting/report-form";
  }

  @PostMapping("/store")
  @Authorized(requiresPeopleLead = true)
  public String store(@ModelAttribute("report") ReportForm form,
                      BindingResult bindingResult,
                      Model model,
                      RedirectAttributes redirectAttributes) {

    if (form.getName() == null || form.getName().isBlank()) {
      bindingResult.rejectValue("name", "error.name", "Name is required");
    } else if (reportService.isNameTaken(form.getName(), form.getGroupId(), form.getId())) {
      // a title is unique within its group (#1523); the message names the group
      var group = reportService.getTargetGroup(form.getGroupId(), form.getId());
      var groupName = group.isNone() ? messages.getMessage("main.reporting.group.none") : group.name();
      bindingResult.rejectValue("name", "error.name", messages.getMessage("main.reporting.report.name.taken",
          new Object[] {groupName}));
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
      addGroupModel(model, form);
      return "reporting/report-form";
    }

    // The filter stays as the user left it (ADR-0023); where it hides the saved report, the
    // message says so instead of the list silently not showing it.
    if (form.getId() == null) {
      reportService.create(form.getName(), form.getSql(), form.getGroupId());
      filterHintViewHelper.addSuccess(redirectAttributes, "Report created successfully", REPORT_FILTER,
          REPORT_GROUP_FILTER);
    } else {
      reportService.update(form.getId(), form.getName(), form.getSql(), form.getGroupId());
      filterHintViewHelper.addSuccess(redirectAttributes, "Report updated successfully", REPORT_FILTER,
          REPORT_GROUP_FILTER);
    }

    return "redirect:/reporting/reports";
  }

  @PostMapping("/delete")
  @Authorized(requiresPeopleLead = true)
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
    var fileName = ReportFileNames.create(rd, reportService.getGroupOf(rd), reportResult.getParameters(), "xlsx");
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
    /** The group a manager chooses (#1523), {@code null} for none; a people lead's form sends none. */
    private Long groupId;

  }

  @Setter
  @Getter
  public static class ExecuteForm {

    private Long reportId;

    private List<ReportParameter> parameters = new ArrayList<>();

    /**
     * The row the focus starts in: the first one the request left open. The given values come
     * first, so it is the one behind them — after the command palette filled some, the next to
     * type (#1493); without any, the first.
     */
    private int firstOpen;

    public void initParameters(List<ReportParameter> preset, java.util.Set<String> missingParameterNames) {
      var list = new java.util.ArrayList<ReportParameter>();
      if (preset != null) list.addAll(preset);
      firstOpen = list.size();

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
      return ReportParameters.kindOf(parameterName).type();
    }

    private String getValue(String parameterName) {
      return switch (ReportParameters.kindOf(parameterName)) {
        case YEAR -> Year.from(DateUtils.today()).toString();
        case MONTH -> Integer.toString(YearMonth.from(DateUtils.today()).getMonthValue());
        case DATE -> DateUtils.today().toString();
        case STRING -> "";
      };
    }

  }
}
