package de.hbt.salat.dailyreport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Das Vertrag-Soll der Tagesansicht (#1236): Beginn, Pause und Tagessoll, mit eingeschalteter
 * Pflichtpause mindestens die Pause, die das Tagessoll verlangt.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TargetEndTest {

  private static final LocalTime START = LocalTime.of(8, 45);
  private static final Duration SEVEN_HOURS = Duration.ofHours(7);

  @ParameterizedTest(name = "Pause {0} min -> {1}, Pflichtpause angerechnet: {2}")
  @CsvSource({
      "0,  16:15, true",
      "15, 16:15, true",
      "30, 16:15, false",
      "45, 16:30, false",
  })
  void with_the_mandatory_break_seven_hours_end_no_earlier_than_with_half_an_hour_of_break(
      int bookedBreakMinutes, LocalTime expectedEnd, boolean mandatoryBreakApplied) {
    var targetEnd = TargetEnd.of(START, Duration.ofMinutes(bookedBreakMinutes), SEVEN_HOURS, true);

    assertThat(targetEnd.time()).isEqualTo(expectedEnd);
    assertThat(targetEnd.mandatoryBreak() != null).isEqualTo(mandatoryBreakApplied);
  }

  @Test
  void the_hint_names_the_break_that_counted() {
    assertThat(TargetEnd.of(START, Duration.ZERO, SEVEN_HOURS, true).mandatoryBreak())
        .isEqualTo(Duration.ofMinutes(30));
  }

  @ParameterizedTest(name = "Pause {0} min -> {1}")
  @CsvSource({
      "0,  15:45",
      "45, 16:30",
  })
  void without_the_mandatory_break_only_the_booked_break_counts(int bookedBreakMinutes, LocalTime expectedEnd) {
    var targetEnd = TargetEnd.of(START, Duration.ofMinutes(bookedBreakMinutes), SEVEN_HOURS, false);

    assertThat(targetEnd.time()).isEqualTo(expectedEnd);
    assertThat(targetEnd.mandatoryBreak()).isNull();
  }

  @Test
  void a_target_over_nine_hours_requires_three_quarters_of_an_hour() {
    var nineAndAHalf = Duration.ofMinutes(9 * 60 + 30);

    var unbooked = TargetEnd.of(START, Duration.ofMinutes(30), nineAndAHalf, true);
    var booked = TargetEnd.of(START, Duration.ofMinutes(45), nineAndAHalf, true);

    assertThat(unbooked.time()).isEqualTo(LocalTime.of(19, 0));
    assertThat(unbooked.mandatoryBreak()).isEqualTo(Duration.ofMinutes(45));
    assertThat(booked.time()).isEqualTo(LocalTime.of(19, 0));
    assertThat(booked.mandatoryBreak()).isNull();
  }

  @Test
  void a_target_of_exactly_nine_hours_still_requires_half_an_hour() {
    var targetEnd = TargetEnd.of(START, Duration.ZERO, Duration.ofHours(9), true);

    assertThat(targetEnd.time()).isEqualTo(LocalTime.of(18, 15));
    assertThat(targetEnd.mandatoryBreak()).isEqualTo(Duration.ofMinutes(30));
  }

  @Test
  void a_target_of_exactly_six_hours_requires_no_break() {
    var targetEnd = TargetEnd.of(START, Duration.ZERO, Duration.ofHours(6), true);

    assertThat(targetEnd.time()).isEqualTo(LocalTime.of(14, 45));
    assertThat(targetEnd.mandatoryBreak()).isNull();
  }

}
