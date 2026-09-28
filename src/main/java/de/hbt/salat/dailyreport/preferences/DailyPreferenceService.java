package de.hbt.salat.dailyreport.preferences;

import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.settings.service.UserPreferenceService;

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
