package org.tb.dailyreport.preferences;

import java.util.Map;

public record TimereportPreferences(Long favoriteSuborderId) {

  public static final String MODULE_KEY = "timereport";
  static final String KEY_FAVORITE_SUBORDER_ID = "favoriteSuborderId";

  public static TimereportPreferences defaults() {
    return new TimereportPreferences(null);
  }

  public static TimereportPreferences from(Map<String, Object> map) {
    try {
      var val = map.get(KEY_FAVORITE_SUBORDER_ID);
      Long id = val != null ? Long.parseLong(val.toString()) : null;
      return new TimereportPreferences(id);
    } catch (Exception e) {
      return defaults();
    }
  }

  public Map<String, Object> toMap() {
    if (favoriteSuborderId == null) return Map.of();
    return Map.of(KEY_FAVORITE_SUBORDER_ID, favoriteSuborderId.toString());
  }

}
