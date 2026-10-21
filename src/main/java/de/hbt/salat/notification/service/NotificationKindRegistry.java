package de.hbt.salat.notification.service;

import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.common.notification.NotificationKindContributor;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The notification kinds there are (#828): whatever the modules contribute through
 * {@link NotificationKindContributor} — the pattern of {@code BetaFeatureRegistry}. Two modules
 * contributing the same key stop the application at start: the key is what a person's choice is
 * stored under.
 */
@Component
public class NotificationKindRegistry {

  private final List<NotificationKind> kinds;

  public NotificationKindRegistry(List<NotificationKindContributor> contributors) {
    var byKey = contributors.stream()
        .flatMap(contributor -> contributor.getNotificationKinds().stream())
        .collect(Collectors.toMap(NotificationKind::getKey, Function.identity(), (first, second) -> {
          throw new IllegalStateException("notification kind contributed twice: " + first.getKey());
        }));
    this.kinds = byKey.values().stream()
        .sorted(Comparator.comparingInt(NotificationKind::getPosition).thenComparing(NotificationKind::getKey))
        .toList();
  }

  /** In the order of the settings page. */
  public List<NotificationKind> all() {
    return kinds;
  }
}
