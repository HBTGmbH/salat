package de.hbt.salat.common.notification;

import java.util.Collection;
import java.util.List;

/**
 * Who receives a notification, and how (#828). The notification module asks this when it notifies;
 * the employee module answers, because the address for notifications and the person's choices are
 * stored with the person. The notification module may import neither employee nor settings
 * ({@code ArchitectureTest.notificationShouldAccessCommonAuthOnly}), so it reaches them through this
 * interface alone — the way a module reaches the beta module through {@code Betas}.
 */
public interface NotificationRecipients {

  /** The recipients of a notification of this kind, each once, in the order of the ids. */
  List<NotificationRecipient> resolve(Collection<Long> salatUserIds, NotificationKind kind);
}
