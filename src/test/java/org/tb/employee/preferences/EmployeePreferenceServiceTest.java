package org.tb.employee.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.tb.employee.preferences.EmployeePreferences.KEY_GRAVATAR_EMAIL;
import static org.tb.employee.preferences.EmployeePreferences.MODULE_KEY;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.settings.service.UserPreferenceService;

/**
 * Ein leeres Gravatar-Feld bedeutet die Standardadresse, so wie die Einstellungen es zusagen
 * (#1034).
 */
@ExtendWith(MockitoExtension.class)
class EmployeePreferenceServiceTest {

  @Mock
  private UserPreferenceService userPreferenceService;

  @Mock
  private AuthorizedEmployee authorizedEmployee;

  private EmployeePreferenceService service;

  @BeforeEach
  void setUp() {
    service = new EmployeePreferenceService(userPreferenceService, authorizedEmployee);
    lenient().when(authorizedEmployee.getSign()).thenReturn("xyz");
  }

  @Test
  void a_stored_address_wins() {
    stored(Map.of(KEY_GRAVATAR_EMAIL, "gravatar@example.com"));

    assertThat(service.getGravatarEmailForCurrentUser()).isEqualTo("gravatar@example.com");
  }

  @Test
  void nothing_stored_falls_back_to_the_default_address() {
    stored(Map.of());

    assertThat(service.getGravatarEmailForCurrentUser()).isEqualTo("xyz@hbt.de");
  }

  @Test
  void an_empty_field_falls_back_to_the_default_address() {
    stored(Map.of(KEY_GRAVATAR_EMAIL, ""));

    assertThat(service.getGravatarEmailForCurrentUser()).isEqualTo("xyz@hbt.de");
  }

  @Test
  void a_field_of_blanks_falls_back_to_the_default_address() {
    stored(Map.of(KEY_GRAVATAR_EMAIL, "   "));

    assertThat(service.getGravatarEmailForCurrentUser()).isEqualTo("xyz@hbt.de");
  }

  /**
   * Nach einem Loginwechsel traegt AuthorizedEmployee die wirksame Anmeldung - die Standardadresse
   * gehoert zu der Person, deren Name die Seitenleiste zeigt, nicht zur urspruenglichen.
   */
  @Test
  void the_default_address_follows_the_effective_login() {
    when(authorizedEmployee.getSign()).thenReturn("abc");
    stored(Map.of());

    assertThat(service.getGravatarEmailForCurrentUser()).isEqualTo("abc@hbt.de");
  }

  /**
   * Findet der AuthorizedEmployeeFilter keinen Mitarbeitenden zum Loginnamen, bleibt das Kuerzel
   * leer. Dann gibt es keine Standardadresse - und keine aus dem leeren Kuerzel gebaute Adresse.
   */
  @Test
  void without_an_employee_there_is_no_default_address() {
    when(authorizedEmployee.getSign()).thenReturn(null);
    stored(Map.of());

    assertThat(service.getGravatarEmailForCurrentUser()).isNull();
  }

  private void stored(Map<String, Object> settings) {
    when(userPreferenceService.getModuleSettings(MODULE_KEY)).thenReturn(settings);
  }

}
