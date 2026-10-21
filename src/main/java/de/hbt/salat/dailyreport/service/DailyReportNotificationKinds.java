package de.hbt.salat.dailyreport.service;

import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.common.notification.NotificationKindContributor;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The notifications about bookings, their release and their acceptance (#828). The mail
 * „Buchungen freigegeben" was sent before #828 and stays on by default; the two reminders are sent
 * on purpose by whoever presses the button, so their mail cannot be switched off. The release and
 * the reminder to accept reach the people leads of the contract only.
 */
@Component
public class DailyReportNotificationKinds implements NotificationKindContributor {

  public static final NotificationKind TIMEREPORT_SHARED = NotificationKind.of("timereport-shared", 10, false);
  public static final NotificationKind TIMEREPORTS_RELEASED = NotificationKind.of("timereports-released", 50, true).forPeopleLeads();
  public static final NotificationKind TIMEREPORTS_ACCEPTED = NotificationKind.of("timereports-accepted", 60, false);
  public static final NotificationKind RELEASE_REMINDER = NotificationKind.mailedAlways("release-reminder", 70);
  public static final NotificationKind ACCEPTANCE_REMINDER = NotificationKind.mailedAlways("acceptance-reminder", 80).forPeopleLeads();

  @Override
  public List<NotificationKind> getNotificationKinds() {
    return List.of(TIMEREPORT_SHARED, TIMEREPORTS_RELEASED, TIMEREPORTS_ACCEPTED, RELEASE_REMINDER, ACCEPTANCE_REMINDER);
  }
}
