package org.tb.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.exception.InvalidDataException;
import org.tb.settings.domain.UserPreference;
import org.tb.settings.domain.UserSettings;
import org.tb.settings.persistence.UserPreferenceRepository;
import org.tb.settings.service.UserPreferenceService;

@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({ UserPreferenceService.class })
public class UserPreferenceIntegrationTest {

  @Autowired
  private UserPreferenceRepository repository;

  @Autowired
  private SalatUserRepository salatUserRepository;

  @Autowired
  private UserPreferenceService service;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private SalatUser testUser;

  @BeforeEach
  void setup() {
    testUser = new SalatUser();
    testUser.setLoginname("testuser");
    testUser.setStatus("ma");
    salatUserRepository.save(testUser);
  }

  @Test
  void should_serialize_and_deserialize_settings_as_json() {
    // Given: settings with custom work day start
    LocalTime customTime = LocalTime.of(8, 30);
    UserSettings settings = new UserSettings(customTime);
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(settings);

    // When: saving to database
    UserPreference saved = repository.save(pref);
    repository.flush();

    // Then: JSON is persisted correctly
    UserPreference loaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getSettings().workDayStart()).isEqualTo(customTime);
  }

  @Test
  void should_default_to_9am_when_creating_new_preference() {
    // When: creating new preference with defaults
    UserSettings settings = UserSettings.defaults();

    // Then: should be 9:00 AM
    assertThat(settings.workDayStart()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void should_default_to_9am_when_no_explicit_settings_provided() {
    // Given: preference without explicit settings (uses field initializer)
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    // Note: settings field has default initializer = UserSettings.defaults()

    // When: saving and loading
    UserPreference saved = repository.save(pref);
    repository.flush();
    UserPreference loaded = repository.findById(saved.getId()).orElseThrow();

    // Then: defaults are used
    assertThat(loaded.getSettings().workDayStart()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void should_find_preference_by_salat_user() {
    // Given: saved preference
    UserSettings settings = new UserSettings(LocalTime.of(7, 0));
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(settings);
    repository.save(pref);
    repository.flush();

    // When: querying by SalatUser
    UserPreference found = repository.findBySalatUser(testUser).orElseThrow();

    // Then: correct preference is returned
    assertThat(found.getSettings().workDayStart()).isEqualTo(LocalTime.of(7, 0));
  }

  @Test
  void should_find_preference_by_salat_user_id() {
    // Given: saved preference
    UserSettings settings = new UserSettings(LocalTime.of(10, 15));
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(settings);
    repository.save(pref);
    repository.flush();

    // When: querying by salatUserId
    UserPreference found = repository.findBySalatUserId(testUser.getId()).orElseThrow();

    // Then: correct preference is returned
    assertThat(found.getSettings().workDayStart()).isEqualTo(LocalTime.of(10, 15));
  }

  @Test
  void should_update_existing_preference() {
    // Given: saved preference with initial time
    UserSettings initialSettings = new UserSettings(LocalTime.of(9, 0));
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(initialSettings);
    UserPreference saved = repository.save(pref);
    repository.flush();

    // When: updating to new time
    UserSettings updatedSettings = new UserSettings(LocalTime.of(6, 30));
    saved.setSettings(updatedSettings);
    repository.save(saved);
    repository.flush();

    // Then: persisted value is updated
    UserPreference reloaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getSettings().workDayStart()).isEqualTo(LocalTime.of(6, 30));
  }

  @Test
  void service_should_get_settings_for_salat_user() {
    // Given: saved preference
    UserSettings settings = new UserSettings(LocalTime.of(8, 0));
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(settings);
    repository.save(pref);

    // When: retrieving via service
    UserSettings retrieved = service.getSettingsFor(testUser);

    // Then: correct settings returned
    assertThat(retrieved.workDayStart()).isEqualTo(LocalTime.of(8, 0));
  }

  @Test
  void service_should_get_settings_for_salat_user_id() {
    // Given: saved preference
    UserSettings settings = new UserSettings(LocalTime.of(7, 45));
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(settings);
    repository.save(pref);

    // When: retrieving via service by ID
    UserSettings retrieved = service.getSettingsFor(testUser.getId());

    // Then: correct settings returned
    assertThat(retrieved.workDayStart()).isEqualTo(LocalTime.of(7, 45));
  }

  @Test
  void service_should_get_settings_for_login_name() {
    // Given: saved preference
    UserSettings settings = new UserSettings(LocalTime.of(11, 0));
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(settings);
    repository.save(pref);

    // When: retrieving via service by login name
    UserSettings retrieved = service.getSettingsFor(testUser.getLoginname());

    // Then: correct settings returned
    assertThat(retrieved.workDayStart()).isEqualTo(LocalTime.of(11, 0));
  }

  @Test
  void service_should_return_defaults_for_missing_preference() {
    // Given: no preference exists for user
    SalatUser otherUser = new SalatUser();
    otherUser.setLoginname("other");
    otherUser.setStatus("ma");
    salatUserRepository.save(otherUser);

    // When: retrieving settings
    UserSettings retrieved = service.getSettingsFor(otherUser);

    // Then: defaults returned
    assertThat(retrieved.workDayStart()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void service_should_throw_on_invalid_login_name() {
    // When/Then: invalid login name throws exception
    assertThatThrownBy(() -> service.getSettingsFor("nonexistent"))
        .isInstanceOf(InvalidDataException.class);
  }

  @Test
  void service_should_save_settings_for_user() {
    // Given: new settings to save
    UserSettings settings = new UserSettings(LocalTime.of(5, 30));

    // When: saving via service
    service.saveSettings(testUser, settings);

    // Then: persisted in database
    UserPreference found = repository.findBySalatUser(testUser).orElseThrow();
    assertThat(found.getSettings().workDayStart()).isEqualTo(LocalTime.of(5, 30));
  }

  @Test
  void service_should_update_existing_settings() {
    // Given: existing preference
    UserSettings initial = new UserSettings(LocalTime.of(9, 0));
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(initial);
    repository.save(pref);

    // When: saving new settings for same user
    UserSettings updated = new UserSettings(LocalTime.of(12, 30));
    service.saveSettings(testUser, updated);

    // Then: preference is updated (not duplicated)
    assertThat(repository.findBySalatUser(testUser))
        .isPresent()
        .get()
        .extracting(p -> p.getSettings().workDayStart())
        .isEqualTo(LocalTime.of(12, 30));
  }

  @Test
  void service_backward_compatibility_get_work_day_start() {
    // Given: settings with custom work day start
    UserSettings settings = new UserSettings(LocalTime.of(8, 45));
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(settings);
    repository.save(pref);

    // When: using AuthorizedUser as current user
    when(authorizedUser.getLoginSign()).thenReturn(testUser.getLoginname());

    // Then: backward compat method returns correct time
    LocalTime retrieved = service.getWorkDayStart();
    assertThat(retrieved).isEqualTo(LocalTime.of(8, 45));
  }

  @Test
  void service_backward_compatibility_save_work_day_start() {
    // Given: current user
    when(authorizedUser.getLoginSign()).thenReturn(testUser.getLoginname());

    // When: saving via legacy method
    service.saveForCurrentUser(LocalTime.of(6, 15));

    // Then: persisted as UserSettings
    UserSettings retrieved = service.getSettingsFor(testUser);
    assertThat(retrieved.workDayStart()).isEqualTo(LocalTime.of(6, 15));
  }

  @Test
  void should_enforce_unique_constraint_per_salat_user() {
    // Given: preference for user saved
    UserSettings settings1 = new UserSettings(LocalTime.of(9, 0));
    UserPreference pref1 = new UserPreference();
    pref1.setSalatUser(testUser);
    pref1.setSettings(settings1);
    repository.save(pref1);
    repository.flush();

    // When/Then: attempting second preference for same user should fail due to unique constraint
    UserPreference pref2 = new UserPreference();
    pref2.setSalatUser(testUser);
    pref2.setSettings(new UserSettings(LocalTime.of(10, 0)));

    assertThatThrownBy(() -> {
      repository.save(pref2);
      repository.flush();
    }).isNotNull(); // constraint violation thrown - message varies by locale/DB
  }

}
