package org.tb.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Ein Urlaubsauftrag eines Vertrags und wie viel davon gebucht ist.
 *
 * @param usedVacationMinutes    alle Buchungen auf den Urlaub, auch die geplanten
 * @param plannedVacationMinutes davon die nach heute — gebucht, aber noch nicht genommen
 * @param suborderId den Unterauftrag und die Gültigkeit des Mitarbeiterauftrags trägt die Angabe
 *                   mit, damit ein Link genau die Buchungen zeigen kann, die hier gezählt sind (#1175)
 * @param validFrom  erster Tag des Mitarbeiterauftrags
 * @param validUntil letzter Tag, {@code null} bei offenem Ende
 */
public record VacationInfo(String suborderSign, Duration budget, long usedVacationMinutes, long plannedVacationMinutes,
                           long suborderId,
                           LocalDate validFrom, LocalDate validUntil) {
}
