package de.hbt.salat.beta.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The keys of the beta features a single user has switched on, stored as one module section of
 * {@link de.hbt.salat.settings.domain.UserPreferenceMap}.
 *
 * <p>Modelled as a set rather than one flag per feature so that the next beta costs no schema and
 * no form change. It holds keys (#1447): the betas come from the modules, and which keys are still a
 * beta the caller decides with {@code known}.
 */
public record BetaFeatures(Set<String> keys) {

  public static final String MODULE_KEY = "beta";
  static final String KEY_ENABLED = "enabled";

  public BetaFeatures(Set<String> keys) {
    this.keys = keys == null ? Set.of() : Set.copyOf(keys);
  }

  public static BetaFeatures none() {
    return new BetaFeatures(Set.of());
  }

  /** Builds the set from raw keys, ignoring the keys of features that no longer exist. */
  public static BetaFeatures ofKeys(Collection<String> keys, Predicate<String> known) {
    if (keys == null || keys.isEmpty()) {
      return none();
    }
    return new BetaFeatures(Set.copyOf(keys.stream().filter(Objects::nonNull).filter(known).toList()));
  }

  public static BetaFeatures from(Map<String, Object> module, Predicate<String> known) {
    if (module == null) {
      return none();
    }
    // Jackson deserializes the JSON array into a List<String>
    if (module.get(KEY_ENABLED) instanceof Collection<?> values) {
      return ofKeys(values.stream().filter(Objects::nonNull).map(Object::toString).toList(), known);
    }
    return none();
  }

  public boolean has(String key) {
    return keys.contains(key);
  }

  public BetaFeatures with(String key) {
    if (has(key)) {
      return this;
    }
    var withKey = new TreeSet<>(keys);
    withKey.add(key);
    return new BetaFeatures(withKey);
  }

  /**
   * An empty map is returned when nothing is enabled, so that switching every beta off leaves no
   * leftover section behind — same convention as {@code UiPreferenceService} uses for the browser
   * locale default.
   */
  public Map<String, Object> toMap() {
    if (keys.isEmpty()) {
      return Map.of();
    }
    return Map.of(KEY_ENABLED, keys.stream().sorted().toList());
  }

}
