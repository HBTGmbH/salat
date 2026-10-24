package de.hbt.salat.settingseditor.controller;

import static de.hbt.salat.common.GlobalConstants.DEFAULT_WORK_DAY_START;
import static de.hbt.salat.common.util.DateUtils.today;

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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import static de.hbt.salat.achievement.service.AchievementNotificationKinds.ACHIEVEMENT_REACHED;

import de.hbt.salat.achievement.domain.AchievementPreferences;
import de.hbt.salat.achievement.service.AchievementPreferenceService;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.controller.SuborderOption;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailyPreferences;
import de.hbt.salat.dailyreport.preferences.DailySidebarPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailySidebarPreferences;
import de.hbt.salat.dailyreport.preferences.DurationInputMode;
import de.hbt.salat.dailyreport.preferences.TimereportPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferences;
import de.hbt.salat.dailyreport.service.DailyReportBetaFeatureContributor;
import de.hbt.salat.employee.preferences.EmployeePreferenceService;
import de.hbt.salat.employee.preferences.EmployeePreferences;
import de.hbt.salat.employee.preferences.NotificationChannelPreferenceService;
import de.hbt.salat.employee.preferences.NotificationChannelPreferences;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.favorites.domain.FavoritePreferences;
import de.hbt.salat.favorites.service.FavoriteService;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.beta.service.BetaFeatureRegistry;
import de.hbt.salat.beta.service.BetaFeatureService;
import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.notification.service.NotificationKindRegistry;
import de.hbt.salat.settings.service.UiPreferenceService;
import de.hbt.salat.settings.web.LocaleSyncInterceptor;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@Authorized
public class SettingsController {

  private final DailyPreferenceService dailyPreferenceService;
  private final TimereportPreferenceService timereportPreferenceService;
  private final DailySidebarPreferenceService dailySidebarPreferenceService;
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
  private final BetaFeatureRegistry betaFeatureRegistry;
  private final FavoriteService favoriteService;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final AchievementPreferenceService achievementPreferenceService;
  private final AuthorizedUser authorizedUser;
  private final NotificationKindRegistry notificationKindRegistry;
  private final NotificationChannelPreferenceService notificationChannelPreferenceService;

  @GetMapping
  public String show(Model model) {
    DailyPreferences daily = dailyPreferenceService.getForCurrentUser();
    TimereportPreferences timereport = timereportPreferenceService.getForCurrentUser();
    EmployeePreferences employee = employeePreferenceService.getForCurrentUser();

    SettingsForm form = new SettingsForm();
    form.setWorkDayStart(daily.workDayStart());
    form.setConsiderMandatoryBreak(daily.considerMandatoryBreak());
    form.setFavoriteSuborderId(timereport.favoriteSuborderId() != null
        ? timereport.favoriteSuborderId().toString() : "");
    form.setDurationInputMode(timereport.durationInputMode().getKey());
    form.setTwoStepSuborderSelect(timereport.twoStepSuborderSelect());
    form.setFavoriteListSize(favoriteService.getListSize());
    var sidebar = dailySidebarPreferenceService.getForCurrentUser();
    form.setWeekStripPlacement(sidebar.weekStrip().getKey());
    form.setFavoriteShortListOrder(sidebar.favoriteOrder().getKey());
    form.setLocale(uiPreferenceService.getLocaleForCurrentUser());
    form.setNotificationEmail(employee.notificationEmail() != null ? employee.notificationEmail() : "");
    form.setGravatarEmail(employee.gravatarEmail() != null ? employee.gravatarEmail() : "");
    form.setBetaFeatures(new ArrayList<>(betaFeatureService.getForCurrentUser().keys().stream()
        .sorted()
        .toList()));
    form.setAchievementsAnnounced(achievementPreferenceService.getForCurrentUser().announce());
    var channels = notificationChannelPreferenceService.getForCurrentUser();
    var notificationKinds = notificationKindsShown();
    form.setNotificationBell(new ArrayList<>(notificationKinds.stream().filter(channels::bell).map(NotificationKind::getKey).toList()));
    form.setNotificationMail(new ArrayList<>(notificationKinds.stream().filter(channels::mail).map(NotificationKind::getKey).toList()));

    var loginEmployee = employeeService.getLoginEmployee();
    model.addAttribute("settingsForm", form);
    model.addAttribute("defaultEmail", employeePreferenceService.defaultEmailFor(loginEmployee));
    model.addAttribute("suborders", loadSuborders());
    // the switch section is shown only while there is a beta to switch on
    model.addAttribute("betaFeatureOptions", betaFeatureRegistry.all());
    model.addAttribute("notificationKinds", notificationKinds);
    model.addAttribute("peopleLead", authorizedUser.isPeopleLead());
    model.addAttribute("achievementKindKey", ACHIEVEMENT_REACHED.getKey());
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
    // The settings of the beta "Favoriten zuerst" are stored only with it on (#1442): switched off,
    // the page neither shows nor sends them, and what was stored stays for the next time.
    boolean favoritesFirst = form.getBetaFeatures() != null
        && form.getBetaFeatures().contains(DailyReportBetaFeatureContributor.FAVORITES_FIRST.getKey());
    if (favoritesFirst) {
      // first, so that a value out of range saves nothing at all rather than half the form (#1414)
      try {
        favoriteService.setListSize(form.getFavoriteListSize() != null ? form.getFavoriteListSize() : 0);
      } catch (ErrorCodeException e) {
        redirectAttributes.addFlashAttribute("toastError", errorCodeViewHelper.toViewMessages(e).stream()
            .map(Object::toString).findFirst().orElse("Error"));
        return "redirect:/settings";
      }
      dailySidebarPreferenceService.saveForCurrentUser(
          DailySidebarPreferences.ofKeys(form.getWeekStripPlacement(), form.getFavoriteShortListOrder()));
    }
    uiPreferenceService.saveLocaleForCurrentUser(form.getLocale());
    dailyPreferenceService.saveForCurrentUser(new DailyPreferences(form.getWorkDayStart(), form.isConsiderMandatoryBreak()));

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
        new TimereportPreferences(favSuborderId, durationInputMode, lastUsedDurationMode,
            form.isTwoStepSuborderSelect()));
    employeePreferenceService.saveForCurrentUser(
        new EmployeePreferences(form.getNotificationEmail(), form.getGravatarEmail()));
    // null when every beta switch is off — Spring resets the list via the "_betaFeatures" marker
    betaFeatureService.saveForCurrentUser(
        form.getBetaFeatures() != null ? form.getBetaFeatures() : List.of());
    if (!authorizedUser.isRestricted()) {
      achievementPreferenceService.saveForCurrentUser(new AchievementPreferences(form.isAchievementsAnnounced()));
    }
    // null when every switch of a column is off — Spring resets the list via its "_" marker. The bell
    // of an achievement is the switch of its popup, stored above, so its own bell stays on.
    var bell = new ArrayList<>(form.getNotificationBell() != null ? form.getNotificationBell() : List.of());
    bell.add(ACHIEVEMENT_REACHED.getKey());
    notificationChannelPreferenceService.saveForCurrentUser(NotificationChannelPreferences.of(
        notificationKindsSwitchable(), bell, form.getNotificationMail()));

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

