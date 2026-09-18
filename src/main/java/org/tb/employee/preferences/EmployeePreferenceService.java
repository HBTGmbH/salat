package org.tb.employee.preferences;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employee;
import org.tb.settings.service.UserPreferenceService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class EmployeePreferenceService {

  private final UserPreferenceService userPreferenceService;
  private final AuthorizedEmployee authorizedEmployee;

  @Transactional(readOnly = true)
  public EmployeePreferences getForCurrentUser() {
    return EmployeePreferences.from(
        userPreferenceService.getModuleSettings(EmployeePreferences.MODULE_KEY));
  }

  @Transactional(readOnly = true)
  public EmployeePreferences getForEmployee(Employee employee) {
    if (employee.getSalatUser() == null) return EmployeePreferences.defaults();
    return EmployeePreferences.from(
        userPreferenceService.getModuleSettings(employee.getSalatUser(), EmployeePreferences.MODULE_KEY));
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
    return orDefault(getForEmployee(employee).notificationEmail(), defaultEmailFor(employee));
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
