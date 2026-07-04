package org.tb.employee.preferences;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.settings.service.UserPreferenceService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class EmployeePreferenceService {

  private final UserPreferenceService userPreferenceService;

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
    return getForCurrentUser().gravatarEmail();
  }

  @Transactional(readOnly = true)
  public String getNotificationEmailFor(Employee employee) {
    String preference = getForEmployee(employee).notificationEmail();
    return (preference != null && !preference.isBlank()) ? preference : defaultEmailFor(employee);
  }

  @Transactional(readOnly = true)
  public String getGravatarEmailFor(Employee employee) {
    String preference = getForEmployee(employee).gravatarEmail();
    return (preference != null && !preference.isBlank()) ? preference : defaultEmailFor(employee);
  }

  public String defaultEmailFor(Employee employee) {
    if (employee == null) return null;
    return employee.getSign() + "@" + GlobalConstants.MAIL_DOMAIN;
  }

  public void saveForCurrentUser(EmployeePreferences preferences) {
    userPreferenceService.saveModuleSettings(EmployeePreferences.MODULE_KEY, preferences.toMap());
  }

}
