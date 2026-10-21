package de.hbt.salat.employee.preferences;

import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.common.notification.NotificationRecipient;
import de.hbt.salat.common.notification.NotificationRecipients;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.settings.service.UiPreferenceService;
import de.hbt.salat.settings.service.UserPreferenceService;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Through which channels a person wants each kind of notification (#828), and — for the notification
 * module, which may not import this one — who receives a notification and how. The address for
 * notifications and the choices are stored with the person, so the answer is given here.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class NotificationChannelPreferenceService implements NotificationRecipients {

  private final UserPreferenceService userPreferenceService;
  private final UiPreferenceService uiPreferenceService;
  private final EmployeePreferenceService employeePreferenceService;
  private final EmployeeRepository employeeRepository;

  @Transactional(readOnly = true)
  public NotificationChannelPreferences getForCurrentUser() {
    return NotificationChannelPreferences.from(
        userPreferenceService.getModuleSettings(NotificationChannelPreferences.MODULE_KEY));
  }

  public void saveForCurrentUser(NotificationChannelPreferences preferences) {
    userPreferenceService.saveModuleSettings(NotificationChannelPreferences.MODULE_KEY, preferences.toMap());
  }

  /**
   * A login without a person gets the bell as chosen and no mail: there is no address to send it to.
   * Called by every module that notifies, also from a job, so it is open to every login — it hands
   * out nothing beyond what the mail itself shows the recipient.
   */
  @Override
  @Transactional(readOnly = true)
  public List<NotificationRecipient> resolve(Collection<Long> salatUserIds, NotificationKind kind) {
    if (salatUserIds == null || salatUserIds.isEmpty()) return List.of();
    Map<Long, Employee> employees = employeeRepository.findBySalatUserIds(salatUserIds).stream()
        .collect(Collectors.toMap(employee -> employee.getSalatUser().getId(), Function.identity(), (first, second) -> first));
    return salatUserIds.stream()
        .distinct()
        .map(salatUserId -> recipient(salatUserId, employees.get(salatUserId), kind))
        .toList();
  }

  private NotificationRecipient recipient(long salatUserId, Employee employee, NotificationKind kind) {
    var channels = NotificationChannelPreferences.from(
        userPreferenceService.getModuleSettings(salatUserId, NotificationChannelPreferences.MODULE_KEY));
    if (employee == null) {
      return new NotificationRecipient(salatUserId, "", null, Locale.GERMAN, channels.bell(kind), false);
    }
    return new NotificationRecipient(salatUserId, employee.getFirstname(),
        employeePreferenceService.getNotificationEmailFor(employee), uiPreferenceService.getLocaleFor(salatUserId),
        channels.bell(kind), channels.mail(kind));
  }
}
