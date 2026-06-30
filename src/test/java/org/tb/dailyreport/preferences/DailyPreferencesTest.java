package org.tb.dailyreport.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyPreferencesTest {

  @Test
  void from_should_parse_workDayStart() {
    Map<String, Object> map = Map.of(DailyPreferences.KEY_WORK_DAY_START, "08:30");

    DailyPreferences prefs = DailyPreferences.from(map);

    assertThat(prefs.workDayStart()).isEqualTo(LocalTime.of(8, 30));
  }

  @Test
  void from_should_parse_workDayStart_with_seconds() {
    Map<String, Object> map = Map.of(DailyPreferences.KEY_WORK_DAY_START, "08:30:00");

    DailyPreferences prefs = DailyPreferences.from(map);

    assertThat(prefs.workDayStart()).isEqualTo(LocalTime.of(8, 30));
  }

  @Test
  void from_should_return_defaults_for_empty_map() {
    DailyPreferences prefs = DailyPreferences.from(Map.of());

    assertThat(prefs).isEqualTo(DailyPreferences.defaults());
  }

  @Test
  void from_should_return_defaults_for_invalid_time_string() {
    Map<String, Object> map = Map.of(DailyPreferences.KEY_WORK_DAY_START, "not-a-time");

    DailyPreferences prefs = DailyPreferences.from(map);

    assertThat(prefs).isEqualTo(DailyPreferences.defaults());
  }

  @Test
  void from_should_return_defaults_for_missing_key() {
    Map<String, Object> map = Map.of("someOtherKey", "value");

    DailyPreferences prefs = DailyPreferences.from(map);

    assertThat(prefs).isEqualTo(DailyPreferences.defaults());
  }

  @Test
  void defaults_should_return_9am() {
    assertThat(DailyPreferences.defaults().workDayStart()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void toMap_should_roundtrip_via_from() {
    DailyPreferences original = new DailyPreferences(LocalTime.of(7, 45));

    DailyPreferences roundtripped = DailyPreferences.from(original.toMap());

    assertThat(roundtripped.workDayStart()).isEqualTo(original.workDayStart());
  }

  @Test
  void toMap_should_contain_workDayStart_key() {
    DailyPreferences prefs = new DailyPreferences(LocalTime.of(10, 0));

    Map<String, Object> map = prefs.toMap();

    assertThat(map).containsKey(DailyPreferences.KEY_WORK_DAY_START);
  }

}
