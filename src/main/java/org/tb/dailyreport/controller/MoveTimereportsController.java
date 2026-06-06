package org.tb.dailyreport.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.service.MoveTimereportsPreview;
import org.tb.dailyreport.service.MoveTimereportsService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Controller
@RequestMapping("/dailyreports/move")
@RequiredArgsConstructor
@PreAuthorize("not hasRole('RESTRICTED')")
public class MoveTimereportsController {

  private final MoveTimereportsService moveTimereportsService;
  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final EmployeecontractService employeecontractService;
  private final MessageSourceAccessor messages;
  private final ErrorCodeViewHelper errorCodeViewHelper;

  @GetMapping
  @PreAuthorize("hasRole('MANAGER')")
  public String form(Model model) {
    populateFormModel(model, new MoveTimereportsForm());
    return "dailyreport/move-timereports-form";
  }

  @PostMapping("/preview")
  @PreAuthorize("hasRole('MANAGER')")
  public String preview(@ModelAttribute MoveTimereportsForm form, Model model) {
    if (!validateForm(form, model)) {
      populateFormModel(model, form);
      return "dailyreport/move-timereports-form";
    }
    try {
      MoveTimereportsPreview preview = moveTimereportsService.preview(
          form.getSourceSuborderId(),
          form.getTargetSuborderId(),
          form.getEmployeeContractIds(),
          form.getFromDateTyped(),
          form.getToDateTyped());
      model.addAttribute("preview", preview);
      model.addAttribute("form", form);
      addSectionAttributes(model);
      return "dailyreport/move-timereports-preview";
    } catch (ErrorCodeException ex) {
      model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
      populateFormModel(model, form);
      return "dailyreport/move-timereports-form";
    }
  }

  @PostMapping("/confirm")
  @PreAuthorize("hasRole('MANAGER')")
  public String confirm(@ModelAttribute MoveTimereportsForm form, RedirectAttributes redirectAttributes) {
    if (form.getSourceSuborderId() == null || form.getTargetSuborderId() == null
        || form.getFromDateTyped() == null || form.getToDateTyped() == null) {
      redirectAttributes.addFlashAttribute("toastError",
          messages.getMessage("main.movetimereports.error.missing", "Ungültige Eingabe."));
      return "redirect:/dailyreports/move";
    }
    try {
      moveTimereportsService.move(
          form.getSourceSuborderId(),
          form.getTargetSuborderId(),
          form.getEmployeeContractIds(),
          form.getFromDateTyped(),
          form.getToDateTyped());
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.movetimereports.message.done", "Buchungen wurden erfolgreich umgebucht."));
      return "redirect:/dailyreports/move";
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError",
          errorCodeViewHelper.toViewMessages(ex).stream()
              .map(m -> m.resolved()).findFirst().orElse("Fehler"));
      return "redirect:/dailyreports/move";
    }
  }

  @PostMapping("/source-suborders")
  @PreAuthorize("hasRole('MANAGER')")
  public String sourceSuborders(@RequestParam(required = false) Long sourceCustomerOrderId,
      @ModelAttribute MoveTimereportsForm form, Model model, HttpServletRequest request) {
    var suborders = sourceCustomerOrderId != null
        ? suborderService.getSubordersByCustomerorderId(sourceCustomerOrderId)
        : Collections.emptyList();
    model.addAttribute("sourceSuborders", suborders);
    populateFormModel(model, form);
    boolean htmxRequest = "true".equals(request.getHeader("HX-Request"));
    model.addAttribute("htmxRequest", htmxRequest);
    model.addAttribute("sourceSubordersChanged", true);
    return "dailyreport/move-timereports-form";
  }

  @PostMapping("/target-suborders")
  @PreAuthorize("hasRole('MANAGER')")
  public String targetSuborders(@RequestParam(required = false) Long targetCustomerOrderId,
      @ModelAttribute MoveTimereportsForm form, Model model, HttpServletRequest request) {
    var suborders = targetCustomerOrderId != null
        ? suborderService.getSubordersByCustomerorderId(targetCustomerOrderId)
        : Collections.emptyList();
    model.addAttribute("targetSuborders", suborders);
    populateFormModel(model, form);
    boolean htmxRequest = "true".equals(request.getHeader("HX-Request"));
    model.addAttribute("htmxRequest", htmxRequest);
    model.addAttribute("targetSubordersChanged", true);
    return "dailyreport/move-timereports-form";
  }

  private boolean validateForm(MoveTimereportsForm form, Model model) {
    var errors = new java.util.ArrayList<String>();
    if (form.getSourceSuborderId() == null) {
      errors.add(messages.getMessage("main.movetimereports.error.source.required", "Quell-Unterauftrag erforderlich."));
    }
    if (form.getTargetSuborderId() == null) {
      errors.add(messages.getMessage("main.movetimereports.error.target.required", "Ziel-Unterauftrag erforderlich."));
    }
    if (form.getFromDateTyped() == null) {
      errors.add(messages.getMessage("main.movetimereports.error.fromdate.required", "Von-Datum erforderlich."));
    }
    if (form.getToDateTyped() == null) {
      errors.add(messages.getMessage("main.movetimereports.error.todate.required", "Bis-Datum erforderlich."));
    }
    if (form.getFromDateTyped() != null && form.getToDateTyped() != null
        && form.getFromDateTyped().isAfter(form.getToDateTyped())) {
      errors.add(messages.getMessage("main.movetimereports.error.daterange.invalid", "Von-Datum muss vor dem Bis-Datum liegen."));
    }
    if (!errors.isEmpty()) {
      model.addAttribute("validationErrors", errors);
      return false;
    }
    return true;
  }

  private void populateFormModel(Model model, MoveTimereportsForm form) {
    model.addAttribute("form", form);
    model.addAttribute("customerOrders",
        customerorderService.getCustomerordersByFilters(null, null, null, null));
    model.addAttribute("employeeContracts",
        employeecontractService.getViewableEmployeeContractsValidAt(java.time.LocalDate.now()));
    var sourceSuborders = form.getSourceCustomerOrderId() != null
        ? suborderService.getSubordersByCustomerorderId(form.getSourceCustomerOrderId())
        : Collections.emptyList();
    var targetSuborders = form.getTargetCustomerOrderId() != null
        ? suborderService.getSubordersByCustomerorderId(form.getTargetCustomerOrderId())
        : Collections.emptyList();
    model.addAttribute("sourceSuborders", sourceSuborders);
    model.addAttribute("targetSuborders", targetSuborders);
    addSectionAttributes(model);
  }

  private void addSectionAttributes(Model model) {
    model.addAttribute("section", "release");
    model.addAttribute("subSection", "move-timereports");
  }
}
