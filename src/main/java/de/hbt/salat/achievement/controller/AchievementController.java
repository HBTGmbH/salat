package de.hbt.salat.achievement.controller;

import static de.hbt.salat.common.util.DateUtils.today;

import de.hbt.salat.achievement.service.AchievementService;
import de.hbt.salat.auth.domain.Authorized;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The profile page with the own achievements, and closing the popup that announces new ones (#1501). */
@Controller
@RequiredArgsConstructor
@Authorized(requireUnrestricted = true)
public class AchievementController {

  private final AchievementService achievementService;

  /** Whoever opens the page sees every achievement there, so none is left to announce. */
  @GetMapping("/profile")
  public String profile(@RequestParam(required = false) String fAchievementSort, Model model) {
    achievementService.markSeenForCurrentPerson();
    model.addAttribute("profile", achievementService.getProfileOfCurrentPerson(today()));
    model.addAttribute("achievementSort", "type".equals(fAchievementSort) ? "type" : "date");
    model.addAttribute("today", today());
    return "achievement/profile";
  }

  /** Sent by the popup when it closes, however it is closed. */
  @PostMapping("/achievements/seen")
  @Authorized(requireUnrestricted = true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void seen() {
    achievementService.markSeenForCurrentPerson();
  }
}
