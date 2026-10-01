package de.hbt.salat.dailyreport.preferences;

import static de.hbt.salat.common.GlobalConstants.DEFAULT_WORK_DAY_START;

import java.time.LocalTime;
import java.util.Map;

/**
 * @param workDayStart            the start of a day that has no stored working day yet
 * @param considerMandatoryBreak  whether the contract target of the daily view counts at least the
 *                                mandatory break, even while less break is booked (#1236)
 */
public record DailyPreferences(LocalTime workDayStart, boolean considerMandatoryBreak) {

  public static final String MODULE_KEY = "daily";
  static final String KEY_WORK_DAY_START = "workDayStart";
  static final String KEY_CONSIDER_MANDATORY_BREAK = "considerMandatoryBreak";

  public static DailyPreferences defaults() {
    return new DailyPreferences(LocalTime.of(DEFAULT_WORK_DAY_START, 0), true);
  }

  public static DailyPreferences from(Map<String, Object> map) {
    // every key is parsed on its own: a stored setting without the newer key, or with a malformed
    // one, must not cost the work day start as well (#1236)
    return new DailyPreferences(workDayStartOf(map), considerMandatoryBreakOf(map));
  }

  public Map<String, Object> toMap() {
    return Map.of(
        KEY_WORK_DAY_START, workDayStart.toString(),
        KEY_CONSIDER_MANDATORY_BREAK, Boolean.toString(considerMandatoryBreak));
  }

  private static LocalTime workDayStartOf(Map<String, Object> map) {
    try {
      return LocalTime.parse((String) map.get(KEY_WORK_DAY_START));
    } catch (Exception e) {
      return defaults().workDayStart();
    }
  }

  private static boolean considerMandatoryBreakOf(Map<String, Object> map) {
    var value = map.get(KEY_CONSIDER_MANDATORY_BREAK);
    if (value != null && ("true".equalsIgnoreCase(value.toString()) || "false".equalsIgnoreCase(value.toString()))) {
      return Boolean.parseBoolean(value.toString());
    }
    return defaults().considerMandatoryBreak();
  }

}
