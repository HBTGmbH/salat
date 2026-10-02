package de.hbt.salat.settings.domain;

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
 *
 * <p>There is currently no beta. The enum stays, empty, so that the next one costs a constant, its
 * two texts ({@link #labelKey()}, {@link #helpKey()}) and a named getter in {@code BetaViewHelper};
 * the settings page shows its switch section only while there is a constant. The last beta was the
 * time and duration input of #830 ({@code TIME_INPUT}), made the default with #1248.
 */
public enum BetaFeature {
  ;

  private final String key;

  BetaFeature(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }

  /** Message key of the switch label on the settings page. */
  public String labelKey() {
    return "main.settings.beta." + key + ".label";
  }

  /** Message key of the one-line description below the switch on the settings page. */
  public String helpKey() {
    return "main.settings.beta." + key + ".help";
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
