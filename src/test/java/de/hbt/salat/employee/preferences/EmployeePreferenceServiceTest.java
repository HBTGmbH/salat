package de.hbt.salat.employee.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static de.hbt.salat.employee.preferences.EmployeePreferences.KEY_GRAVATAR_EMAIL;
import static de.hbt.salat.employee.preferences.EmployeePreferences.KEY_NOTIFICATION_EMAIL;
import static de.hbt.salat.employee.preferences.EmployeePreferences.MODULE_KEY;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.settings.service.UserPreferenceService;

/**
 * Ein leeres Gravatar-Feld bedeutet die Standardadresse, so wie die Einstellungen es zusagen
 * (#1034).
 */
@ExtendWith(MockitoExtension.class)
class EmployeePreferenceServiceTest {

  private static final long EMPLOYEE_ID = 3L;
  private static final long SALAT_USER_ID = 9L;

  @Mock
  private UserPreferenceService userPreferenceService;

  @Mock
  private AuthorizedEmployee authorizedEmployee;

  @Mock
  private EmployeeRepository employeeRepository;

  private EmployeePreferenceService service;

  @BeforeEach
  void setUp() {
    service = new EmployeePreferenceService(userPreferenceService, authorizedEmployee, employeeRepository);
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

  /**
   * Ein anderes Modul kennt die Person nur ueber die id (#1340): hinterlegte Adresse vor der
   * Standardadresse aus dem Kuerzel, wie bei der Abfrage ueber den Mitarbeitenden.
   */
  @Test
  void the_notification_address_by_id_prefers_the_stored_one() {
    var employee = employeeWithLogin("abc");
    when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
    when(userPreferenceService.getModuleSettings(SALAT_USER_ID, MODULE_KEY))
        .thenReturn(Map.of(KEY_NOTIFICATION_EMAIL, "notify@example.com"));

    assertThat(service.getNotificationEmailForEmployeeId(EMPLOYEE_ID)).isEqualTo("notify@example.com");
  }

  @Test
  void the_notification_address_by_id_falls_back_to_the_default_address() {
    var employee = employeeWithLogin("abc");
    when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
    when(userPreferenceService.getModuleSettings(SALAT_USER_ID, MODULE_KEY)).thenReturn(Map.of());

    assertThat(service.getNotificationEmailForEmployeeId(EMPLOYEE_ID)).isEqualTo("abc@hbt.de");
  }

  @Test
  void an_unknown_id_has_no_notification_address() {
    when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

    assertThat(service.getNotificationEmailForEmployeeId(EMPLOYEE_ID)).isNull();
  }

  private static Employee employeeWithLogin(String sign) {
    var employee = new Employee();
    employee.setSign(sign);
    var salatUser = new SalatUser();
    setField(salatUser, "id", SALAT_USER_ID);
    employee.setSalatUser(salatUser);
    return employee;
  }

  private void stored(Map<String, Object> settings) {
    when(userPreferenceService.getModuleSettings(MODULE_KEY)).thenReturn(settings);
  }

}
