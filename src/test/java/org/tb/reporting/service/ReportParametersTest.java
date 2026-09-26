package org.tb.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.test.FixedClock;
import org.tb.common.util.DateUtils;
import org.tb.reporting.domain.ReportParameter;

@FixedClock
class ReportParametersTest {

  @Test
  void onlyParametersTheSqlNamesArePassedOn() {
    var params = Map.of("jahr", "2025", "unbekannt", "egal");

    var result = ReportParameters.fromRequest(params, "select * from t where jahr = :jahr");

    assertThat(result).extracting(ReportParameter::getName).containsExactly("jahr");
  }

  @Test
  void aValueCarriesItsTypeAsPrefix() {
    var params = Map.of("von", "date,2025-09-01");

    var result = ReportParameters.fromRequest(params, "select * from t where d >= :von");

    assertThat(result).singleElement().satisfies(parameter -> {
      assertThat(parameter.getType()).isEqualTo("date");
      assertThat(parameter.getValue()).isEqualTo("2025-09-01");
    });
  }

  @Test
  void withoutAPrefixAValueIsAString() {
    var params = Map.of("kuerzel", "abc");

    var result = ReportParameters.fromRequest(params, "select * from t where sign = :kuerzel");

    assertThat(result).singleElement().satisfies(parameter -> {
      assertThat(parameter.getType()).isEqualTo("string");
      assertThat(parameter.getValue()).isEqualTo("abc");
    });
  }

  @Test
  void aReportWithoutSqlHasNoParameters() {
    assertThat(ReportParameters.fromRequest(Map.of("jahr", "2025"), null)).isEmpty();
  }

  @Test
  void missingNamesTheParametersWithoutAValue() {
    var given = List.of(new ReportParameter("jahr", "number", "2025"));

    var result = ReportParameters.missing(given, "select * from t where jahr = :jahr and monat = :monat");

    assertThat(result).containsExactly("monat");
  }

  @Test
  void nothingIsMissingWhenEveryPlaceholderHasAValue() {
    var given = List.of(new ReportParameter("jahr", "number", "2025"));

    var result = ReportParameters.missing(given, "select * from t where jahr = :jahr");

    assertThat(result).isEmpty();
  }

  @Test
  void parametersWithoutANameAreDroppedAsEmptyFormRows() {
    var given = List.of(new ReportParameter("", "string", "x"), new ReportParameter("jahr", "number", "2025"));

    assertThat(ReportParameters.nonEmpty(given)).extracting(ReportParameter::getName).containsExactly("jahr");
  }

  @Test
  void aStarBecomesAnSqlWildcard() {
    var given = List.of(new ReportParameter("kuerzel", "string", "G*"));

    assertThat(ReportParameters.toParameterMap(given)).containsEntry("kuerzel", "G%");
  }

  @Test
  void aDateValueBecomesALocalDate() {
    var given = List.of(new ReportParameter("von", "date", "2025-09-01"));

    assertThat(ReportParameters.toParameterMap(given)).containsEntry("von", DateUtils.parse("2025-09-01"));
  }

  @Test
  void todayIsResolvedForDateParameters() {
    var given = List.of(new ReportParameter("von", "date", "TODAY"));

    assertThat(ReportParameters.toParameterMap(given)).containsEntry("von", DateUtils.today());
  }

  @Test
  void aDateKeywordIsResolvedAgainstToday() {
    var given = List.of(
        new ReportParameter("von", "date", "VORMONAT"),
        new ReportParameter("bis", "date", "VORMONATSENDE"),
        new ReportParameter("gestern", "date", "GESTERN"),
        new ReportParameter("heute", "date", "HEUTE"),
        new ReportParameter("quartal", "date", "BOQ"));

    assertThat(ReportParameters.toParameterMap(given))
        .containsEntry("von", LocalDate.of(2026, 5, 1))
        .containsEntry("bis", LocalDate.of(2026, 5, 31))
        .containsEntry("gestern", LocalDate.of(2026, 6, 24))
        .containsEntry("heute", LocalDate.of(2026, 6, 25))
        .containsEntry("quartal", LocalDate.of(2026, 4, 1));
  }

  @Test
  void aDateKeywordFromTheApiIsResolved() {
    var parameters = ReportParameters.fromRequest(Map.of("tag", "date,GESTERN"), "select * from t where d = :tag");

    assertThat(ReportParameters.toParameterMap(parameters)).containsEntry("tag", LocalDate.of(2026, 6, 24));
  }

  @Test
  void aNumericKeywordBecomesAnIntegerForNumberParameters() {
    var given = List.of(new ReportParameter("kw", "number", "KW"), new ReportParameter("monat", "number", "MONTH"));

    assertThat(ReportParameters.toParameterMap(given))
        .containsEntry("kw", 26)
        .containsEntry("monat", 6)
        .extractingByKey("kw").isInstanceOf(Integer.class);
  }

  @Test
  void anyOtherNumberValueIsPassedOnUnchanged() {
    var given = List.of(
        new ReportParameter("jahr", "number", "2025"),
        new ReportParameter("heute", "number", "HEUTE"),
        new ReportParameter("klein", "number", "kw"));

    assertThat(ReportParameters.toParameterMap(given))
        .containsEntry("jahr", "2025")
        .containsEntry("heute", "HEUTE")
        .containsEntry("klein", "kw");
  }

  @Test
  void aStringValueIsNeverTakenForAKeyword() {
    var given = List.of(new ReportParameter("suche", "string", "HEUTE"), new ReportParameter("kw", "string", "KW"));

    assertThat(ReportParameters.toParameterMap(given))
        .containsEntry("suche", "HEUTE")
        .containsEntry("kw", "KW");
  }

  @Test
  void aNumericKeywordIsNoDate() {
    var given = List.of(new ReportParameter("von", "date", "KW"));

    assertThatThrownBy(() -> ReportParameters.toParameterMap(given))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining("RP-0004");
  }

  @Test
  void keywordsAreCaseSensitive() {
    var given = List.of(new ReportParameter("von", "date", "heute"));

    assertThatThrownBy(() -> ReportParameters.toParameterMap(given))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining("RP-0004");
  }

  @Test
  void anEmptyDateValueBecomesNull() {
    var given = List.of(new ReportParameter("von", "date", ""));

    assertThat(ReportParameters.toParameterMap(given)).containsEntry("von", null);
  }

  @Test
  void anUnreadableDateIsRejectedRatherThanPassedToTheDatabase() {
    var given = List.of(new ReportParameter("von", "date", "vorgestern"));

    assertThatThrownBy(() -> ReportParameters.toParameterMap(given))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining("RP-0004");
  }

}
