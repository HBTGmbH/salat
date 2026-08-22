package org.tb.dailyreport.preferences;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.settings.service.UserPreferenceService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class TimereportPreferenceService {

  private final UserPreferenceService userPreferenceService;

  @Transactional(readOnly = true)
  public TimereportPreferences getForCurrentUser() {
    return TimereportPreferences.from(
        userPreferenceService.getModuleSettings(TimereportPreferences.MODULE_KEY));
  }

  public void saveForCurrentUser(TimereportPreferences preferences) {
    userPreferenceService.saveModuleSettings(TimereportPreferences.MODULE_KEY, preferences.toMap());
  }

  public void toggleFavoriteSuborder(Long suborderId) {
    var current = getForCurrentUser();
    Long newFavorite = suborderId != null && suborderId.equals(current.favoriteSuborderId())
        ? null
        : suborderId;
    saveForCurrentUser(new TimereportPreferences(
        newFavorite, current.durationInputMode(), current.lastUsedDurationMode()));
  }

  /**
   * Records the entry mode a booking was created with (#844). A no-op unless the user left the
   * preference on {@link DurationInputMode#REMEMBER} — an explicitly chosen mode must not be
   * overwritten by booking in the other one once.
   */
  public void rememberDurationMode(String formDurationMode) {
    var current = getForCurrentUser();
    if (current.durationInputMode() != DurationInputMode.REMEMBER) {
      return;
    }
    var used = DurationInputMode.ofFormValue(formDurationMode);
    if (used == current.lastUsedDurationMode()) {
      return;
    }
    saveForCurrentUser(new TimereportPreferences(
        current.favoriteSuborderId(), current.durationInputMode(), used));
  }

}
