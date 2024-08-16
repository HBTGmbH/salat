package org.tb.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.common.util.DurationUtils.decimalFormat;
import static org.tb.common.util.DurationUtils.format;
import static org.tb.common.util.DurationUtils.parseDuration;
import static org.tb.common.util.DurationUtils.validateDuration;

import java.time.Duration;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class DurationUtilsTest {

  @Test
  public void hours_minutes_should_be_parsed_with_colon() {
    assertThat(parseDuration("4:05")).isEqualTo(Duration.ofHours(4).plusMinutes(5));
    assertThat(parseDuration("4:00")).isEqualTo(Duration.ofHours(4));
    assertThat(parseDuration("04:00")).isEqualTo(Duration.ofHours(4));
    assertThat(parseDuration("0:05")).isEqualTo(Duration.ofMinutes(5));
    assertThat(parseDuration("0:5")).isEqualTo(Duration.ofMinutes(5));
    assertThat(parseDuration("0:50")).isEqualTo(Duration.ofMinutes(50));
    assertThat(parseDuration("-0:50")).isEqualTo(Duration.ofMinutes(-50));
    assertThat(parseDuration("4:5")).isEqualTo(Duration.ofHours(4).plusMinutes(5));
    assertThat(parseDuration("-4:5")).isEqualTo(Duration.ofHours(-4).plusMinutes(-5));
  }

  @Test
  public void hours_should_be_parsed_without_colon() {
    assertThat(parseDuration("4")).isEqualTo(Duration.ofHours(4));
    assertThat(parseDuration("0")).isEqualTo(Duration.ofHours(0));
    assertThat(parseDuration("00")).isEqualTo(Duration.ofHours(0));
    assertThat(parseDuration("1")).isEqualTo(Duration.ofHours(1));
    assertThat(parseDuration("-1")).isEqualTo(Duration.ofHours(-1));
  }

  @Test
  public void hours_should_be_parsed_with_colon() {
    assertThat(parseDuration("4:")).isEqualTo(Duration.ofHours(4));
    assertThat(parseDuration("04:")).isEqualTo(Duration.ofHours(4));
    assertThat(parseDuration("0:")).isEqualTo(Duration.ofHours(0));
    assertThat(parseDuration("1:")).isEqualTo(Duration.ofHours(1));
    assertThat(parseDuration("-1:")).isEqualTo(Duration.ofHours(-1));
    assertThat(parseDuration("-01:")).isEqualTo(Duration.ofHours(-1));
  }

  @Test
  public void minutes_should_be_parsed_with_colon() {
    assertThat(parseDuration(":4")).isEqualTo(Duration.ofMinutes(4));
    assertThat(parseDuration(":0")).isEqualTo(Duration.ofMinutes(0));
    assertThat(parseDuration(":33")).isEqualTo(Duration.ofMinutes(33));
    assertThat(parseDuration("-:33")).isEqualTo(Duration.ofMinutes(-33));
  }

  @Test
  public void hours_minutes_should_be_valid() {
    assertThat(validateDuration("4:05")).isTrue();
    assertThat(validateDuration("4:00")).isTrue();
    assertThat(validateDuration("0:05")).isTrue();
    assertThat(validateDuration("0:5")).isTrue();
    assertThat(validateDuration("0:50")).isTrue();
    assertThat(validateDuration("-0:50")).isTrue();
    assertThat(validateDuration("04:5")).isTrue();
    assertThat(validateDuration("-4:5")).isTrue();
    assertThat(validateDuration("0:0")).isTrue();
    assertThat(validateDuration("-0:00")).isTrue();
  }

  @Test
  public void hours_should_be_valid_without_colon() {
    assertThat(validateDuration("4")).isTrue();
    assertThat(validateDuration("04")).isTrue();
    assertThat(validateDuration("0")).isTrue();
    assertThat(validateDuration("00")).isTrue();
    assertThat(validateDuration("1")).isTrue();
    assertThat(validateDuration("-1")).isTrue();
  }

  @Test
  public void hours_should_be_valid_with_colon() {
    assertThat(validateDuration("4:")).isTrue();
    assertThat(validateDuration("04:")).isTrue();
    assertThat(validateDuration("0:")).isTrue();
    assertThat(validateDuration("1:")).isTrue();
    assertThat(validateDuration("-1:")).isTrue();
    assertThat(validateDuration("-01:")).isTrue();
  }

  @Test
  public void minutes_should_be_valid_with_colon() {
    assertThat(validateDuration(":4")).isTrue();
    assertThat(validateDuration(":0")).isTrue();
    assertThat(validateDuration(":59")).isTrue();
    assertThat(validateDuration("-:33")).isTrue();
  }

  @Test
  public void wrong_minus_position_should_be_invalid() {
    assertThat(validateDuration(":4-")).isFalse();
    assertThat(validateDuration("3-3:0")).isFalse();
    assertThat(validateDuration("33-:0")).isFalse();
    assertThat(validateDuration("33:0-")).isFalse();
    assertThat(validateDuration("-33-0")).isFalse();
    assertThat(validateDuration("33-")).isFalse();
    assertThat(validateDuration(":3-3")).isFalse();
    assertThat(validateDuration(":-33")).isFalse();
  }

  @Test
  public void wrong_divider_should_be_invalid() {
    assertThat(validateDuration(";4")).isFalse();
    assertThat(validateDuration("3,0")).isFalse();
    assertThat(validateDuration("33.0")).isFalse();
    assertThat(validateDuration("33_10")).isFalse();
    assertThat(validateDuration("33 0")).isFalse();
  }

  @Test
  public void more_than_60_minutes_should_be_invalid() {
    assertThat(validateDuration("1:60")).isFalse();
    assertThat(validateDuration("1:100")).isFalse();
    assertThat(validateDuration(":99")).isFalse();
    assertThat(validateDuration("-:60")).isFalse();
    assertThat(validateDuration("-:99")).isFalse();
  }

  @Test
  public void should_format_hours() {
    assertThat(format(Duration.ofHours(2))).isEqualTo("2:00");
    assertThat(format(Duration.ofHours(0), true)).isEqualTo("0:00");
    assertThat(format(Duration.ofHours(-2))).isEqualTo("-2:00");
  }

  @Test
  public void should_format_minutes() {
    assertThat(format(Duration.ofMinutes(20))).isEqualTo("0:20");
    assertThat(format(Duration.ofMinutes(2))).isEqualTo("0:02");
    assertThat(format(Duration.ofMinutes(0), true)).isEqualTo("0:00");
    assertThat(format(Duration.ofMinutes(-20))).isEqualTo("-0:20");
    assertThat(format(Duration.ofMinutes(-2))).isEqualTo("-0:02");
  }

  @Test
  public void should_format_hours_and_minutes() {
    assertThat(format(Duration.ofMinutes(64))).isEqualTo("1:04");
    assertThat(format(Duration.ofMinutes(145))).isEqualTo("2:25");
    assertThat(format(Duration.ofMinutes(-64))).isEqualTo("-1:04");
    assertThat(format(Duration.ofMinutes(-145))).isEqualTo("-2:25");
  }

  @Test
  public void should_decimal_format_hours() {
    assertThat(decimalFormat(Duration.ofHours(2))).isEqualTo("2,00");
    assertThat(decimalFormat(Duration.ofHours(0))).isEqualTo("0,00");
    assertThat(decimalFormat(Duration.ofHours(-2))).isEqualTo("-2,00");
  }

  @Test
  public void should_decimal_format_minutes() {
    assertThat(decimalFormat(Duration.ofMinutes(20))).isEqualTo("0,33");
    assertThat(decimalFormat(Duration.ofMinutes(2))).isEqualTo("0,03");
    assertThat(decimalFormat(Duration.ofMinutes(0))).isEqualTo("0,00");
    assertThat(decimalFormat(Duration.ofMinutes(-20))).isEqualTo("-0,33");
    assertThat(decimalFormat(Duration.ofMinutes(-2))).isEqualTo("-0,03");
  }

  @Test
  public void should_decimal_format_hours_and_minutes() {
    assertThat(decimalFormat(Duration.ofMinutes(64))).isEqualTo("1,07");
    assertThat(decimalFormat(Duration.ofMinutes(145))).isEqualTo("2,42");
    assertThat(decimalFormat(Duration.ofMinutes(-64))).isEqualTo("-1,07");
    assertThat(decimalFormat(Duration.ofMinutes(-145))).isEqualTo("-2,42");
  }

}
