package de.hbt.salat.achievement.viewhelper;

import de.hbt.salat.achievement.domain.AchievementGroup;
import de.hbt.salat.achievement.domain.UnseenAchievement;
import de.hbt.salat.achievement.service.AchievementService;
import de.hbt.salat.common.achievement.AchievementDefinition;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * What the layout and the profile page ask about achievements (#1501): the popup on every full page,
 * the number on a medal's plate and how far the next level is.
 */
@Slf4j
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class AchievementViewHelper {

  /** From here on a number is shortened to thousands, so that four digits fit the plate at 64 px. */
  private static final int THOUSAND = 1000;
  /** From here on the thousands lose their decimal: „10K“, not „10,0K“. */
  private static final int TEN_THOUSAND = 10_000;

  private final AchievementService achievementService;
  private final MessageSourceAccessor messages;

  private List<UnseenAchievement> popup;

  /**
   * The achievements the popup announces on this page - usually none, found out with a single small
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

  /** The number on a medal's plate: „25“, „1,0K“, „10K“ - in the reader's language. */
  public String plate(int value) {
    if (value < THOUSAND) {
      return String.valueOf(value);
    }
    var format = NumberFormat.getNumberInstance(LocaleContextHolder.getLocale());
    int digits = value < TEN_THOUSAND ? 1 : 0;
    format.setMinimumFractionDigits(digits);
    format.setMaximumFractionDigits(digits);
    format.setGroupingUsed(false);
    return format.format(value / (double) THOUSAND) + "K";
  }

  /** How far the next level is: „noch 4 Jahre 4 Monate“, „noch 37 Aufrufe“ or „noch nicht erreicht“. */
  public String next(AchievementDefinition definition, AchievementGroup.Next next, LocalDate today) {
    if (next.expectedOn() != null) {
      return remaining(today, next.expectedOn());
    }
    if (next.remaining() != null) {
      return messages.getMessage(definition.remainingKey(), new Object[] {next.remaining()});
    }
    return messages.getMessage("main.achievement.next.open");
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
