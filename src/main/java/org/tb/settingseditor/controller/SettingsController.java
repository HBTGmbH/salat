package org.tb.settingseditor.controller;

import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;

import java.time.LocalTime;
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
import org.tb.dailyreport.preferences.DailyPreferenceService;
import org.tb.dailyreport.preferences.DailyPreferences;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SettingsController {

  private final DailyPreferenceService dailyPreferenceService;
  private final MessageSourceAccessor messages;

  @GetMapping
  public String show(Model model) {
    DailyPreferences daily = dailyPreferenceService.getForCurrentUser();
    SettingsForm form = new SettingsForm();
    form.setWorkDayStart(daily.workDayStart());
    model.addAttribute("settingsForm", form);
    model.addAttribute("section", "settings");
    model.addAttribute("sectionTitle", messages.getMessage("main.settings.section.title"));
    model.addAttribute("title", messages.getMessage("main.settings.title"));
    return "settingseditor/settings-form";
  }

  @PostMapping("/store")
  public String store(@ModelAttribute SettingsForm form,
                      RedirectAttributes redirectAttributes) {
    dailyPreferenceService.saveForCurrentUser(new DailyPreferences(form.getWorkDayStart()));
    redirectAttributes.addFlashAttribute("toastSuccess",
        messages.getMessage("main.settings.save.success"));
    return "redirect:/settings";
  }

  @Data
  public static class SettingsForm {

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime workDayStart = LocalTime.of(DEFAULT_WORK_DAY_START, 0);

  }

}
