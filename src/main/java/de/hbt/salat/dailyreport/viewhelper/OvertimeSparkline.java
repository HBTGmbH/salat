package de.hbt.salat.dailyreport.viewhelper;

import static java.util.Comparator.comparing;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.dailyreport.domain.OvertimeReport;
import de.hbt.salat.dailyreport.domain.OvertimeReportMonth;

/**
 * Der Verlauf des Überstundensaldos auf der Dashboard-Karte (#1294): die Monatsendsalden der letzten
 * {@link #MONTHS} Monate als Gebirge im Hintergrund.
 *
 * <p>Die Werte kommen aus demselben Bericht wie die Tabelle auf „Meine Konten“
 * ({@code OvertimeService.createDetailedReportForEmployee}) — {@code diffCumulative} eines Monats ist
 * sein Endsaldo, beim laufenden Monat der Stand bis gestern.
 *
 * <p>Tabler zeichnet die Sparkline aus {@code data-bs-values}, einer Liste von Zahlen; die Werte stehen
 * deshalb in Stunden mit Dezimalpunkt, unabhängig von der Sprache. Höchst- und Tiefststand sind die
 * Textalternative: das Bild selbst ist für Screenreader verborgen.
 *
 * @param values           die Endsalden in Stunden, ältester Monat zuerst, für {@code data-bs-values}
 * @param monthCount       wie viele Monate das Gebirge zeigt
 * @param highest          der höchste Endsaldo, formatiert wie die Zahl der Karte
 * @param highestMonth     der Monat dazu; bei Gleichstand der jüngste
 * @param lowest           der niedrigste Endsaldo
 * @param lowestMonth      der Monat dazu; bei Gleichstand der jüngste
 */
public record OvertimeSparkline(
    String values,
    int monthCount,
    String highest,
    String highestMonth,
    String lowest,
    String lowestMonth
) {

  /** So weit reicht der Verlauf zurück — die jüngere Entwicklung, nicht die ganze Vertragsgeschichte. */
  static final int MONTHS = 12;

  /**
   * Der Verlauf aus dem Bericht, oder leer, wenn er weniger als zwei Monate hat — ein einzelner Punkt
   * ist kein Verlauf.
   */
  public static Optional<OvertimeSparkline> from(OvertimeReport report) {
    var months = report.getMonths().stream()
        .sorted(comparing(OvertimeReportMonth::getYearMonth).reversed())
        .limit(MONTHS)
        .sorted(comparing(OvertimeReportMonth::getYearMonth))
        .toList();
    if (months.size() < 2) {
      return Optional.empty();
    }
    // bei Gleichstand der juengste Monat: max/min behalten den ersten, also rueckwaerts suchen
    var newestFirst = months.reversed();
    var highest = newestFirst.stream().max(comparing(OvertimeReportMonth::getDiffCumulative)).orElseThrow();
    var lowest = newestFirst.stream().min(comparing(OvertimeReportMonth::getDiffCumulative)).orElseThrow();
    return Optional.of(new OvertimeSparkline(
        months.stream().map(month -> hours(month.getDiffCumulative())).collect(Collectors.joining(",")),
        months.size(),
        DurationUtils.format(highest.getDiffCumulative()),
        DateUtils.format(highest.getYearMonth()),
        DurationUtils.format(lowest.getDiffCumulative()),
        DateUtils.format(lowest.getYearMonth())));
  }

  private static String hours(Duration duration) {
    return String.format(Locale.ROOT, "%.2f", duration.toMinutes() / 60.0);
  }

}
