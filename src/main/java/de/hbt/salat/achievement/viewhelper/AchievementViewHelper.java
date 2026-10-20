package de.hbt.salat.achievement.viewhelper;

import de.hbt.salat.achievement.domain.UnseenAchievement;
import de.hbt.salat.achievement.service.AchievementService;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * What the layout and the profile page ask about achievements (#1501): the popup on every full page,
 * and how long until the next level.
 */
@Slf4j
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class AchievementViewHelper {

  private final AchievementService achievementService;
  private final MessageSourceAccessor messages;

  private List<UnseenAchievement> popup;

  /**
   * The achievements the popup announces on this page — usually none, found out with a single small
   * query; only when there is something to announce are the achievements read.
   */
  public List<UnseenAchievement> getPopup() {
    if (popup == null) {
      try {
        popup = achievementService.hasUnseenForCurrentPerson() ? achievementService.getUnseenForCurrentPerson() : List.of();
      } catch (RuntimeException e) {
        // the error page renders the layout too, possibly without anybody logged in
        log.debug("No achievements to announce", e);
        popup = List.of();
      }
    }
    return popup;
  }

  /** „noch 4 Jahre 4 Monate“: the time from today until the day, in whole years and months. */
  public String remaining(LocalDate today, LocalDate day) {
    var period = Period.between(today, day);
    if (period.getYears() == 0 && period.getMonths() == 0) {
      return messages.getMessage("main.achievement.remaining.soon");
    }
    String years = period.getYears() > 0
        ? messages.getMessage("main.achievement.remaining.years", new Object[] {period.getYears()}) : null;
    String months = period.getMonths() > 0
        ? messages.getMessage("main.achievement.remaining.months", new Object[] {period.getMonths()}) : null;
    String span = years != null && months != null ? years + " " + months : years != null ? years : months;
    return messages.getMessage("main.achievement.remaining", new Object[] {span});
  }
}
