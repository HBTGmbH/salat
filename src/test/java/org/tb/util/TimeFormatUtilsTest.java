package org.tb.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.util.TimeFormatUtils.*;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class TimeFormatUtilsTest {

  @Test
  public void decimal_hours_should_format_with_comma() {
    assertThat(decimalFormatHours(8.5)).isEqualTo("8,50");
    assertThat(decimalFormatHours(2.44)).isEqualTo("2,44");
    assertThat(decimalFormatHours(2.501)).isEqualTo("2,50");
    assertThat(decimalFormatHours(0.44)).isEqualTo("0,44");
    assertThat(decimalFormatHours(0.4)).isEqualTo("0,40");
  }

  @Test
  public void hours_and_minutes_should_format_with_comma() {
    assertThat(decimalFormatHoursAndMinutes(8, 30)).isEqualTo("8,50");
    assertThat(decimalFormatHoursAndMinutes(0, 30)).isEqualTo("0,50");
    assertThat(decimalFormatHoursAndMinutes(2, 51)).isEqualTo("2,85");
    assertThat(decimalFormatHoursAndMinutes(3, 52)).isEqualTo("3,87");
  }

  @Test
  public void hours_and_minutes_should_format_as_time() {
    assertThat(timeFormatHoursAndMinutes(8, 30)).isEqualTo("8:30");
    assertThat(timeFormatHoursAndMinutes(-8, -30)).isEqualTo("-8:30");
    assertThat(timeFormatHoursAndMinutes(0, 3)).isEqualTo("0:03");
  }

  @Test
  public void minutes_should_format_as_time() {
    assertThat(timeFormatMinutes(270)).isEqualTo("4:30");
    assertThat(timeFormatMinutes(-270)).isEqualTo("-4:30");
    assertThat(timeFormatMinutes(3)).isEqualTo("0:03");
  }

  @Test
  public void minutes_should_format_as_decimal_hours_with_comma() {
    assertThat(decimalFormatMinutes(270)).isEqualTo("4,50");
    assertThat(decimalFormatMinutes(272)).isEqualTo("4,53");
    assertThat(decimalFormatMinutes(3)).isEqualTo("0,05");
  }

  @Test
  public void decimal_hours_should_format_as_time() {
    assertThat(timeFormatHours(3)).isEqualTo("3:00");
    assertThat(timeFormatHours(3.5)).isEqualTo("3:30");
    assertThat(timeFormatHours(3.75)).isEqualTo("3:45");
    assertThat(timeFormatHours(2.7641)).isEqualTo("2:46");
  }

}
