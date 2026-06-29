package org.tb.settings.controller;

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
import org.tb.settings.domain.UserPreference;
import org.tb.settings.service.UserPreferenceService;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SettingsController {

  private final UserPreferenceService userPreferenceService;
  private final MessageSourceAccessor messages;

  @GetMapping
  public String show(Model model) {
    UserPreference pref = userPreferenceService.getOrCreateForCurrentUser();
    model.addAttribute("settingsForm", UserPreferenceForm.from(pref));
    model.addAttribute("section", "settings");
    model.addAttribute("sectionTitle", messages.getMessage("main.settings.section.title"));
    model.addAttribute("title", messages.getMessage("main.settings.title"));
    return "settings/settings-form";
  }

  @PostMapping("/store")
  public String store(@ModelAttribute UserPreferenceForm form,
                      RedirectAttributes redirectAttributes) {
    userPreferenceService.saveForCurrentUser(form.getWorkDayStart());
    redirectAttributes.addFlashAttribute("toastSuccess",
        messages.getMessage("main.settings.save.success"));
    return "redirect:/settings";
  }

  @Data
  public static class UserPreferenceForm {

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime workDayStart = LocalTime.of(DEFAULT_WORK_DAY_START, 0);

    public static UserPreferenceForm from(UserPreference pref) {
      UserPreferenceForm f = new UserPreferenceForm();
      f.setWorkDayStart(pref.getWorkDayStart());
      return f;
    }

  }

}
