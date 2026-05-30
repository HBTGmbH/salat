package org.tb.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ReportResultColumnValueTest {

  @Test
  void null_value_returns_empty_string() {
    var v = new ReportResultColumnValue(null);
    assertThat(v.getFormattedValue(Locale.GERMAN)).isEmpty();
    assertThat(v.getFormattedValue(Locale.ENGLISH)).isEmpty();
  }

  @Test
  void bigdecimal_uses_comma_for_german() {
    var v = new ReportResultColumnValue(new BigDecimal("1234.56"));
    assertThat(v.getFormattedValue(Locale.GERMAN)).isEqualTo("1.234,56");
  }

  @Test
  void bigdecimal_uses_dot_for_english() {
    var v = new ReportResultColumnValue(new BigDecimal("1234.56"));
    assertThat(v.getFormattedValue(Locale.ENGLISH)).isEqualTo("1,234.56");
  }

  @Test
  void double_uses_comma_for_german() {
    var v = new ReportResultColumnValue(1234.5);
    assertThat(v.getFormattedValue(Locale.GERMAN)).isEqualTo("1.234,5");
  }

  @Test
  void double_uses_dot_for_english() {
    var v = new ReportResultColumnValue(1234.5);
    assertThat(v.getFormattedValue(Locale.ENGLISH)).isEqualTo("1,234.5");
  }

  @Test
  void float_uses_comma_for_german() {
    var v = new ReportResultColumnValue(12.5f);
    assertThat(v.getFormattedValue(Locale.GERMAN)).isEqualTo("12,5");
  }

  @Test
  void integer_is_not_locale_formatted() {
    var v = new ReportResultColumnValue(2024);
    assertThat(v.getFormattedValue(Locale.GERMAN)).isEqualTo("2024");
    assertThat(v.getFormattedValue(Locale.ENGLISH)).isEqualTo("2024");
  }

  @Test
  void long_is_not_locale_formatted() {
    var v = new ReportResultColumnValue(42L);
    assertThat(v.getFormattedValue(Locale.GERMAN)).isEqualTo("42");
  }

  @Test
  void string_is_returned_as_is() {
    var v = new ReportResultColumnValue("hello");
    assertThat(v.getFormattedValue(Locale.GERMAN)).isEqualTo("hello");
  }

  @Test
  void is_numeric_true_for_number_types() {
    assertThat(new ReportResultColumnValue(1).isNumeric()).isTrue();
    assertThat(new ReportResultColumnValue(1L).isNumeric()).isTrue();
    assertThat(new ReportResultColumnValue(1.0).isNumeric()).isTrue();
    assertThat(new ReportResultColumnValue(new BigDecimal("1")).isNumeric()).isTrue();
  }

  @Test
  void is_numeric_false_for_non_number_types() {
    assertThat(new ReportResultColumnValue("text").isNumeric()).isFalse();
    assertThat(new ReportResultColumnValue(null).isNumeric()).isFalse();
  }

}
