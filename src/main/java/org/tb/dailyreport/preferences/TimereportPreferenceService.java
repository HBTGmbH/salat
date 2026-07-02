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
    saveForCurrentUser(new TimereportPreferences(newFavorite));
  }

}
