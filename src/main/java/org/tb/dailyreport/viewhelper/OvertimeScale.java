package org.tb.dailyreport.viewhelper;

import java.time.Duration;

/**
 * Die Ampelskala einer Überstundenzelle des Dashboards (#1030).
 *
 * <p>Die Grenzen stehen hier und sonst nirgends: dieselbe Instanz färbt die Zahl und liefert der
 * Legende ihre Werte. Ein i18n-Text nennt deshalb keine Zahl, sondern bekommt sie als Argument —
 * andernfalls wäre die Legende beim nächsten Schwellenwechsel still falsch.
 *
 * <p>Die Skala gilt in <b>beide</b> Richtungen: zu viele Minusstunden werden genauso bemängelt wie
 * zu viele Überstunden. Sie ist zweiseitig, aber nicht notwendig symmetrisch — beim Gesamtsaldo ist
 * sie es bewusst nicht.
 *
 * <p>Bewertet wird die vorzeichenbehaftete Dauer, so wie {@code OvertimeService} sie ablegt. Das
 * Merkmal {@code negative} daneben ist nur die Pfeilrichtung; wer es ein zweites Mal auf die Dauer
 * anwendet, prüft bei Minusstunden die positive Seite der Skala.
 */
public record OvertimeScale(
    long dangerBelowHours,
    long warningBelowHours,
    long warningAboveHours,
    long dangerAboveHours
) {

  /** Gesamtsaldo: grün von −20 h bis +40 h, rot unter −40 h und über +80 h. */
  public static final OvertimeScale TOTAL = new OvertimeScale(-40, -20, 40, 80);

  /** Monatssaldo: symmetrisch, grün innerhalb ±15 h, rot jenseits ±30 h. */
  public static final OvertimeScale CURRENT_MONTH = new OvertimeScale(-30, -15, 15, 30);

  /** Farbe ohne Saldo — eine Zelle ohne Wert warnt vor nichts. */
  public static final String NEUTRAL_COLOR_CLASS = "success";

  /**
   * Die Tabler-Farbe der Stufe, in der dieser Saldo liegt. Angeschnittene Stunden zählen zur
   * kleineren Stufe, weil {@link Duration#toHours()} zur Null hin abschneidet.
   */
  public String colorClass(Duration overtime) {
    if (overtime == null) {
      return NEUTRAL_COLOR_CLASS;
    }
    long hours = overtime.toHours();
    if (hours > dangerAboveHours || hours < dangerBelowHours) {
      return "danger";
    }
    if (hours > warningAboveHours || hours < warningBelowHours) {
      return "warning";
    }
    return NEUTRAL_COLOR_CLASS;
  }

  /** Untere rote Grenze, mit Vorzeichen — für die Legende. */
  public String dangerBelowLabel() {
    return signed(dangerBelowHours);
  }

  /** Untere gelbe Grenze, mit Vorzeichen — für die Legende. */
  public String warningBelowLabel() {
    return signed(warningBelowHours);
  }

  /** Obere gelbe Grenze, mit Vorzeichen — für die Legende. */
  public String warningAboveLabel() {
    return signed(warningAboveHours);
  }

  /** Obere rote Grenze, mit Vorzeichen — für die Legende. */
  public String dangerAboveLabel() {
    return signed(dangerAboveHours);
  }

  /**
   * Das Vorzeichen steht immer da, auch das Plus: es ist der kürzeste Weg, in der Legende zu zeigen,
   * dass die Skala Minusstunden genauso bewertet wie Überstunden.
   */
  private static String signed(long hours) {
    return "%+d".formatted(hours);
  }

}
