package org.tb.dailyreport.domain;

import java.time.Duration;

/**
 * Die Bilanz eines Zeitraums, wie das Überstundenkonto sie rechnet (#760):
 * {@code diff = workingTime - target + adjustment}. Um {@code diff} ändert sich das
 * Überstundenkonto über genau diesen Zeitraum.
 *
 * @param target      die Sollarbeitszeit — Arbeitstage ohne Feiertage mal Tagesarbeitszeit
 * @param workingTime die gebuchte Arbeitszeit jeden Status; Bereitschaft zählt nicht dazu (#463)
 * @param adjustment  die Überstundenanpassungen, die im Zeitraum wirksam werden
 * @param diff        die Veränderung des Überstundenkontos im Zeitraum
 */
public record OvertimeBalance(Duration target, Duration workingTime, Duration adjustment, Duration diff) {
}
