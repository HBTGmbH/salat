package de.hbt.salat.achievement.service;

import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.common.notification.NotificationKindContributor;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The announcement of a new achievement (#828). Its bell is the switch „Hinweis und Popup" of
 * {@link AchievementPreferenceService}, because it turns the popup off as well; the settings page
 * shows it in the bell column. Mail starts off.
 */
@Component
public class AchievementNotificationKinds implements NotificationKindContributor {

  public static final NotificationKind ACHIEVEMENT_REACHED = NotificationKind.of("achievement-reached", 40, false);

  @Override
  public List<NotificationKind> getNotificationKinds() {
    return List.of(ACHIEVEMENT_REACHED);
  }
}
