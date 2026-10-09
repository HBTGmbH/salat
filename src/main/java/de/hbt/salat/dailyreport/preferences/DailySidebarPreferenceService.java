package de.hbt.salat.dailyreport.preferences;

import static de.hbt.salat.dailyreport.service.DailyReportBetaFeatureContributor.FAVORITES_FIRST;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.beta.Betas;
import de.hbt.salat.settings.service.UserPreferenceService;

/** The settings of the beta „Favoriten zuerst“ for the sidebar of the daily view (#1442). */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class DailySidebarPreferenceService {

  private final UserPreferenceService userPreferenceService;
  private final Betas betas;

  /** What the logged-in person chose, for the settings; the defaults of the beta until they choose. */
  @Transactional(readOnly = true)
  public DailySidebarPreferences getForCurrentUser() {
    return DailySidebarPreferences.from(userPreferenceService.getModuleSettings(DailySidebarPreferences.MODULE_KEY));
  }

  /**
   * What the daily view follows: the choice while the beta is on, otherwise the sidebar without it,
   * whatever was stored.
   */
  @Transactional(readOnly = true)
  public DailySidebarPreferences getEffectiveForCurrentUser() {
    return betas.isEnabled(FAVORITES_FIRST) ? getForCurrentUser() : DailySidebarPreferences.classic();
  }

  public void saveForCurrentUser(DailySidebarPreferences preferences) {
    userPreferenceService.saveModuleSettings(DailySidebarPreferences.MODULE_KEY, preferences.toMap());
  }
}
