package org.tb.dailyreport.viewhelper;

import java.time.YearMonth;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Die Adressen einer Übersicht vor der Freigabe (#760): die Seite selbst in ihrer aktuellen Sicht,
 * dieselbe Seite nach Auftrag und nach Tag, das Ziel der Aktion und der Weg zurück. Alle sind
 * wurzelrelativ und ohne Kontextpfad — so taugen sie als Rücksprungadresse des Buchungsformulars,
 * das mit {@code "redirect:" + returnUrl} zurückleitet, und im Template als {@code @{…}}.
 *
 * <p>Die Übersicht trägt den <em>gewählten</em> Monat ({@code until}), nicht das Ende des Zeitraums:
 * endet der Vertrag mitten im Monat, ist das Ende beschnitten, und eine Rückkehr soll dieselbe
 * Übersicht neu berechnen, nicht eine über einen anderen Monat.
 *
 * @param currentUrl die Übersicht in der Sicht, die gerade gezeigt wird
 * @param byOrderUrl die Übersicht nach Auftrag, der Standard — ohne {@code view}
 * @param byDayUrl   die Übersicht nach Tag
 * @param actionUrl  wohin die Aktion abgeschickt wird
 * @param backUrl    die Seite, von der die Übersicht geöffnet wurde
 */
public record ReviewLinks(String currentUrl, String byOrderUrl, String byDayUrl, String actionUrl, String backUrl) {

  /** Die Sicht nach Auftrag, der Standard. */
  public static final String VIEW_BY_ORDER = "order";
  /** Die Sicht nach Tag. */
  public static final String VIEW_BY_DAY = "day";

  /**
   * Die Sicht, die ein Aufruf verlangt: {@code day} nach Tag, alles andere nach Auftrag. Die Sicht
   * wird bewusst nicht gemerkt — jede neue Übersicht beginnt nach Auftrag (#760).
   */
  public static String viewOf(String view) {
    return VIEW_BY_DAY.equals(view) ? VIEW_BY_DAY : VIEW_BY_ORDER;
  }

  /**
   * @param reviewPath der Pfad der Übersicht
   * @param contractId der Vertrag, wenn die Adresse ihn nennen muss; {@code null}, wo die Seite
   *                   ihn aus der gemerkten Auswahl nimmt
   * @param until      der gewählte Monat
   * @param view       die gezeigte Sicht, {@code order} oder {@code day}
   */
  public static ReviewLinks of(String reviewPath, Long contractId, YearMonth until, String view,
      String actionUrl, String backUrl) {
    var byOrder = reviewUrl(reviewPath, contractId, until, VIEW_BY_ORDER);
    var byDay = reviewUrl(reviewPath, contractId, until, VIEW_BY_DAY);
    return new ReviewLinks(VIEW_BY_DAY.equals(view) ? byDay : byOrder, byOrder, byDay, actionUrl, backUrl);
  }

  private static String reviewUrl(String reviewPath, Long contractId, YearMonth until, String view) {
    var builder = UriComponentsBuilder.fromPath(reviewPath);
    if (contractId != null) {
      builder.queryParam("contractId", contractId);
    }
    builder.queryParam("until", until.toString());
    if (VIEW_BY_DAY.equals(view)) {
      builder.queryParam("view", VIEW_BY_DAY);
    }
    return builder.build().toUriString();
  }
}
