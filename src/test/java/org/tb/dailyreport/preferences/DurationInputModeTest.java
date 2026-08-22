package org.tb.dailyreport.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.dailyreport.preferences.DurationInputMode.BEGIN_END;
import static org.tb.dailyreport.preferences.DurationInputMode.DURATION;
import static org.tb.dailyreport.preferences.DurationInputMode.REMEMBER;

import org.junit.jupiter.api.Test;

class DurationInputModeTest {

  @Test
  void the_keys_match_the_values_of_the_forms_durationMode_field() {
    assertThat(REMEMBER.getKey()).isEqualTo("remember");
    assertThat(DURATION.getKey()).isEqualTo("duration");
    assertThat(BEGIN_END.getKey()).isEqualTo("beginEnd");
  }

  @Test
  void a_known_key_resolves() {
    assertThat(DurationInputMode.ofKey("beginEnd")).contains(BEGIN_END);
  }

  @Test
  void an_unknown_key_stays_empty() {
    assertThat(DurationInputMode.ofKey("nonsense")).isEmpty();
    assertThat(DurationInputMode.ofKey(null)).isEmpty();
  }

  @Test
  void only_beginEnd_counts_as_begin_end_on_the_form() {
    assertThat(DurationInputMode.ofFormValue("beginEnd")).isEqualTo(BEGIN_END);
    assertThat(DurationInputMode.ofFormValue("duration")).isEqualTo(DURATION);
    // the form never submits "remember", and a missing field must not become begin/end
    assertThat(DurationInputMode.ofFormValue("remember")).isEqualTo(DURATION);
    assertThat(DurationInputMode.ofFormValue(null)).isEqualTo(DURATION);
  }

}
