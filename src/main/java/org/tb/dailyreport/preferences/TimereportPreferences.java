package org.tb.dailyreport.preferences;

import static org.tb.dailyreport.preferences.DurationInputMode.REMEMBER;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @param favoriteSuborderId    preselected suborder of the booking form, {@code null} if unset
 * @param durationInputMode     how the "Dauer oder Beginn / Ende" toggle is preset (#844)
 * @param lastUsedDurationMode  the mode of the last created booking; the effective preset while
 *                              {@code durationInputMode} is {@link DurationInputMode#REMEMBER}.
 *                              {@code null} until the user has created their first booking, never
 *                              {@code REMEMBER} itself.
 */
public record TimereportPreferences(Long favoriteSuborderId,
                                    DurationInputMode durationInputMode,
                                    DurationInputMode lastUsedDurationMode) {

  public static final String MODULE_KEY = "timereport";
  static final String KEY_FAVORITE_SUBORDER_ID = "favoriteSuborderId";
  static final String KEY_DURATION_INPUT_MODE = "durationInputMode";
  static final String KEY_LAST_USED_DURATION_MODE = "lastUsedDurationMode";

  public static TimereportPreferences defaults() {
    return new TimereportPreferences(null, REMEMBER, null);
  }

  public static TimereportPreferences from(Map<String, Object> map) {
    // every key is parsed on its own: a malformed favourite must not cost the entry mode as well
    return new TimereportPreferences(
        favoriteSuborderIdOf(map),
        modeOf(map, KEY_DURATION_INPUT_MODE).orElse(REMEMBER),
        modeOf(map, KEY_LAST_USED_DURATION_MODE)
            .filter(mode -> mode != REMEMBER)
            .orElse(null));
  }

  public Map<String, Object> toMap() {
    // defaults are omitted so that resetting a setting leaves no leftover JSON behind
    var values = new LinkedHashMap<String, Object>();
    if (favoriteSuborderId != null) {
      values.put(KEY_FAVORITE_SUBORDER_ID, favoriteSuborderId.toString());
    }
    if (durationInputMode != REMEMBER) {
      values.put(KEY_DURATION_INPUT_MODE, durationInputMode.getKey());
    }
    if (lastUsedDurationMode != null) {
      values.put(KEY_LAST_USED_DURATION_MODE, lastUsedDurationMode.getKey());
    }
    return Map.copyOf(values);
  }

  /**
   * The value the booking form's {@code durationMode} field is preset with, or {@code null} while
   * the user has expressed no preference at all — the form then falls back to its own heuristic
   * (begin/end for a live booking, duration otherwise, → #851).
   */
  public String effectiveDurationMode() {
    var mode = durationInputMode == REMEMBER ? lastUsedDurationMode : durationInputMode;
    return mode != null ? mode.getKey() : null;
  }

  private static Long favoriteSuborderIdOf(Map<String, Object> map) {
    try {
      var value = map.get(KEY_FAVORITE_SUBORDER_ID);
      return value != null ? Long.parseLong(value.toString()) : null;
    } catch (Exception e) {
      return null;
    }
  }

  private static Optional<DurationInputMode> modeOf(Map<String, Object> map, String key) {
    var value = map.get(key);
    return value != null ? DurationInputMode.ofKey(value.toString()) : Optional.empty();
  }

}
