package org.tb.settings.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Opt-in beta features a user can switch on in the settings.
 *
 * <p>Every constant here is <strong>temporary by design</strong>. A beta feature is introduced
 * together with an agreed end date and is resolved by exactly one of two actions: the feature
 * becomes the default and the constant is removed, or the feature is withdrawn and the constant is
 * removed. Deleting the constant makes the compiler point at every remaining usage, which is the
 * whole reason the keys are modelled as an enum instead of free-form strings.
 *
 * <p>Keys stored for users are matched by {@link #getKey()}; unknown keys (a beta that has since
 * been removed) are silently dropped when the preferences are read, so no cleanup migration is
 * needed.
 */
public enum BetaFeature {

  /**
   * #830 — stepper with 15 minute grid, additive quick-add chips and keyboard stepping for time and
   * duration fields. End of beta: see issue #830.
   */
  TIME_INPUT("timeinput");

  private final String key;

  BetaFeature(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }

  public static Optional<BetaFeature> ofKey(String key) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(feature -> feature.key.equals(key))
        .findFirst();
  }

}
