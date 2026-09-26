package org.tb.reporting.service;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;
import static java.time.temporal.TemporalAdjusters.lastDayOfYear;
import static java.time.temporal.TemporalAdjusters.nextOrSame;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Die Schlüsselwörter für Datumsangaben im Reporting — {@code HEUTE}, {@code BOM}, {@code VORMONAT},
 * {@code KW} und so weiter — und was sie an einem Stichtag bedeuten.
 *
 * <p>Ein Schlüsselwort wird an zwei Stellen verstanden, und an beiden bedeutet es dasselbe:
 * <ul>
 *   <li>im SQL eines Reports als <code>${NAME}</code>, ersetzt durch den Wert als Text
 *       ({@link #resolve(String, LocalDate)}): ein Datum als <code>yyyy-MM-dd</code>, eine Zahl als
 *       Ziffernfolge;</li>
 *   <li>als Wert eines Reportparameters, ohne <code>${}</code> ({@link #resolveValue(String, LocalDate)}):
 *       ein Datumsschlüsselwort als {@link LocalDate}, ein Zahlenschlüsselwort als {@link Integer}, damit
 *       der JDBC-Treiber den passenden Typ bindet. Welche Parametertypen welche Schlüsselwörter annehmen,
 *       entscheidet {@link ReportParameters}.</li>
 * </ul>
 * Früher kannte ein Parameterwert nur {@code TODAY} und {@code HEUTE}, während das SQL die ganze Liste
 * kannte (#657). Deshalb gibt es die Bedeutungen nur noch einmal, in {@link Keyword}.
 *
 * <p>Stichtag ist immer „heute" in der Zeitzone der Anwendung ({@code DateUtils.today()}), vom Aufrufer
 * übergeben — ein Report sieht also im Web, in einem geplanten Job und über die API denselben Tag.
 *
 * <p>Die Schlüsselwörter:
 * <table>
 *   <caption>Schlüsselwörter und ihre Werte</caption>
 *   <tr><th>Englisch</th><th>Deutsch</th><th>Wert</th></tr>
 *   <tr><td>TODAY</td><td>HEUTE</td><td>der Stichtag</td></tr>
 *   <tr><td>YESTERDAY</td><td>GESTERN</td><td>der Tag davor</td></tr>
 *   <tr><td>TOMORROW</td><td>MORGEN</td><td>der Tag danach</td></tr>
 *   <tr><td>BOW / EOW</td><td>—</td><td>Montag / Sonntag der laufenden Woche</td></tr>
 *   <tr><td>BOM / EOM</td><td>—</td><td>erster / letzter Tag des laufenden Monats</td></tr>
 *   <tr><td>BOQ / EOQ</td><td>—</td><td>erster / letzter Tag des laufenden Quartals</td></tr>
 *   <tr><td>BOY / EOY</td><td>—</td><td>erster / letzter Tag des laufenden Jahres</td></tr>
 *   <tr><td>LAST_MONTH</td><td>VORMONAT</td><td>erster Tag des Vormonats</td></tr>
 *   <tr><td>END_LAST_MONTH</td><td>VORMONATSENDE</td><td>letzter Tag des Vormonats</td></tr>
 *   <tr><td>LAST_YEAR</td><td>VORJAHR</td><td>erster Tag des Vorjahres</td></tr>
 *   <tr><td>END_LAST_YEAR</td><td>VORJAHRESENDE</td><td>letzter Tag des Vorjahres</td></tr>
 *   <tr><td>DAY</td><td>TAG</td><td>Tag im Monat (Zahl)</td></tr>
 *   <tr><td>WEEKDAY</td><td>WOCHENTAG</td><td>Wochentag nach ISO, 1 = Montag … 7 = Sonntag (Zahl)</td></tr>
 *   <tr><td>WEEKNUM</td><td>KW</td><td>Kalenderwoche nach ISO (Zahl)</td></tr>
 *   <tr><td>MONTH</td><td>MONAT</td><td>Monat, 1–12 (Zahl)</td></tr>
 *   <tr><td>QUARTER</td><td>QUARTAL</td><td>Quartal, 1–4 (Zahl)</td></tr>
 *   <tr><td>YEAR</td><td>JAHR</td><td>Jahr (Zahl)</td></tr>
 * </table>
 * Die Abkürzungen {@code BOW} … {@code EOY} haben keine deutsche Entsprechung; sie sind als Kürzel
 * gebräuchlich, eine Übersetzung wäre keine Hilfe.
 *
 * <p><b>Groß- und Kleinschreibung zählt.</b> Nur {@code HEUTE} ist ein Schlüsselwort, {@code heute}
 * oder {@code Heute} nicht. So war es schon, im SQL wie beim Parameterwert, und so bleibt ein
 * gewöhnliches Wort ein gewöhnliches Wort: die Großschreibung macht das Schlüsselwort erkennbar.
 *
 * <p>{@code FROM} und {@code UNTIL} gehören bewusst nicht dazu. Anders als bei den ETL-Jobs gibt es beim
 * Report keinen Bezugszeitraum, nur den Stichtag; einen Zeitraum übergibt ein Report als benannte
 * Parameter.
 *
 * <p>Die Klasse ist zustandslos und statisch, wie {@link ReportParameters}, das sie für jeden
 * Parameterwert aufruft. Bis #657 war sie eine Spring-Bean; die hatte nichts zu injizieren, hätte aber
 * bis in die statischen Parameterhelfer durchgereicht werden müssen.
 */
public final class ReportParameterResolver {

  /** Die Schlüsselwörter unter allen Namen, unter denen sie verstanden werden. */
  private static final Map<String, Keyword> KEYWORDS_BY_NAME = keywordsByName();

  private ReportParameterResolver() {
  }

  /**
   * Ersetzt jedes <code>${NAME}</code> im SQL, dessen Name ein Schlüsselwort ist, durch dessen Wert am
   * Stichtag. Unbekannte Platzhalter bleiben stehen.
   */
  public static String resolve(String sql, LocalDate today) {
    String result = sql;
    for (var entry : KEYWORDS_BY_NAME.entrySet()) {
      var placeholder = "${" + entry.getKey() + "}";
      if (result.contains(placeholder)) {
        result = result.replace(placeholder, String.valueOf(entry.getValue().valueAt(today)));
      }
    }
    return result;
  }

  /**
   * Der Wert eines einzelnen Schlüsselworts am Stichtag: ein {@link LocalDate} für ein
   * Datumsschlüsselwort, ein {@link Integer} für ein Zahlenschlüsselwort. Leer, wenn der Text kein
   * Schlüsselwort ist — der Aufrufer liest ihn dann als gewöhnlichen Wert.
   */
  public static Optional<Object> resolveValue(String keyword, LocalDate today) {
    if (keyword == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(KEYWORDS_BY_NAME.get(keyword)).map(k -> k.valueAt(today));
  }

  private static Map<String, Keyword> keywordsByName() {
    var result = new LinkedHashMap<String, Keyword>();
    for (Keyword keyword : Keyword.values()) {
      register(result, keyword.name(), keyword);
      if (keyword.germanName != null) {
        register(result, keyword.germanName, keyword);
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private static void register(Map<String, Keyword> keywordsByName, String name, Keyword keyword) {
    if (keywordsByName.putIfAbsent(name, keyword) != null) {
      throw new IllegalStateException("Report keyword " + name + " is defined twice");
    }
  }

  /**
   * Die einzige Stelle, an der steht, was ein Schlüsselwort bedeutet. Ein Datumsschlüsselwort liefert
   * ein {@link LocalDate}, ein Zahlenschlüsselwort ein {@link Integer}.
   */
  private enum Keyword {
    TODAY("HEUTE", today -> today),
    YESTERDAY("GESTERN", today -> today.minusDays(1)),
    TOMORROW("MORGEN", today -> today.plusDays(1)),
    BOW(null, today -> today.with(previousOrSame(MONDAY))),
    EOW(null, today -> today.with(nextOrSame(SUNDAY))),
    BOM(null, today -> today.withDayOfMonth(1)),
    EOM(null, today -> today.with(lastDayOfMonth())),
    BOQ(null, today -> today.with(today.getMonth().firstMonthOfQuarter()).withDayOfMonth(1)),
    EOQ(null, today -> today.with(today.getMonth().firstMonthOfQuarter()).plusMonths(2).with(lastDayOfMonth())),
    BOY(null, today -> today.withDayOfYear(1)),
    EOY(null, today -> today.with(lastDayOfYear())),
    LAST_MONTH("VORMONAT", today -> today.minusMonths(1).withDayOfMonth(1)),
    END_LAST_MONTH("VORMONATSENDE", today -> today.withDayOfMonth(1).minusDays(1)),
    LAST_YEAR("VORJAHR", today -> today.minusYears(1).withDayOfYear(1)),
    END_LAST_YEAR("VORJAHRESENDE", today -> today.withDayOfYear(1).minusDays(1)),
    DAY("TAG", LocalDate::getDayOfMonth),
    WEEKDAY("WOCHENTAG", today -> today.getDayOfWeek().getValue()),
    WEEKNUM("KW", today -> today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)),
    MONTH("MONAT", LocalDate::getMonthValue),
    QUARTER("QUARTAL", today -> today.get(IsoFields.QUARTER_OF_YEAR)),
    YEAR("JAHR", LocalDate::getYear);

    private final String germanName;
    private final Function<LocalDate, Object> value;

    Keyword(String germanName, Function<LocalDate, Object> value) {
      this.germanName = germanName;
      this.value = value;
    }

    Object valueAt(LocalDate today) {
      return value.apply(today);
    }
  }

}
