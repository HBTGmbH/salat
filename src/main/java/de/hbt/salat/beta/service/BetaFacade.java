package de.hbt.salat.beta.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.beta.BetaFeature;
import de.hbt.salat.common.beta.Betas;

/**
 * {@link Betas} for the modules (#1447): they ask through {@code common.beta} and never import the
 * beta module, as they contribute their search hits to the palette.
 */
@Component
@RequiredArgsConstructor
class BetaFacade implements Betas {

  private final BetaFeatureService betaFeatureService;
  private final BetaUsageService betaUsageService;

  @Override
  public boolean isEnabled(BetaFeature feature) {
    return betaFeatureService.isEnabledForCurrentUser(feature);
  }

  @Override
  public void count(BetaFeature feature, String event) {
    betaUsageService.count(feature, event);
  }
}
