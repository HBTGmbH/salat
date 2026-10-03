package de.hbt.salat.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Year;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DateUtilsTest {

  @Test
  void same_dates_must_return_working_day_distance1_if_working_day() {
    LocalDate a = LocalDate.of(2022, 3, 1);
    LocalDate b = LocalDate.of(2022, 3, 1);
    assertThat(DateUtils.getWorkingDayDistance(a, b)).isEqualTo(1);
  }

  @Test
  void same_dates_must_return_working_day_distance0_if_not_working_day() {
    LocalDate a = LocalDate.of(2022, 3, 5);
    LocalDate b = LocalDate.of(2022, 3, 5);
    assertThat(DateUtils.getWorkingDayDistance(a, b)).isEqualTo(0);
  }

  @Test
  void working_day_distance_must_consider_weekends() {
    LocalDate a = LocalDate.of(2022, 3, 1);
    LocalDate b = LocalDate.of(2022, 3, 11);
    assertThat(DateUtils.getWorkingDayDistance(a, b)).isEqualTo(9);
  }

  @Test
  void min_with_null_value_should_return_other_value() {
    LocalDate a = LocalDate.of(2022, 3, 1);
    assertThat(DateUtils.min(a, null)).isEqualTo(a);
    assertThat(DateUtils.min(null, a)).isEqualTo(a);
  }

  @Test
  void min_with_only_null_values_should_return_null() {
    assertThat(DateUtils.min(null, null)).isNull();
  }

  @Test
  void min_should_return_min_value() {
    LocalDate a = LocalDate.of(2022, 3, 1);
    LocalDate b = LocalDate.of(2022, 3, 2);
    assertThat(DateUtils.min(a, b)).isEqualTo(a);
    assertThat(DateUtils.min(b, a)).isEqualTo(a);
  }

  @Test
  void max_with_null_value_should_return_other_value() {
    LocalDate a = LocalDate.of(2022, 3, 1);
    assertThat(DateUtils.max(a, null)).isEqualTo(a);
    assertThat(DateUtils.max(null, a)).isEqualTo(a);
  }

  @Test
  void max_with_only_null_values_should_return_null() {
    assertThat(DateUtils.max(null, null)).isNull();
  }

  @Test
  void max_should_return_max_value() {
    LocalDate a = LocalDate.of(2022, 3, 1);
    LocalDate b = LocalDate.of(2022, 3, 2);
    assertThat(DateUtils.max(a, b)).isEqualTo(b);
    assertThat(DateUtils.max(b, a)).isEqualTo(b);
  }

  @Test
  void get_first_day_of_year() {
    assertThat(DateUtils.getFirstDay(Year.of(2022))).isEqualTo(LocalDate.of(2022, 1, 1));
  }

  @Test
  void get_last_day_of_year() {
    assertThat(DateUtils.getLastDay(Year.of(2022))).isEqualTo(LocalDate.of(2022, 12, 31));
  }


  /**
   * The short month is part of message keys ({@code main.timereport.select.month.jan}) in the daily view and the
   * matrix, so it must stay exactly what the constants used to say — English, whatever the locale of the server
   * (#1245).
   */
  @Test
  void the_short_month_is_the_english_abbreviation_whatever_the_default_locale() {
    var defaultLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY);
      var months = IntStream.rangeClosed(1, 12)
          .mapToObj(month -> DateUtils.formatMonth(LocalDate.of(2026, month, 1)))
          .toList();

      assertThat(months).containsExactly("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }
}
