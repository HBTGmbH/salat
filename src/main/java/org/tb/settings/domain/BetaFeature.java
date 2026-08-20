package org.tb.settings.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Opt-in beta features a user can switch on in the settings.
 *
 * <p>A beta feature runs <strong>without a fixed end date</strong>: the switch stays available until
 * the team decides either to make the feature the default (constant removed, classic branch deleted)
 * or to withdraw it (constant removed, feature deleted). Modelling the keys as an enum rather than
 * free-form strings means that whenever that decision comes, deleting the constant makes the
 * compiler point at every remaining usage.
 *
 * <p>Keys stored for users are matched by {@link #getKey()}; unknown keys — a beta that has since
 * been removed — are silently dropped when the preferences are read, so no cleanup migration is
 * needed.
 */
public enum BetaFeature {

  /**
   * #830 — stepper with 15 minute grid, additive quick-add chips and keyboard stepping for time and
   * duration fields. Feedback goes to the Slack channel #salat.
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
