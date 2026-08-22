package org.tb.settings.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Unveränderlicher Wertetyp. {@code equals}/{@code hashCode} sind zwingend erforderlich:
 * Das Attribut {@code UserPreference.settings} wird über einen {@link UserPreferenceConverter}
 * gemappt, und Hibernate entscheidet per {@code equals} gegen eine Kopie des Ladezustands,
 * ob die Entity schmutzig ist. Ohne Wertesemantik gilt sie bei jedem Flush als geändert und
 * erzeugt ein UPDATE — auch bei reinen GET-Requests.
 */
public final class UserPreferenceMap {

  private final Map<String, Map<String, Object>> modules;

  public UserPreferenceMap(Map<String, Map<String, Object>> modules) {
    this.modules = Map.copyOf(modules);
  }

  public static UserPreferenceMap empty() {
    return new UserPreferenceMap(Map.of());
  }

  public Map<String, Object> getModule(String key) {
    return modules.getOrDefault(key, Map.of());
  }

  public UserPreferenceMap withModule(String key, Map<String, Object> values) {
    var updated = new HashMap<>(modules);
    updated.put(key, Map.copyOf(values));
    return new UserPreferenceMap(updated);
  }

  public Map<String, Map<String, Object>> asRawMap() {
    return modules;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserPreferenceMap other)) return false;
    return modules.equals(other.modules);
  }

  @Override
  public int hashCode() {
    return Objects.hash(modules);
  }

}
