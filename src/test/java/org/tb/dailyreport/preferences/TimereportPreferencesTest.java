package org.tb.dailyreport.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.dailyreport.preferences.DurationInputMode.BEGIN_END;
import static org.tb.dailyreport.preferences.DurationInputMode.DURATION;
import static org.tb.dailyreport.preferences.DurationInputMode.REMEMBER;
import static org.tb.dailyreport.preferences.TimereportPreferences.KEY_DURATION_INPUT_MODE;
import static org.tb.dailyreport.preferences.TimereportPreferences.KEY_FAVORITE_SUBORDER_ID;
import static org.tb.dailyreport.preferences.TimereportPreferences.KEY_LAST_USED_DURATION_MODE;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Round-tripping the booking form's entry mode preference (#844).
 */
class TimereportPreferencesTest {

  @Test
  void an_empty_map_yields_the_defaults() {
    assertThat(TimereportPreferences.from(Map.of())).isEqualTo(TimereportPreferences.defaults());
  }

  @Test
  void remembering_the_last_entry_is_the_default() {
    var defaults = TimereportPreferences.defaults();

    assertThat(defaults.durationInputMode()).isEqualTo(REMEMBER);
    assertThat(defaults.lastUsedDurationMode()).isNull();
  }

  @Test
  void without_any_choice_the_form_decides_for_itself() {
    // no preset at all, so the form keeps its live-booking heuristic (#851)
    assertThat(TimereportPreferences.defaults().effectiveDurationMode()).isNull();
  }

  @Test
  void defaults_are_not_written_to_the_map() {
    assertThat(TimereportPreferences.defaults().toMap()).isEmpty();
  }

  @Test
  void an_explicit_mode_survives_the_roundtrip() {
    var original = new TimereportPreferences(42L, BEGIN_END, BEGIN_END);

    assertThat(TimereportPreferences.from(original.toMap())).isEqualTo(original);
  }

  @Test
  void the_remembered_mode_survives_the_roundtrip() {
    var original = new TimereportPreferences(null, REMEMBER, BEGIN_END);

    assertThat(TimereportPreferences.from(original.toMap())).isEqualTo(original);
  }

  @Test
  void while_remembering_the_last_used_mode_is_the_effective_one() {
    assertThat(new TimereportPreferences(null, REMEMBER, BEGIN_END).effectiveDurationMode())
        .isEqualTo("beginEnd");
    assertThat(new TimereportPreferences(null, REMEMBER, DURATION).effectiveDurationMode())
        .isEqualTo("duration");
  }

  @Test
  void an_explicit_mode_wins_over_the_last_used_one() {
    assertThat(new TimereportPreferences(null, DURATION, BEGIN_END).effectiveDurationMode())
        .isEqualTo("duration");
    assertThat(new TimereportPreferences(null, BEGIN_END, DURATION).effectiveDurationMode())
        .isEqualTo("beginEnd");
  }

  @Test
  void an_explicit_mode_applies_even_without_a_remembered_one() {
    assertThat(new TimereportPreferences(null, BEGIN_END, null).effectiveDurationMode())
        .isEqualTo("beginEnd");
  }

  @Test
  void an_unknown_mode_falls_back_to_the_default() {
    var map = Map.<String, Object>of(
        KEY_DURATION_INPUT_MODE, "nonsense",
        KEY_LAST_USED_DURATION_MODE, "nonsense");

    assertThat(TimereportPreferences.from(map)).isEqualTo(TimereportPreferences.defaults());
  }

  @Test
  void remember_is_not_accepted_as_the_last_used_mode() {
    // it is a preference, not a form state — persisting it would make the preset undecidable
    var map = Map.<String, Object>of(KEY_LAST_USED_DURATION_MODE, REMEMBER.getKey());

    assertThat(TimereportPreferences.from(map).lastUsedDurationMode()).isNull();
  }

  @Test
  void a_malformed_favorite_does_not_cost_the_entry_mode() {
    var map = new HashMap<String, Object>();
    map.put(KEY_FAVORITE_SUBORDER_ID, "not-a-number");
    map.put(KEY_DURATION_INPUT_MODE, BEGIN_END.getKey());

    var preferences = TimereportPreferences.from(map);

    assertThat(preferences.favoriteSuborderId()).isNull();
    assertThat(preferences.durationInputMode()).isEqualTo(BEGIN_END);
  }

  @Test
  void the_favorite_suborder_still_round_trips() {
    var original = new TimereportPreferences(7L, REMEMBER, null);

    assertThat(original.toMap()).containsExactly(Map.entry(KEY_FAVORITE_SUBORDER_ID, "7"));
    assertThat(TimereportPreferences.from(original.toMap())).isEqualTo(original);
  }

}
