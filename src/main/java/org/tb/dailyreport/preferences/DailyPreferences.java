package org.tb.dailyreport.preferences;

import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;

import java.time.LocalTime;
import java.util.Map;

public record DailyPreferences(LocalTime workDayStart) {

  public static final String MODULE_KEY = "daily";
  static final String KEY_WORK_DAY_START = "workDayStart";

  public static DailyPreferences defaults() {
    return new DailyPreferences(LocalTime.of(DEFAULT_WORK_DAY_START, 0));
  }

  public static DailyPreferences from(Map<String, Object> map) {
    try {
      return new DailyPreferences(LocalTime.parse((String) map.get(KEY_WORK_DAY_START)));
    } catch (Exception e) {
      return defaults();
    }
  }

  public Map<String, Object> toMap() {
    return Map.of(KEY_WORK_DAY_START, workDayStart.toString());
  }

}
