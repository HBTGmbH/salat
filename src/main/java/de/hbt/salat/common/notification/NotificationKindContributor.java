package de.hbt.salat.common.notification;

import java.util.List;

/**
 * Contributes the notification kinds of a module (#828) — the pattern of {@code BetaFeatureContributor}.
 * Implement it in the module that notifies, annotate the implementation with {@code @Component} and
 * keep a public no-argument constructor: the check that every kind has its texts builds the
 * contributors without Spring. Declare each kind as a {@code public static final} constant, so that
 * the module passes it to {@code NotificationService} by name.
 */
public interface NotificationKindContributor {

  List<NotificationKind> getNotificationKinds();
}
