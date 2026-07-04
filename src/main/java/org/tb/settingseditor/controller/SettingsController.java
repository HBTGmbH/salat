package org.tb.settingseditor.controller;

import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.tb.dailyreport.preferences.DailyPreferenceService;
import org.tb.dailyreport.preferences.DailyPreferences;
import org.tb.dailyreport.preferences.TimereportPreferenceService;
import org.tb.dailyreport.preferences.TimereportPreferences;
import org.tb.employee.preferences.EmployeePreferenceService;
import org.tb.employee.preferences.EmployeePreferences;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;
import org.tb.settings.service.UiPreferenceService;
import org.tb.settings.web.LocaleSyncInterceptor;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SettingsController {

  private final DailyPreferenceService dailyPreferenceService;
  private final TimereportPreferenceService timereportPreferenceService;
  private final EmployeePreferenceService employeePreferenceService;
  private final EmployeeService employeeService;
  private final EmployeecontractService employeecontractService;
  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final MessageSourceAccessor messages;
  private final CookieLocaleResolver localeResolver;
  private final UiPreferenceService uiPreferenceService;
  private final LocaleSyncInterceptor localeSyncInterceptor;

  @GetMapping
  public String show(Model model) {
    DailyPreferences daily = dailyPreferenceService.getForCurrentUser();
    TimereportPreferences timereport = timereportPreferenceService.getForCurrentUser();
    EmployeePreferences employee = employeePreferenceService.getForCurrentUser();

    SettingsForm form = new SettingsForm();
    form.setWorkDayStart(daily.workDayStart());
    form.setFavoriteSuborderId(timereport.favoriteSuborderId() != null
        ? timereport.favoriteSuborderId().toString() : "");
    form.setLocale(uiPreferenceService.getLocaleForCurrentUser());
    form.setNotificationEmail(employee.notificationEmail() != null ? employee.notificationEmail() : "");
    form.setGravatarEmail(employee.gravatarEmail() != null ? employee.gravatarEmail() : "");

    model.addAttribute("settingsForm", form);
    model.addAttribute("suborders", loadSuborders());
    model.addAttribute("section", "settings");
    model.addAttribute("sectionTitle", messages.getMessage("main.settings.section.title"));
    model.addAttribute("title", messages.getMessage("main.settings.title"));
    return "settingseditor/settings-form";
  }

  @PostMapping("/store")
  public String store(@ModelAttribute SettingsForm form,
                      RedirectAttributes redirectAttributes,
                      HttpServletRequest request,
                      HttpServletResponse response) {
    uiPreferenceService.saveLocaleForCurrentUser(form.getLocale());
    dailyPreferenceService.saveForCurrentUser(new DailyPreferences(form.getWorkDayStart()));

    Long favSuborderId = null;
    if (form.getFavoriteSuborderId() != null && !form.getFavoriteSuborderId().isBlank()) {
      try {
        favSuborderId = Long.parseLong(form.getFavoriteSuborderId());
      } catch (NumberFormatException ignored) {
      }
    }
    timereportPreferenceService.saveForCurrentUser(new TimereportPreferences(favSuborderId));
    employeePreferenceService.saveForCurrentUser(
        new EmployeePreferences(form.getNotificationEmail(), form.getGravatarEmail()));

    String localeValue = form.getLocale() != null ? form.getLocale() : "";
    switch (localeValue) {
      case "de" -> localeResolver.setLocale(request, response, Locale.GERMAN);
      case "en" -> localeResolver.setLocale(request, response, Locale.ENGLISH);
      // browser detection: write "auto" sentinel so subsequent requests skip the DB lookup
      default -> localeSyncInterceptor.writeSentinelCookie(response);
    }

    redirectAttributes.addFlashAttribute("toastSuccess",
        messages.getMessage("main.settings.save.success"));
    return "redirect:/settings";
  }

  private List<SuborderOption> loadSuborders() {
    var loginEmployee = employeeService.getLoginEmployee();
    var contractOpt = employeecontractService.getCurrentContract(loginEmployee.getId());
    if (contractOpt.isEmpty()) return List.of();
    long ecId = contractOpt.get().getId();
    return customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, today())
        .stream()
        .flatMap(order -> suborderService.getSuborderSummaries(ecId, order.getId(), today()).stream()
            .map(s -> {
              var desc = s.shortdescription();
              var label = (desc != null && !desc.isBlank())
                  ? s.completeOrderSign() + " · " + desc
                  : s.completeOrderSign();
              var subtext = order.getSign() + " · " + order.getShortdescription()
                  + " · " + order.getCustomer().getShortname();
              return new SuborderOption(s.id(), label, subtext);
            }))
        .toList();
  }

  @Data
  public static class SettingsForm {

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime workDayStart = LocalTime.of(DEFAULT_WORK_DAY_START, 0);

    private String favoriteSuborderId = "";

    private String locale = "-browser-";

    private String notificationEmail = "";

    private String gravatarEmail = "";

  }

}
