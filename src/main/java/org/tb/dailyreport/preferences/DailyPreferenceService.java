package org.tb.dailyreport.preferences;

import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.SalatUser;
import org.tb.employee.service.EmployeecontractService;
import org.tb.settings.service.UserPreferenceService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class DailyPreferenceService {

  private final UserPreferenceService userPreferenceService;
  private final EmployeecontractService employeecontractService;

  @Transactional(readOnly = true)
  public DailyPreferences getForCurrentUser() {
    return DailyPreferences.from(
        userPreferenceService.getModuleSettings(DailyPreferences.MODULE_KEY));
  }

  @Transactional(readOnly = true)
  public DailyPreferences getForEmployeeContractId(long employeeContractId) {
    var contract = employeecontractService.getEmployeecontractById(employeeContractId);
    return DailyPreferences.from(
        userPreferenceService.getModuleSettings(contract.getEmployee().getSalatUser(), DailyPreferences.MODULE_KEY));
  }

  public void saveForCurrentUser(DailyPreferences preferences) {
    userPreferenceService.saveModuleSettings(DailyPreferences.MODULE_KEY, preferences.toMap());
  }

}
