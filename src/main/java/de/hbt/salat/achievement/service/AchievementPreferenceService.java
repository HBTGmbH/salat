package de.hbt.salat.achievement.service;

import de.hbt.salat.achievement.domain.AchievementPreferences;
import de.hbt.salat.achievement.persistence.AchievementRepository;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.settings.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Whether a person is told about new achievements (#1501), stored as a section of the preferences. */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class AchievementPreferenceService {

  private final UserPreferenceService userPreferenceService;
  private final AchievementRepository achievementRepository;
  private final AuthorizedEmployee authorizedEmployee;

  @Transactional(readOnly = true)
  public AchievementPreferences getForCurrentUser() {
    return AchievementPreferences.from(userPreferenceService.getModuleSettings(AchievementPreferences.MODULE_KEY));
  }

  /**
   * Switched off, what is still waiting to be announced counts as seen: otherwise the popup would
   * come with the next page anyway.
   */
  public void saveForCurrentUser(AchievementPreferences preferences) {
    userPreferenceService.saveModuleSettings(AchievementPreferences.MODULE_KEY, preferences.toMap());
    var employeeId = authorizedEmployee.getEmployeeId();
    if (!preferences.announce() && employeeId != null) {
      achievementRepository.markSeen(employeeId, ClockProvider.now());
    }
  }

  /** For the nightly job, which knows the person by the id of the login. */
  @Authorized(requiresManager = true)
  @Transactional(readOnly = true)
  public boolean isAnnouncedTo(long salatUserId) {
    return AchievementPreferences.from(
        userPreferenceService.getModuleSettings(salatUserId, AchievementPreferences.MODULE_KEY)).announce();
  }
}
