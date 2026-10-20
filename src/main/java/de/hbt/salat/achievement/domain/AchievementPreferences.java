package de.hbt.salat.achievement.domain;

import java.util.Map;

/**
 * Whether a person is told about a new achievement — notification and popup (#1501). On by default;
 * switched off, achievements are still recorded and shown on the profile page.
 */
public record AchievementPreferences(boolean announce) {

  public static final String MODULE_KEY = "achievement";
  static final String KEY_ANNOUNCE = "announce";

  public static AchievementPreferences defaults() {
    return new AchievementPreferences(true);
  }

  public static AchievementPreferences from(Map<String, Object> module) {
    if (module != null && module.get(KEY_ANNOUNCE) instanceof Boolean announce) {
      return new AchievementPreferences(announce);
    }
    return defaults();
  }

  /** Only the deviation from the default is stored, so that the default leaves no section behind. */
  public Map<String, Object> toMap() {
    return announce ? Map.of() : Map.of(KEY_ANNOUNCE, false);
  }
}
