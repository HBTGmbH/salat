package org.tb.dailyreport.viewhelper;

import java.time.LocalDate;

/**
 * Die Farben der Kennzahlen im Dashboard unter Buchungen (#1175) — alle an einer Stelle, damit die
 * Regel nachzulesen und zu prüfen ist. Die Überstunden stehen in {@link OvertimeScale}.
 *
 * <p>Die Regel:
 * <ul>
 *   <li><b>Rot</b> nur, wo etwas zu tun ist und es schon zu spät ist,</li>
 *   <li><b>Gelb</b> nur, wo bald etwas zu tun ist und man es noch rechtzeitig tun kann,</li>
 *   <li><b>Grün</b> nur, wo ein Ziel erreicht ist und das eine Aussage hat,</li>
 *   <li>alles andere ist <b>neutral</b>: normaler Fortschritt, im Plan liegende Werte, fehlende
 *       Werte.</li>
 * </ul>
 * Warnt eine Karte ständig, fällt die Warnung, die zählt, nicht mehr auf.
 */
public final class DashboardGrades {

  public static final String SUCCESS = "success";
  public static final String WARNING = "warning";
  public static final String DANGER = "danger";
  public static final String NEUTRAL = "secondary";

  private DashboardGrades() {
  }

  /**
   * Woche und Monat: grün, sobald das Soll erfüllt ist, bis dahin neutral. Ein Rückstand beim Buchen
   * meldet schon „Letzte Buchung", ein Gelb für den Fortschritt wäre die zweite Warnung für dieselbe
   * Sache — und stünde genau dann da, wenn man fast fertig ist.
   */
  public static String progress(int percent) {
    return percent >= 100 ? SUCCESS : NEUTRAL;
  }

  /**
   * Letzte Buchung, nach offenen Arbeitstagen bis einschließlich heute
   * ({@code UnbookedWorkingDays}): heute gebucht ist grün; nur der heutige Tag offen ist der
   * Normalfall und neutral, auch am Montag nach einer vollständig gebuchten Woche; ein ganzer
   * fehlender Arbeitstag lässt sich noch nachholen und ist gelb; ab zweien ist es zu spät.
   */
  public static String lastBooking(int openWorkingDays) {
    if (openWorkingDays <= 0) return SUCCESS;
    if (openWorkingDays == 1) return NEUTRAL;
    if (openWorkingDays == 2) return WARNING;
    return DANGER;
  }

  /**
   * Freigegeben bis: rot, wenn die Freigabe bis zum Ende des Vormonats fehlt, sonst grün. Nie
   * freigegeben heißt neuer Vertrag — {@code Employeecontract.getReleaseWarning()} warnt dann nicht,
   * und die Karte tut es auch nicht.
   */
  public static String release(LocalDate releasedUntil, boolean overdue) {
    if (releasedUntil == null) return NEUTRAL;
    return overdue ? DANGER : SUCCESS;
  }

  /**
   * Urlaub: bis zum Budget neutral, darüber rot. Wer im Herbst den größten Teil seines Urlaubs
   * genommen hat, hat nichts falsch gemacht, und ein nicht verbrauchtes Budget ist kein Ziel.
   */
  public static String vacation(boolean budgetExceeded) {
    return budgetExceeded ? DANGER : NEUTRAL;
  }
}
