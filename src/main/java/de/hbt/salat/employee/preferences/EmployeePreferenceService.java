package de.hbt.salat.employee.preferences;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.settings.service.UserPreferenceService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class EmployeePreferenceService {

  private final UserPreferenceService userPreferenceService;
  private final AuthorizedEmployee authorizedEmployee;
  private final EmployeeRepository employeeRepository;

  @Transactional(readOnly = true)
  public EmployeePreferences getForCurrentUser() {
    return EmployeePreferences.from(
        userPreferenceService.getModuleSettings(EmployeePreferences.MODULE_KEY));
  }

  @Transactional(readOnly = true)
  public EmployeePreferences getForEmployee(Employee employee) {
    return preferencesOf(employee);
  }

  @Transactional(readOnly = true)
  public String getGravatarEmailForCurrentUser() {
    // nach dem Kuerzel erst fragen, wenn nichts hinterlegt ist: AuthorizedEmployee gibt es nur
    // innerhalb einer Anfrage, und eine hinterlegte Adresse braucht es nicht
    String preference = getForCurrentUser().gravatarEmail();
    return isSet(preference) ? preference : defaultEmailForCurrentUser();
  }

  @Transactional(readOnly = true)
  public String getNotificationEmailFor(Employee employee) {
    return notificationEmailOf(employee);
  }

  /**
   * {@link #getNotificationEmailFor(Employee)} by the id, for a module that knows the person only as
   * values (#1340, ADR-0021); {@code null} when there is no employee with this id.
   */
  @Transactional(readOnly = true)
  public String getNotificationEmailForEmployeeId(long employeeId) {
    return employeeRepository.findById(employeeId)
        .map(this::notificationEmailOf)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public String getGravatarEmailFor(Employee employee) {
    return orDefault(getForEmployee(employee).gravatarEmail(), defaultEmailFor(employee));
  }

  public String defaultEmailFor(Employee employee) {
    if (employee == null) return null;
    return defaultEmail(employee.getSign());
  }

  /**
   * Die Standardadresse der wirksamen Anmeldung - bei einem Loginwechsel also die der Person, deren
   * Name in der Seitenleiste steht. AuthorizedEmployee traegt genau diese: der
   * AuthorizedEmployeeFilter laedt sie zu Beginn jeder Anfrage ueber den wirksamen Loginnamen. Das
   * Kuerzel von dort zu nehmen spart die Abfrage, die ein erneutes Laden des Mitarbeitenden kosten
   * wuerde - die Seitenleiste ruft das je Seitenaufruf dreimal auf (#1034).
   *
   * <p>Findet der Filter keinen Mitarbeitenden zum Loginnamen, bleibt das Kuerzel leer; dann gibt es
   * keine Standardadresse und es bleibt beim hinterlegten Wert bzw. bei {@code null}.
   */
  private String defaultEmailForCurrentUser() {
    String sign = authorizedEmployee.getSign();
    return sign != null ? defaultEmail(sign) : null;
  }

  /** Read by the id of the login, so that the entity of the module {@code auth} does not travel (#1340). */
  private EmployeePreferences preferencesOf(Employee employee) {
    if (employee.getSalatUser() == null) return EmployeePreferences.defaults();
    return EmployeePreferences.from(
        userPreferenceService.getModuleSettings(employee.getSalatUser().getId(), EmployeePreferences.MODULE_KEY));
  }

  private String notificationEmailOf(Employee employee) {
    return orDefault(preferencesOf(employee).notificationEmail(), defaultEmail(employee.getSign()));
  }

  private static String defaultEmail(String sign) {
    return sign + "@" + GlobalConstants.MAIL_DOMAIN;
  }

  private static String orDefault(String preference, String defaultEmail) {
    return isSet(preference) ? preference : defaultEmail;
  }

  private static boolean isSet(String preference) {
    return preference != null && !preference.isBlank();
  }

  public void saveForCurrentUser(EmployeePreferences preferences) {
    userPreferenceService.saveModuleSettings(EmployeePreferences.MODULE_KEY, preferences.toMap());
  }

}
