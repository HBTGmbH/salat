package org.tb.dailyreport.viewhelper;

import java.time.Duration;
import org.tb.employee.domain.Employeecontract;

/**
 * Die Ampelskala einer Überstundenzelle (#1030).
 *
 * <p>Die Grenzen stehen hier und sonst nirgends: dieselbe Instanz färbt die Zahl und liefert der
 * Legende ihre Werte. Ein i18n-Text nennt deshalb keine Zahl, sondern bekommt sie als Argument —
 * andernfalls wäre die Legende beim nächsten Schwellenwechsel still falsch.
 *
 * <p>Die Skala gilt in <b>beide</b> Richtungen: zu viele Minusstunden werden genauso bemängelt wie
 * zu viele Überstunden. Sie ist zweiseitig, aber nicht notwendig symmetrisch — beim Gesamtsaldo ist
 * sie es bewusst nicht.
 *
 * <p>{@link #TOTAL} und {@link #CURRENT_MONTH} gelten für eine 40-Stunden-Woche. Ein Vertrag mit
 * anderer Wochenarbeitszeit bekommt sie über {@link #forContract} anteilig: 20 Überstunden sind bei
 * 20 Wochenstunden eine ganze Woche, bei 40 eine halbe (#1175). Die Grenzen werden dabei auf volle
 * Stunden gerundet — eine Ampel braucht keine Minuten, und die Legende bleibt lesbar.
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

  /** Die Wochenarbeitszeit, für die {@link #TOTAL} und {@link #CURRENT_MONTH} gelten. */
  static final Duration BASE_WEEKLY_WORKING_TIME = Duration.ofHours(40);

  /** Gesamtsaldo bei 40 Wochenstunden: grün von −20 h bis +40 h, rot unter −40 h und über +80 h. */
  public static final OvertimeScale TOTAL = new OvertimeScale(-40, -20, 40, 80);

  /** Monatssaldo bei 40 Wochenstunden: symmetrisch, grün innerhalb ±15 h, rot jenseits ±30 h. */
  public static final OvertimeScale CURRENT_MONTH = new OvertimeScale(-30, -15, 15, 30);

  /** Farbe ohne Saldo — eine Zelle ohne Wert warnt vor nichts. */
  public static final String NEUTRAL_COLOR_CLASS = "success";

  /**
   * Diese Skala für die Wochenarbeitszeit des Vertrags: fünf Tage zu seiner täglichen
   * Sollarbeitszeit, so wie das Dashboard auch das Wochensoll rechnet.
   */
  public OvertimeScale forContract(Employeecontract employeecontract) {
    return scaledTo(employeecontract.getDailyWorkingTime().multipliedBy(5));
  }

  /**
   * Jede Grenze im Verhältnis {@code weeklyWorkingTime} zu 40 Stunden, auf volle Stunden gerundet;
   * eine halbe Stunde rundet vom Nullpunkt weg, damit die Skala symmetrisch bleibt, wo sie es war.
   */
  public OvertimeScale scaledTo(Duration weeklyWorkingTime) {
    long weekly = weeklyWorkingTime.toMinutes();
    long base = BASE_WEEKLY_WORKING_TIME.toMinutes();
    return new OvertimeScale(
        scale(dangerBelowHours, weekly, base),
        scale(warningBelowHours, weekly, base),
        scale(warningAboveHours, weekly, base),
        scale(dangerAboveHours, weekly, base));
  }

  private static long scale(long hours, long weeklyMinutes, long baseMinutes) {
    return Long.signum(hours) * Math.round(Math.abs(hours) * (double) weeklyMinutes / baseMinutes);
  }

  /**
   * Die Tabler-Farbe der Stufe, in der dieser Saldo liegt. Eine Stufe beginnt jenseits ihrer Grenze:
   * +40:00 ist bei der Gesamtskala noch grün, +40:01 gelb — so, wie die Legende „über +40 h" sagt.
   */
  public String colorClass(Duration overtime) {
    if (overtime == null) {
      return NEUTRAL_COLOR_CLASS;
    }
    long minutes = overtime.toMinutes();
    if (minutes > dangerAboveHours * 60 || minutes < dangerBelowHours * 60) {
      return "danger";
    }
    if (minutes > warningAboveHours * 60 || minutes < warningBelowHours * 60) {
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
