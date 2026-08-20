package org.tb.settings.service;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.settings.domain.BetaFeature;
import org.tb.settings.domain.BetaFeatures;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class BetaFeatureService {

  private final UserPreferenceService userPreferenceService;

  @Transactional(readOnly = true)
  public BetaFeatures getForCurrentUser() {
    return BetaFeatures.from(userPreferenceService.getModuleSettings(BetaFeatures.MODULE_KEY));
  }

  @Transactional(readOnly = true)
  public boolean isEnabledForCurrentUser(BetaFeature feature) {
    return getForCurrentUser().has(feature);
  }

  public void saveForCurrentUser(Collection<String> featureKeys) {
    userPreferenceService.saveModuleSettings(BetaFeatures.MODULE_KEY,
        BetaFeatures.ofKeys(featureKeys).toMap());
  }

  /** Used by the in-context activation link, which switches on a single feature. */
  public void enableForCurrentUser(BetaFeature feature) {
    var current = getForCurrentUser();
    if (current.has(feature)) {
      return;
    }
    userPreferenceService.saveModuleSettings(BetaFeatures.MODULE_KEY, current.with(feature).toMap());
  }

}
