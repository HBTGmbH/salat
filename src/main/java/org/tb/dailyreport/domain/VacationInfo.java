package org.tb.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Ein Urlaubsauftrag eines Vertrags und wie viel davon gebucht ist - oder, mit {@code special}, der
 * Sonderurlaub des Vertrags zusammengefasst (#1175).
 *
 * @param usedVacationMinutes    alle Buchungen auf den Urlaub, auch die geplanten
 * @param plannedVacationMinutes davon die nach heute — gebucht, aber noch nicht genommen
 * @param suborderIds            den Unterauftrag und den Zeitraum traegt die Angabe mit, damit ein Link genau
 *                               die Buchungen zeigen kann, die hier gezaehlt sind; Sonderurlaub kann auf
 *                               mehreren Unterauftraegen liegen
 * @param validFrom              erster Tag des Zeitraums
 * @param validUntil             letzter Tag, {@code null} bei offenem Ende
 * @param special                Sonderurlaub: ohne Budget, zaehlt nicht gegen den Anspruch
 */
public record VacationInfo(String suborderSign, Duration budget, long usedVacationMinutes, long plannedVacationMinutes,
                           List<Long> suborderIds, LocalDate validFrom, LocalDate validUntil, boolean special) {

  public VacationInfo {
    suborderIds = List.copyOf(suborderIds);
  }
}
