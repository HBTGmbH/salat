package org.tb.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReportParameterResolverTest {

  /** Ein Samstag in der 39. Kalenderwoche, im dritten Quartal. */
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

  private static final Map<String, String> GERMAN_TO_ENGLISH = Map.ofEntries(
      Map.entry("HEUTE", "TODAY"),
      Map.entry("GESTERN", "YESTERDAY"),
      Map.entry("MORGEN", "TOMORROW"),
      Map.entry("VORMONAT", "LAST_MONTH"),
      Map.entry("VORMONATSENDE", "END_LAST_MONTH"),
      Map.entry("VORJAHR", "LAST_YEAR"),
      Map.entry("VORJAHRESENDE", "END_LAST_YEAR"),
      Map.entry("TAG", "DAY"),
      Map.entry("WOCHENTAG", "WEEKDAY"),
      Map.entry("KW", "WEEKNUM"),
      Map.entry("MONAT", "MONTH"),
      Map.entry("QUARTAL", "QUARTER"),
      Map.entry("JAHR", "YEAR")
  );

  @ParameterizedTest(name = "{0} = {1}")
  @CsvSource({
      "TODAY, 2026-09-26", "HEUTE, 2026-09-26",
      "YESTERDAY, 2026-09-25", "GESTERN, 2026-09-25",
      "TOMORROW, 2026-09-27", "MORGEN, 2026-09-27",
      "BOW, 2026-09-21", "EOW, 2026-09-27",
      "BOM, 2026-09-01", "EOM, 2026-09-30",
      "BOQ, 2026-07-01", "EOQ, 2026-09-30",
      "BOY, 2026-01-01", "EOY, 2026-12-31",
      "LAST_MONTH, 2026-08-01", "VORMONAT, 2026-08-01",
      "END_LAST_MONTH, 2026-08-31", "VORMONATSENDE, 2026-08-31",
      "LAST_YEAR, 2025-01-01", "VORJAHR, 2025-01-01",
      "END_LAST_YEAR, 2025-12-31", "VORJAHRESENDE, 2025-12-31",
  })
  void aDateKeywordResolvesToALocalDate(String keyword, LocalDate expected) {
    assertThat(ReportParameterResolver.resolveValue(keyword, TODAY)).contains(expected);
  }

  @ParameterizedTest(name = "{0} = {1}")
  @CsvSource({
      "DAY, 26", "TAG, 26",
      "WEEKDAY, 6", "WOCHENTAG, 6",
      "WEEKNUM, 39", "KW, 39",
      "MONTH, 9", "MONAT, 9",
      "QUARTER, 3", "QUARTAL, 3",
      "YEAR, 2026", "JAHR, 2026",
  })
  void aNumericKeywordResolvesToAnInteger(String keyword, int expected) {
    assertThat(ReportParameterResolver.resolveValue(keyword, TODAY)).contains(Integer.valueOf(expected));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-01-15", "2026-02-28", "2027-01-01", "2027-01-03", "2028-02-29", "2026-12-31"})
  void aGermanAliasMeansTheSameAsItsEnglishKeyword(LocalDate today) {
    GERMAN_TO_ENGLISH.forEach((german, english) ->
        assertThat(ReportParameterResolver.resolveValue(german, today))
            .as(german + " at " + today)
            .isPresent()
            .isEqualTo(ReportParameterResolver.resolveValue(english, today)));
  }

  @ParameterizedTest(name = "{0} in {1} = {2}")
  @CsvSource({
      // der Vormonat eines Januars liegt im Vorjahr
      "VORMONAT, 2026-01-15, 2025-12-01",
      "VORMONATSENDE, 2026-01-15, 2025-12-31",
      // Monatsende eines Schaltjahres
      "VORMONATSENDE, 2028-03-15, 2028-02-29",
      "VORMONAT, 2026-03-31, 2026-02-01",
      // Quartalsende in jedem Quartal, auch vom ersten Tag aus
      "EOQ, 2026-01-01, 2026-03-31",
      "EOQ, 2026-05-31, 2026-06-30",
      "EOQ, 2026-08-15, 2026-09-30",
      "EOQ, 2026-12-31, 2026-12-31",
      "BOQ, 2026-12-31, 2026-10-01",
      // die Woche über den Jahreswechsel
      "BOW, 2027-01-01, 2026-12-28",
      "EOW, 2026-12-31, 2027-01-03",
      // ein Sonntag gehört zur Woche davor
      "BOW, 2026-09-27, 2026-09-21",
      "EOW, 2026-09-27, 2026-09-27",
      "VORJAHR, 2026-01-01, 2025-01-01",
      "VORJAHRESENDE, 2026-01-01, 2025-12-31",
  })
  void dateKeywordsAtTheEdges(String keyword, LocalDate today, LocalDate expected) {
    assertThat(ReportParameterResolver.resolveValue(keyword, today)).contains(expected);
  }

  @ParameterizedTest(name = "{0} on {1} = {2}")
  @CsvSource({
      // ISO-Kalenderwoche über den Jahreswechsel: 2026 hat 53 Wochen
      "KW, 2025-12-29, 1",
      "KW, 2026-01-01, 1",
      "KW, 2026-12-28, 53",
      "KW, 2027-01-01, 53",
      "KW, 2027-01-03, 53",
      "KW, 2027-01-04, 1",
      // das Jahr ist das Kalenderjahr, nicht das Jahr der Kalenderwoche
      "JAHR, 2027-01-01, 2027",
      // ein Sonntag ist nach ISO der siebte Tag
      "WOCHENTAG, 2026-09-27, 7",
      "WOCHENTAG, 2026-09-21, 1",
      "QUARTAL, 2026-01-01, 1",
      "QUARTAL, 2026-12-31, 4",
      "TAG, 2026-02-28, 28",
  })
  void numericKeywordsAtTheEdges(String keyword, LocalDate today, int expected) {
    assertThat(ReportParameterResolver.resolveValue(keyword, today)).contains(Integer.valueOf(expected));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"heute", "Heute", "today", "kw", " HEUTE", "HEUTE ", "${HEUTE}", "FROM", "UNTIL",
      "VORGESTERN", "2026-09-26", "BOD"})
  void anythingElseIsNoKeyword(String value) {
    assertThat(ReportParameterResolver.resolveValue(value, TODAY)).isEmpty();
  }

  @Test
  void theSqlPlaceholdersKeepTheirMeaning() {
    var sql = "${TODAY} ${YESTERDAY} ${BOW} ${EOW} ${BOM} ${EOM} ${BOQ} ${EOQ} ${BOY} ${EOY}"
        + " ${WEEKNUM} ${MONTH} ${QUARTER} ${YEAR}";

    assertThat(ReportParameterResolver.resolve(sql, TODAY)).isEqualTo(
        "2026-09-26 2026-09-25 2026-09-21 2026-09-27 2026-09-01 2026-09-30 2026-07-01 2026-09-30"
            + " 2026-01-01 2026-12-31 39 9 3 2026");
  }

  @Test
  void theSqlPlaceholdersKnowTheGermanAliasesAndTheNewKeywords() {
    var sql = "where refdate between '${VORMONAT}' and '${VORMONATSENDE}'"
        + " and week(refdate, 3) = ${KW} and year(refdate) = ${JAHR}"
        + " and '${MORGEN}' > '${GESTERN}' and ${TAG} + ${WOCHENTAG} > 0 and '${END_LAST_YEAR}' < '${HEUTE}'";

    assertThat(ReportParameterResolver.resolve(sql, TODAY)).isEqualTo(
        "where refdate between '2026-08-01' and '2026-08-31'"
            + " and week(refdate, 3) = 39 and year(refdate) = 2026"
            + " and '2026-09-27' > '2026-09-25' and 26 + 6 > 0 and '2025-12-31' < '2026-09-26'");
  }

  @Test
  void aPlaceholderIsReplacedEveryTimeItOccurs() {
    assertThat(ReportParameterResolver.resolve("${BOM}..${BOM}", TODAY)).isEqualTo("2026-09-01..2026-09-01");
  }

  @Test
  void unknownPlaceholdersAndFromUntilStayInTheSql() {
    var sql = "select ${FROM}, ${UNTIL}, ${heute}, ${UNBEKANNT}, :von, HEUTE from dual";

    assertThat(ReportParameterResolver.resolve(sql, TODAY)).isEqualTo(sql);
  }

}
