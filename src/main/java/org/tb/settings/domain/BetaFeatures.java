package org.tb.settings.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The set of beta features a single user has switched on, stored as one module section of
 * {@link UserPreferenceMap}.
 *
 * <p>Modelled as a set rather than one flag per feature so that the next beta costs no schema and
 * no form change.
 */
public record BetaFeatures(Set<BetaFeature> enabled) {

  public static final String MODULE_KEY = "beta";
  static final String KEY_ENABLED = "enabled";

  public BetaFeatures(Set<BetaFeature> enabled) {
    this.enabled = enabled == null ? Set.of() : Set.copyOf(enabled);
  }

  public static BetaFeatures none() {
    return new BetaFeatures(Set.of());
  }

  /** Builds the set from raw preference keys, ignoring keys of features that no longer exist. */
  public static BetaFeatures ofKeys(Collection<String> keys) {
    if (keys == null || keys.isEmpty()) {
      return none();
    }
    var features = EnumSet.noneOf(BetaFeature.class);
    keys.forEach(key -> BetaFeature.ofKey(key).ifPresent(features::add));
    return new BetaFeatures(features);
  }

  public static BetaFeatures from(Map<String, Object> module) {
    if (module == null) {
      return none();
    }
    // Jackson deserializes the JSON array into a List<String>
    if (module.get(KEY_ENABLED) instanceof Collection<?> values) {
      return ofKeys(values.stream().filter(java.util.Objects::nonNull).map(Object::toString).toList());
    }
    return none();
  }

  public boolean has(BetaFeature feature) {
    return enabled.contains(feature);
  }

  public BetaFeatures with(BetaFeature feature) {
    if (has(feature)) {
      return this;
    }
    var features = EnumSet.noneOf(BetaFeature.class);
    features.addAll(enabled);
    features.add(feature);
    return new BetaFeatures(features);
  }

  /**
   * An empty map is returned when nothing is enabled, so that switching every beta off leaves no
   * leftover section behind — same convention as {@code UiPreferenceService} uses for the browser
   * locale default.
   */
  public Map<String, Object> toMap() {
    if (enabled.isEmpty()) {
      return Map.of();
    }
    return Map.of(KEY_ENABLED, enabled.stream().map(BetaFeature::getKey).sorted().toList());
  }

}
