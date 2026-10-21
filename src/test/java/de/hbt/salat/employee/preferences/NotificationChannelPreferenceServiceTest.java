package de.hbt.salat.employee.preferences;

import static de.hbt.salat.testutils.ReferenceTestUtils.employeeWithId;
import static de.hbt.salat.testutils.ReferenceTestUtils.salatUserWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.common.notification.NotificationRecipient;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.settings.service.UiPreferenceService;
import de.hbt.salat.settings.service.UserPreferenceService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Who receives a notification, and how (#828): the answer the employee module gives the notification module. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class NotificationChannelPreferenceServiceTest {

  private static final NotificationKind SHARED = NotificationKind.of("shared", 10, false);
  private static final long ANNA_LOGIN = 11L;
  private static final long ADMIN_LOGIN = 12L;

  private final UserPreferenceService userPreferenceService = mock(UserPreferenceService.class);
  private final UiPreferenceService uiPreferenceService = mock(UiPreferenceService.class);
  private final EmployeePreferenceService employeePreferenceService = mock(EmployeePreferenceService.class);
  private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
  private final NotificationChannelPreferenceService service = new NotificationChannelPreferenceService(
      userPreferenceService, uiPreferenceService, employeePreferenceService, employeeRepository);

  private Employee anna;

  @BeforeEach
  void setUp() {
    anna = employeeWithId(1L);
    anna.setFirstname("Anna");
    anna.setSalatUser(salatUserWithId(ANNA_LOGIN));
    when(employeePreferenceService.getNotificationEmailFor(anna)).thenReturn("anna@example.org");
    when(uiPreferenceService.getLocaleFor(ANNA_LOGIN)).thenReturn(Locale.ENGLISH);
    when(userPreferenceService.getModuleSettings(anyLong(), eq(NotificationChannelPreferences.MODULE_KEY)))
        .thenReturn(Map.of());
  }

  @Test
  void a_person_is_greeted_by_first_name_at_the_address_for_notifications_in_the_chosen_language() {
    when(employeeRepository.findBySalatUserIds(List.of(ANNA_LOGIN))).thenReturn(List.of(anna));
    when(userPreferenceService.getModuleSettings(ANNA_LOGIN, NotificationChannelPreferences.MODULE_KEY))
        .thenReturn(Map.of("mailOn", List.of("shared")));

    assertThat(service.resolve(List.of(ANNA_LOGIN), SHARED)).containsExactly(
        new NotificationRecipient(ANNA_LOGIN, "Anna", "anna@example.org", Locale.ENGLISH, true, true));
  }

  @Test
  void a_login_without_a_person_gets_the_bell_but_no_mail() {
    when(employeeRepository.findBySalatUserIds(List.of(ADMIN_LOGIN))).thenReturn(List.of());

    assertThat(service.resolve(List.of(ADMIN_LOGIN), NotificationKind.of("budget", 20, true))).containsExactly(
        new NotificationRecipient(ADMIN_LOGIN, "", null, Locale.GERMAN, true, false));
  }

  @Test
  void each_login_is_resolved_once_in_the_order_given() {
    when(employeeRepository.findBySalatUserIds(List.of(ADMIN_LOGIN, ANNA_LOGIN, ADMIN_LOGIN))).thenReturn(List.of(anna));

    assertThat(service.resolve(List.of(ADMIN_LOGIN, ANNA_LOGIN, ADMIN_LOGIN), SHARED))
        .extracting(NotificationRecipient::salatUserId).containsExactly(ADMIN_LOGIN, ANNA_LOGIN);
  }

  @Test
  void nobody_to_notify_asks_nothing() {
    assertThat(service.resolve(List.of(), SHARED)).isEmpty();
  }
}
