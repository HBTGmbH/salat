package org.tb.settingseditor.controller;

import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.tb.dailyreport.preferences.DailyPreferenceService;
import org.tb.dailyreport.preferences.DailyPreferences;
import org.tb.dailyreport.preferences.DurationInputMode;
import org.tb.dailyreport.preferences.TimereportPreferenceService;
import org.tb.dailyreport.preferences.TimereportPreferences;
import org.tb.employee.preferences.EmployeePreferenceService;
import org.tb.employee.preferences.EmployeePreferences;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;
import org.tb.settings.domain.BetaFeature;
import org.tb.settings.service.BetaFeatureService;
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
  private final BetaFeatureService betaFeatureService;

  @GetMapping
  public String show(Model model) {
    DailyPreferences daily = dailyPreferenceService.getForCurrentUser();
    TimereportPreferences timereport = timereportPreferenceService.getForCurrentUser();
    EmployeePreferences employee = employeePreferenceService.getForCurrentUser();

    SettingsForm form = new SettingsForm();
    form.setWorkDayStart(daily.workDayStart());
    form.setFavoriteSuborderId(timereport.favoriteSuborderId() != null
        ? timereport.favoriteSuborderId().toString() : "");
    form.setDurationInputMode(timereport.durationInputMode().getKey());
    form.setLocale(uiPreferenceService.getLocaleForCurrentUser());
    form.setNotificationEmail(employee.notificationEmail() != null ? employee.notificationEmail() : "");
    form.setGravatarEmail(employee.gravatarEmail() != null ? employee.gravatarEmail() : "");
    form.setBetaFeatures(new ArrayList<>(betaFeatureService.getForCurrentUser().enabled().stream()
        .map(BetaFeature::getKey)
        .sorted()
        .toList()));

    var loginEmployee = employeeService.getLoginEmployee();
    model.addAttribute("settingsForm", form);
    model.addAttribute("defaultEmail", employeePreferenceService.defaultEmailFor(loginEmployee));
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
    var durationInputMode = DurationInputMode.ofKey(form.getDurationInputMode())
        .orElse(DurationInputMode.REMEMBER);
    // an explicit choice also becomes the remembered one, so switching back to "remember last"
    // later starts from what the user picked here rather than from a stale value (#844)
    var lastUsedDurationMode = durationInputMode == DurationInputMode.REMEMBER
        ? timereportPreferenceService.getForCurrentUser().lastUsedDurationMode()
        : durationInputMode;
    timereportPreferenceService.saveForCurrentUser(
        new TimereportPreferences(favSuborderId, durationInputMode, lastUsedDurationMode));
    employeePreferenceService.saveForCurrentUser(
        new EmployeePreferences(form.getNotificationEmail(), form.getGravatarEmail()));
    // null when every beta switch is off — Spring resets the list via the "_betaFeatures" marker
    betaFeatureService.saveForCurrentUser(
        form.getBetaFeatures() != null ? form.getBetaFeatures() : List.of());

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

  /**
   * Switches on a single beta feature from an in-context link (see the hint on the booking page).
   * Answers with {@code HX-Refresh} so HTMX reloads the current page with the feature applied,
   * instead of navigating the user away from the form they were filling in.
   */
  @PostMapping("/beta/{key}/enable")
  public ResponseEntity<Void> enableBetaFeature(@PathVariable String key) {
    BetaFeature.ofKey(key).ifPresent(betaFeatureService::enableForCurrentUser);
    return ResponseEntity.noContent().header("HX-Refresh", "true").build();
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

    /** Key of a {@link DurationInputMode}, see the booking form's entry mode toggle (#844). */
    private String durationInputMode = DurationInputMode.REMEMBER.getKey();

    private String locale = "-browser-";

    private String notificationEmail = "";

    private String gravatarEmail = "";

    /** Keys of the beta features the user switched on, see {@link BetaFeature}. */
    private List<String> betaFeatures = new ArrayList<>();

  }

}
