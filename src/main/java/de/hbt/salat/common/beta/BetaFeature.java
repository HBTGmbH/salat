package de.hbt.salat.common.beta;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An opt-in beta a person can switch on in the settings (#830, #1447). It belongs to the module whose
 * page carries it: that module declares it as a constant and contributes it through a
 * {@link BetaFeatureContributor}, the way modules contribute their UiState keys (ADR-0016) — the
 * beta module that switches, counts and evaluates knows none of them.
 *
 * <p>A beta runs <strong>without a fixed end date</strong>: the switch stays available until the
 * team decides either to make the feature the default or to withdraw it. Either way the constant
 * goes, and the compiler points at every remaining usage.
 *
 * <p>Keys stored for users are matched by {@link #getKey()}; the key of a beta that has since been
 * removed is silently dropped when the preferences are read, so no cleanup migration is needed.
 *
 * <p>How a beta is introduced, promoted on its page, measured and ended:
 * {@code docs/beta-funktionen.md}.
 */
public final class BetaFeature {

  /** Lower case letters, digits and dashes; the key goes into message keys and element ids. */
  private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

  private final String key;
  private final int feedbackAfterUses;

  /**
   * @param key               the key the switch is stored under; never change it while the beta runs
   * @param feedbackAfterUses after how many counted uses with the beta switched on a person is asked
   *                          how helpful it is
   */
  public BetaFeature(String key, int feedbackAfterUses) {
    if (key == null || !KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("beta key must be lower case letters, digits and dashes: " + key);
    }
    if (feedbackAfterUses < 1) {
      throw new IllegalArgumentException("a beta asks after at least one use: " + key);
    }
    this.key = key;
    this.feedbackAfterUses = feedbackAfterUses;
  }

  public String getKey() {
    return key;
  }

  public int getFeedbackAfterUses() {
    return feedbackAfterUses;
  }

  /** Message key of the switch label on the settings page. */
  public String labelKey() {
    return "main.settings.beta." + key + ".label";
  }

  /** Message key of the one-line description below the switch on the settings page. */
  public String helpKey() {
    return "main.settings.beta." + key + ".help";
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BetaFeature feature && feature.key.equals(key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key);
  }

  @Override
  public String toString() {
    return key;
  }
}
