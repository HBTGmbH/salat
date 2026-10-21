package de.hbt.salat.settingseditor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hbt.salat.achievement.service.AchievementNotificationKinds;
import de.hbt.salat.achievement.service.AchievementPreferenceService;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.beta.service.BetaFeatureService;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferences;
import de.hbt.salat.employee.preferences.EmployeePreferenceService;
import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.employee.preferences.NotificationChannelPreferenceService;
import de.hbt.salat.employee.preferences.NotificationChannelPreferences;
import de.hbt.salat.notification.service.NotificationKindRegistry;
import de.hbt.salat.settings.service.UiPreferenceService;
import de.hbt.salat.settings.web.LocaleSyncInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/**
 * The channels of each notification kind (#828): stored as the settings page sends them. The bell
 * of an achievement is the switch of its popup and stored there, so the channels keep it on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class SettingsNotificationChannelsTest {

  private static final NotificationKind SHARED = NotificationKind.of("shared", 10, false);
  private static final NotificationKind ALERT = NotificationKind.of("alert", 30, true);
  private static final NotificationKind TEAM = NotificationKind.of("team", 50, true).forPeopleLeads();


  @Mock
  private UiPreferenceService uiPreferenceService;
  @Mock
  private DailyPreferenceService dailyPreferenceService;
  @Mock
  private TimereportPreferenceService timereportPreferenceService;
  @Mock
  private EmployeePreferenceService employeePreferenceService;
  @Mock
  private BetaFeatureService betaFeatureService;
  @Mock
  private LocaleSyncInterceptor localeSyncInterceptor;
  @Mock
  private MessageSourceAccessor messages;
  @Mock
  private AchievementPreferenceService achievementPreferenceService;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private NotificationKindRegistry notificationKindRegistry;
  @Mock
  private NotificationChannelPreferenceService notificationChannelPreferenceService;
  @InjectMocks
  private SettingsController controller;

  @BeforeEach
  void setUp() {
    when(timereportPreferenceService.getForCurrentUser()).thenReturn(TimereportPreferences.defaults());
    when(notificationKindRegistry.all()).thenReturn(List.of(SHARED, ALERT, AchievementNotificationKinds.ACHIEVEMENT_REACHED, TEAM));
    when(authorizedUser.isPeopleLead()).thenReturn(true);
  }

  @Test
  void stores_the_switched_channels_and_keeps_the_bell_of_an_achievement_on() {
    var form = new SettingsController.SettingsForm();
    form.setNotificationBell(List.of("alert"));
    form.setNotificationMail(List.of("shared", "achievement-reached"));

    var stored = store(form);

    assertThat(stored.bell(SHARED)).isFalse();
    assertThat(stored.bell(ALERT)).isTrue();
    assertThat(stored.bell(AchievementNotificationKinds.ACHIEVEMENT_REACHED)).isTrue();
    assertThat(stored.mail(SHARED)).isTrue();
    assertThat(stored.mail(ALERT)).isFalse();
    assertThat(stored.mail(AchievementNotificationKinds.ACHIEVEMENT_REACHED)).isTrue();
  }

  /** With every switch of a column off, Spring leaves the list empty or null. */
  @Test
  void every_switch_off_turns_every_channel_off() {
    var form = new SettingsController.SettingsForm();
    form.setNotificationBell(null);
    form.setNotificationMail(null);

    var stored = store(form);

    assertThat(stored.bell(SHARED)).isFalse();
    assertThat(stored.mail(ALERT)).isFalse();
  }

  /** Externals and interns see no row for achievements, so nothing is stored for it. */
  @Test
  void a_restricted_login_stores_nothing_for_achievements() {
    when(authorizedUser.isRestricted()).thenReturn(true);
    var form = new SettingsController.SettingsForm();
    form.setNotificationMail(List.of());

    var stored = store(form);

    assertThat(stored.mailOn()).doesNotContain("achievement-reached");
    assertThat(stored.mailOff()).doesNotContain("achievement-reached");
    assertThat(stored.bellOff()).doesNotContain("achievement-reached");
  }

  /** The switches of a kind for people leads stand greyed out for everybody else and are not sent. */
  @Test
  void without_the_role_of_a_people_lead_the_kinds_for_people_leads_keep_their_defaults() {
    when(authorizedUser.isPeopleLead()).thenReturn(false);
    var form = new SettingsController.SettingsForm();
    form.setNotificationBell(List.of("shared", "alert"));
    form.setNotificationMail(List.of("alert"));

    var stored = store(form);

    assertThat(stored.bell(TEAM)).isTrue();
    assertThat(stored.mail(TEAM)).isTrue();
    assertThat(stored.toMap()).isEmpty();
  }

  @Test
  void a_people_lead_switches_the_kinds_for_people_leads() {
    var form = new SettingsController.SettingsForm();
    form.setNotificationBell(List.of("shared", "alert"));
    form.setNotificationMail(List.of("alert"));

    var stored = store(form);

    assertThat(stored.bell(TEAM)).isFalse();
    assertThat(stored.mail(TEAM)).isFalse();
  }

  private NotificationChannelPreferences store(SettingsController.SettingsForm form) {
    controller.store(form, new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());
    var stored = ArgumentCaptor.forClass(NotificationChannelPreferences.class);
    verify(notificationChannelPreferenceService).saveForCurrentUser(stored.capture());
    return stored.getValue();
  }
}
