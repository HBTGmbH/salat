package org.tb.dailyreport.preferences;

import java.util.Arrays;
import java.util.Optional;

/**
 * How the "Dauer oder Beginn / Ende" toggle of the booking form is preset (#844). Some colleagues
 * always book a plain duration, others always work with clock times — both should get their
 * preferred entry mode without switching it on every booking.
 *
 * <p>The keys are the same strings the form's {@code durationMode} field uses, so a preference
 * value can be handed to the form as is.
 */
public enum DurationInputMode {

  /** Preset with whatever the user picked for their previous booking. */
  REMEMBER("remember"),
  /** Always preset the duration field. */
  DURATION("duration"),
  /** Always preset the begin/end fields. */
  BEGIN_END("beginEnd");

  private final String key;

  DurationInputMode(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }

  public static Optional<DurationInputMode> ofKey(String key) {
    return Arrays.stream(values())
        .filter(mode -> mode.key.equals(key))
        .findFirst();
  }

  /**
   * The mode the booking form is actually in — {@link #REMEMBER} is a preference, not a form state,
   * so anything that is not begin/end counts as duration.
   */
  public static DurationInputMode ofFormValue(String durationMode) {
    return BEGIN_END.key.equals(durationMode) ? BEGIN_END : DURATION;
  }

}
