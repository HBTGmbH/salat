package org.tb.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;
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
import org.tb.settings.domain.UserPreferenceMap;
import org.tb.settings.persistence.UserPreferenceRepository;
import org.tb.settings.service.UserPreferenceService;

@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({ UserPreferenceService.class })
public class UserPreferenceIntegrationTest {

  private static final String MODULE = "daily";
  private static final Map<String, Object> SETTINGS = Map.of("workDayStart", "08:30");

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
  void should_serialize_and_deserialize_module_settings_as_json() {
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(new UserPreferenceMap(Map.of(MODULE, SETTINGS)));
    UserPreference saved = repository.save(pref);
    repository.flush();

    UserPreference loaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getSettings().getModule(MODULE)).isEqualTo(SETTINGS);
  }

  @Test
  void should_default_to_empty_settings_when_creating_new_preference() {
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    UserPreference saved = repository.save(pref);
    repository.flush();

    UserPreference loaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getSettings().asRawMap()).isEmpty();
  }

  @Test
  void should_find_preference_by_salat_user() {
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(new UserPreferenceMap(Map.of(MODULE, SETTINGS)));
    repository.save(pref);
    repository.flush();

    UserPreference found = repository.findBySalatUser(testUser).orElseThrow();
    assertThat(found.getSettings().getModule(MODULE)).isEqualTo(SETTINGS);
  }

  @Test
  void should_find_preference_by_salat_user_id() {
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(new UserPreferenceMap(Map.of(MODULE, SETTINGS)));
    repository.save(pref);
    repository.flush();

    UserPreference found = repository.findBySalatUserId(testUser.getId()).orElseThrow();
    assertThat(found.getSettings().getModule(MODULE)).isEqualTo(SETTINGS);
  }

  @Test
  void should_update_existing_preference() {
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(new UserPreferenceMap(Map.of(MODULE, Map.of("workDayStart", "09:00"))));
    UserPreference saved = repository.save(pref);
    repository.flush();

    saved.setSettings(new UserPreferenceMap(Map.of(MODULE, Map.of("workDayStart", "06:30"))));
    repository.save(saved);
    repository.flush();

    UserPreference reloaded = repository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getSettings().getModule(MODULE)).containsEntry("workDayStart", "06:30");
  }

  @Test
  void service_should_get_module_settings_for_salat_user() {
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(new UserPreferenceMap(Map.of(MODULE, SETTINGS)));
    repository.save(pref);

    Map<String, Object> retrieved = service.getModuleSettings(testUser, MODULE);

    assertThat(retrieved).isEqualTo(SETTINGS);
  }

  @Test
  void service_should_get_module_settings_for_salat_user_id() {
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(new UserPreferenceMap(Map.of(MODULE, SETTINGS)));
    repository.save(pref);

    Map<String, Object> retrieved = service.getModuleSettings(testUser.getId(), MODULE);

    assertThat(retrieved).isEqualTo(SETTINGS);
  }

  @Test
  void service_should_get_module_settings_for_login_name() {
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(new UserPreferenceMap(Map.of(MODULE, SETTINGS)));
    repository.save(pref);

    Map<String, Object> retrieved = service.getModuleSettings(testUser.getLoginname(), MODULE);

    assertThat(retrieved).isEqualTo(SETTINGS);
  }

  @Test
  void service_should_return_empty_map_for_missing_preference() {
    SalatUser otherUser = new SalatUser();
    otherUser.setLoginname("other");
    otherUser.setStatus("ma");
    salatUserRepository.save(otherUser);

    Map<String, Object> retrieved = service.getModuleSettings(otherUser, MODULE);

    assertThat(retrieved).isEmpty();
  }

  @Test
  void service_should_throw_on_invalid_login_name() {
    assertThatThrownBy(() -> service.getModuleSettings("nonexistent", MODULE))
        .isInstanceOf(InvalidDataException.class);
  }

  @Test
  void service_should_save_module_settings_for_current_user() {
    when(authorizedUser.getLoginSign()).thenReturn(testUser.getLoginname());

    service.saveModuleSettings(MODULE, SETTINGS);

    UserPreference found = repository.findBySalatUser(testUser).orElseThrow();
    assertThat(found.getSettings().getModule(MODULE)).isEqualTo(SETTINGS);
  }

  @Test
  void service_should_update_existing_module_settings() {
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(new UserPreferenceMap(Map.of(MODULE, Map.of("workDayStart", "09:00"))));
    repository.save(pref);
    when(authorizedUser.getLoginSign()).thenReturn(testUser.getLoginname());

    service.saveModuleSettings(MODULE, Map.of("workDayStart", "12:30"));

    assertThat(repository.findBySalatUser(testUser))
        .isPresent()
        .get()
        .extracting(p -> p.getSettings().getModule(MODULE))
        .isEqualTo(Map.of("workDayStart", "12:30"));
  }

  @Test
  void service_should_preserve_other_module_settings_on_save() {
    UserPreference pref = new UserPreference();
    pref.setSalatUser(testUser);
    pref.setSettings(new UserPreferenceMap(Map.of(
        MODULE, Map.of("workDayStart", "09:00"),
        "other", Map.of("key", "value"))));
    repository.save(pref);
    when(authorizedUser.getLoginSign()).thenReturn(testUser.getLoginname());

    service.saveModuleSettings(MODULE, Map.of("workDayStart", "08:00"));

    UserPreference updated = repository.findBySalatUser(testUser).orElseThrow();
    assertThat(updated.getSettings().getModule("other")).containsEntry("key", "value");
  }

  @Test
  void should_enforce_unique_constraint_per_salat_user() {
    UserPreference pref1 = new UserPreference();
    pref1.setSalatUser(testUser);
    repository.save(pref1);
    repository.flush();

    UserPreference pref2 = new UserPreference();
    pref2.setSalatUser(testUser);

    assertThatThrownBy(() -> {
      repository.save(pref2);
      repository.flush();
    }).isNotNull();
  }

}
