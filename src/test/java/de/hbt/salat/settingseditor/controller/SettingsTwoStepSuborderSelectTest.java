package de.hbt.salat.settingseditor.controller;

import static de.hbt.salat.dailyreport.preferences.DurationInputMode.REMEMBER;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
 * The switch for choosing the suborder of the booking form in two steps (#1522), stored with the form.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class SettingsTwoStepSuborderSelectTest {

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
  void switching_it_on_is_stored() {
    var form = new SettingsController.SettingsForm();
    form.setTwoStepSuborderSelect(true);

    store(form);

    verify(timereportPreferenceService).saveForCurrentUser(new TimereportPreferences(null, REMEMBER, null, true));
  }

  @Test
  void it_is_off_unless_switched_on() {
    store(new SettingsController.SettingsForm());

    verify(timereportPreferenceService).saveForCurrentUser(TimereportPreferences.defaults());
  }

  private void store(SettingsController.SettingsForm form) {
    controller.store(form, new RedirectAttributesModelMap(), new MockHttpServletRequest(), new MockHttpServletResponse());
  }
}
