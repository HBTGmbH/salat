package de.hbt.salat.dailyreport.domain;

import java.time.Duration;

/**
 * Die Bilanz eines Zeitraums, wie das Überstundenkonto sie rechnet (#760):
 * {@code diff = workingTime + surcharge - target + adjustment}. Um {@code diff} ändert sich das
 * Überstundenkonto über genau diesen Zeitraum.
 *
 * @param target      die Sollarbeitszeit — Arbeitstage ohne Feiertage mal Tagesarbeitszeit
 * @param workingTime die gebuchte Arbeitszeit jeden Status; Bereitschaft zählt nicht dazu (#463)
 * @param adjustment  die Überstundenanpassungen, die im Zeitraum wirksam werden
 * @param diff        die Veränderung des Überstundenkontos im Zeitraum
 * @param surcharge   die Zeitgutschrift aus Nacht-, Wochenend- und Feiertagseinsätzen (#1469), getrennt
 *                    von der gebuchten Zeit
 */
public record OvertimeBalance(Duration target, Duration workingTime, Duration adjustment, Duration diff,
                              Duration surcharge) {

  /** Ohne Zeitgutschrift. */
  public OvertimeBalance(Duration target, Duration workingTime, Duration adjustment, Duration diff) {
    this(target, workingTime, adjustment, diff, Duration.ZERO);
  }
}
