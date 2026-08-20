package org.tb.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.common.util.TimeFormatUtils.decimalFormatHoursAndMinutes;
import static org.tb.common.util.TimeFormatUtils.decimalFormatMinutes;
import static org.tb.common.util.TimeFormatUtils.parseFlexibleTimeOfDay;
import static org.tb.common.util.TimeFormatUtils.timeFormatHoursAndMinutes;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class TimeFormatUtilsTest {

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
  public void time_of_day_should_be_parsed_with_a_separator() {
    assertThat(parseFlexibleTimeOfDay("8:30")).contains(LocalTime.of(8, 30));
    assertThat(parseFlexibleTimeOfDay("08:30")).contains(LocalTime.of(8, 30));
    assertThat(parseFlexibleTimeOfDay("18:05")).contains(LocalTime.of(18, 5));
    assertThat(parseFlexibleTimeOfDay("8:5")).contains(LocalTime.of(8, 5));
    assertThat(parseFlexibleTimeOfDay(" 8:30 ")).contains(LocalTime.of(8, 30));
    // for a time of day a decimal separator divides hours from minutes, unlike for a duration
    assertThat(parseFlexibleTimeOfDay("8.30")).contains(LocalTime.of(8, 30));
    assertThat(parseFlexibleTimeOfDay("8,30")).contains(LocalTime.of(8, 30));
  }

  @Test
  public void time_of_day_should_be_parsed_from_digits_only() {
    assertThat(parseFlexibleTimeOfDay("8")).contains(LocalTime.of(8, 0));
    assertThat(parseFlexibleTimeOfDay("08")).contains(LocalTime.of(8, 0));
    // two digits are an hour here, whereas the duration parser reads them as minutes
    assertThat(parseFlexibleTimeOfDay("13")).contains(LocalTime.of(13, 0));
    assertThat(parseFlexibleTimeOfDay("830")).contains(LocalTime.of(8, 30));
    assertThat(parseFlexibleTimeOfDay("0830")).contains(LocalTime.of(8, 30));
    assertThat(parseFlexibleTimeOfDay("1830")).contains(LocalTime.of(18, 30));
    assertThat(parseFlexibleTimeOfDay("0")).contains(LocalTime.of(0, 0));
    assertThat(parseFlexibleTimeOfDay("2359")).contains(LocalTime.of(23, 59));
  }

  @Test
  public void time_of_day_should_be_empty_when_out_of_range_or_unparseable() {
    assertThat(parseFlexibleTimeOfDay(null)).isEmpty();
    assertThat(parseFlexibleTimeOfDay("")).isEmpty();
    assertThat(parseFlexibleTimeOfDay("   ")).isEmpty();
    assertThat(parseFlexibleTimeOfDay("abc")).isEmpty();
    assertThat(parseFlexibleTimeOfDay("24")).isEmpty();
    assertThat(parseFlexibleTimeOfDay("24:00")).isEmpty();
    assertThat(parseFlexibleTimeOfDay("8:60")).isEmpty();
    assertThat(parseFlexibleTimeOfDay("2360")).isEmpty();
    assertThat(parseFlexibleTimeOfDay("-8:30")).isEmpty();
    assertThat(parseFlexibleTimeOfDay("12345")).isEmpty();
    assertThat(parseFlexibleTimeOfDay("8h30")).isEmpty();
  }

}