  /** Externals and interns get no achievements (#1501), so nothing announces one to them. */
  private List<NotificationKind> notificationKindsShown() {
    return notificationKindRegistry.all().stream()
        .filter(kind -> !authorizedUser.isRestricted() || !kind.equals(ACHIEVEMENT_REACHED))
        .toList();
  }

  /**
   * A kind for people leads stands greyed out for everybody else and is not sent: what is stored for
   * it goes back to its defaults, which apply once the person becomes a people lead.
   */
  private List<NotificationKind> notificationKindsSwitchable() {
    return notificationKindsShown().stream()
        .filter(kind -> !kind.isPeopleLeadsOnly() || authorizedUser.isPeopleLead())
        .toList();
  }

  private List<SuborderOption> loadSuborders() {
    var loginEmployee = employeeService.getLoginEmployee();
    var contractOpt = employeecontractService.getCurrentContract(loginEmployee.getId());
    if (contractOpt.isEmpty()) return List.of();
    // the same list the booking form offers today, so the favourite is one it can book
    return SuborderOption.bookable(customerorderService, suborderService, contractOpt.get().getId(), today());
  }

  @Data
  public static class SettingsForm {

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime workDayStart = LocalTime.of(DEFAULT_WORK_DAY_START, 0);

    /** Whether the contract target of the daily view counts the mandatory break (#1236). */
    private boolean considerMandatoryBreak = true;

    private String favoriteSuborderId = "";

    /** Key of a {@link DurationInputMode}, see the booking form's entry mode toggle (#844). */
    private String durationInputMode = DurationInputMode.REMEMBER.getKey();

    /** Whether the booking form asks for the order first and then for one of its suborders (#1522). */
    private boolean twoStepSuborderSelect;

    /**
     * How many favourites the short list of the daily view shows ordered by use (#1414); a setting
     * of the beta "Favoriten zuerst" since #1442.
     */
    private Integer favoriteListSize = FavoritePreferences.DEFAULT_LIST_SIZE;

    /** Key of a {@code WeekStripPlacement}, a setting of the beta "Favoriten zuerst" (#1442). */
    private String weekStripPlacement;

    /** Key of a {@code FavoriteShortListOrder}, a setting of the beta "Favoriten zuerst" (#1443). */
    private String favoriteShortListOrder;

    private String locale = "-browser-";

    private String notificationEmail = "";

    private String gravatarEmail = "";

    /** Keys of the beta features the user switched on, see {@code BetaFeatureContributor}. */
    private List<String> betaFeatures = new ArrayList<>();
    /** Whether a new achievement is announced with a notification and a popup (#1501). */
    private boolean achievementsAnnounced = true;

    /** Keys of the notification kinds that go to the bell (#828), see {@code NotificationKindContributor}. */
    private List<String> notificationBell = new ArrayList<>();

    /** Keys of the notification kinds that are mailed (#828); a kind always mailed is not sent. */
    private List<String> notificationMail = new ArrayList<>();

  }

}
