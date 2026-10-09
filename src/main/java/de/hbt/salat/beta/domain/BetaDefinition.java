package de.hbt.salat.beta.domain;

import java.util.Set;

/**
 * What the beta module knows about a beta (#1447): its key, the events counted for it and after how
 * many uses a person is asked for feedback. Built from a {@link BetaFeature} constant; the services
 * work on it rather than on the enum, so that they can be tested while the enum is empty.
 *
 * @param key               the key the switch is stored under
 * @param events            the events counted for it; any other is ignored
 * @param feedbackAfterUses how many counted uses with the beta switched on lead to the question how
 *                          helpful it is
 */
public record BetaDefinition(String key, Set<String> events, int feedbackAfterUses) {

  public BetaDefinition {
    events = Set.copyOf(events);
  }

  public boolean declares(String event) {
    return event != null && events.contains(event);
  }

  public String labelKey() {
    return labelKeyOf(key);
  }

  static String labelKeyOf(String key) {
    return "main.settings.beta." + key + ".label";
  }

  static String helpKeyOf(String key) {
    return "main.settings.beta." + key + ".help";
  }
}
