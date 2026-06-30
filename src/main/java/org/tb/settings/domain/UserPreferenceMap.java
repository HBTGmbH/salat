package org.tb.settings.domain;

import java.util.HashMap;
import java.util.Map;

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

}
