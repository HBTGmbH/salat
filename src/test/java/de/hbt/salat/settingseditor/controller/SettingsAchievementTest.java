package de.hbt.salat.settingseditor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hbt.salat.achievement.domain.AchievementPreferences;
import de.hbt.salat.achievement.service.AchievementPreferenceService;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.beta.service.BetaFeatureService;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferenceService;
import de.hbt.salat.dailyreport.preferences.TimereportPreferences;
import de.hbt.salat.employee.preferences.EmployeePreferenceService;
import de.hbt.salat.employee.preferences.NotificationChannelPreferenceService;
import de.hbt.salat.notification.service.NotificationKindRegistry;
import de.hbt.salat.settings.service.UiPreferenceService;
import de.hbt.salat.settings.web.LocaleSyncInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * The switch for the announcement of new achievements (#1501): stored with the form, and not at all
 * for externals and interns, who get no achievements and see no switch.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class SettingsAchievementTest {

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
  }

  @Test
  void switching_the_announcements_off_is_stored() {
    var form = new SettingsController.SettingsForm();
    form.setAchievementsAnnounced(false);

    store(form);

    verify(achievementPreferenceService).saveForCurrentUser(new AchievementPreferences(false));
  }

  @Test
  void a_restricted_login_stores_no_switch() {
    when(authorizedUser.isRestricted()).thenReturn(true);

    store(new SettingsController.SettingsForm());

    verify(achievementPreferenceService, never()).saveForCurrentUser(any());
  }

  private void store(SettingsController.SettingsForm form) {
    controller.store(form, new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());
  }
}
