package de.hbt.salat.dailyreport.preferences;

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
    DailyPreferences original = new DailyPreferences(LocalTime.of(7, 45), false);

    DailyPreferences roundtripped = DailyPreferences.from(original.toMap());

    assertThat(roundtripped).isEqualTo(original);
  }

  @Test
  void toMap_should_contain_workDayStart_key() {
    DailyPreferences prefs = new DailyPreferences(LocalTime.of(10, 0), true);

    Map<String, Object> map = prefs.toMap();

    assertThat(map).containsKey(DailyPreferences.KEY_WORK_DAY_START);
  }

  @Test
  void the_mandatory_break_is_considered_by_default() {
    assertThat(DailyPreferences.defaults().considerMandatoryBreak()).isTrue();
  }

  /** Eine Einstellung von vor #1236 kennt den Schlüssel nicht und behält trotzdem ihren Arbeitsbeginn. */
  @Test
  void a_stored_setting_without_the_mandatory_break_keeps_its_work_day_start() {
    DailyPreferences prefs = DailyPreferences.from(Map.of(DailyPreferences.KEY_WORK_DAY_START, "08:30"));

    assertThat(prefs.workDayStart()).isEqualTo(LocalTime.of(8, 30));
    assertThat(prefs.considerMandatoryBreak()).isTrue();
  }

  @Test
  void a_malformed_mandatory_break_falls_back_to_considered_and_keeps_the_work_day_start() {
    DailyPreferences prefs = DailyPreferences.from(Map.of(
        DailyPreferences.KEY_WORK_DAY_START, "08:30",
        DailyPreferences.KEY_CONSIDER_MANDATORY_BREAK, "maybe"));

    assertThat(prefs.workDayStart()).isEqualTo(LocalTime.of(8, 30));
    assertThat(prefs.considerMandatoryBreak()).isTrue();
  }

  @Test
  void a_malformed_work_day_start_keeps_a_switched_off_mandatory_break() {
    DailyPreferences prefs = DailyPreferences.from(Map.of(
        DailyPreferences.KEY_WORK_DAY_START, "not-a-time",
        DailyPreferences.KEY_CONSIDER_MANDATORY_BREAK, "false"));

    assertThat(prefs.workDayStart()).isEqualTo(DailyPreferences.defaults().workDayStart());
    assertThat(prefs.considerMandatoryBreak()).isFalse();
  }

  @Test
  void a_mandatory_break_stored_as_json_boolean_is_read_as_well() {
    DailyPreferences prefs = DailyPreferences.from(Map.of(DailyPreferences.KEY_CONSIDER_MANDATORY_BREAK, false));

    assertThat(prefs.considerMandatoryBreak()).isFalse();
  }

}
