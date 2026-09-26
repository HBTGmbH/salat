package org.tb.dailyreport.domain;

import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employeecontract;

/**
 * Die Regel „Arbeitstag ohne Buchung" — es gibt sie genau einmal im Code (#1124). Die Freigabe
 * meldet solche Tage als {@code WD_NO_TIMEREPORT}, das Dashboard weist auf die der Vorwoche hin,
 * und „Rest nicht gearbeitet" markiert die eines Monats als nicht gearbeitet; alle drei fragen
 * diese Klasse.
 *
 * <p>Ein Tag ist ein Arbeitstag ohne Buchung, wenn er
 * <ul>
 *   <li>im Gültigkeitszeitraum des Vertrags liegt ({@link Employeecontract#isValidAt}),</li>
 *   <li>ein Wochentag von Montag bis Freitag ist ({@link DateUtils#isWeekday}),</li>
 *   <li>kein Feiertag ist,</li>
 *   <li>nicht als {@link Workingday.WorkingDayType#NOT_WORKED} markiert ist — ohne
 *       {@link Workingday} gilt der Tag als gearbeitet, das ist die Voreinstellung —</li>
 *   <li>und an ihm nichts gebucht ist.</li>
 * </ul>
 *
 * <p>Welche Buchungen einen Tag zu einem gebuchten machen, entscheidet der Aufrufer, und nur darin
 * unterscheiden sich die Aufrufer: die Freigabe zählt allein die offenen Buchungen, die der
 * Freigebende lesen darf, denn nur die gibt sie frei; für den Hinweis im Dashboard und für
 * „Rest nicht gearbeitet" zählt jede Buchung, gleich welchen Status und gleich, ob der Aufrufer sie
 * lesen darf. Ebenfalls nicht Teil der Regel sind die Ausnahmen für Freelancer, Personen mit Status
 * {@code restricted} und Verträge ohne Sollarbeitszeit: sie gelten nur für den Hinweis (#1123), die
 * Freigabe prüft auch diese Verträge.
 *
 * <p>Den Vertrag schneidet die Regel selbst zu, der Zeitraum darf also über ihn hinausreichen.
 */
public final class UnbookedWorkingDays {

    private UnbookedWorkingDays() {
    }

    /**
     * Die Arbeitstage ohne Buchung in {@code [from, until]}, aufsteigend sortiert.
     *
     * <p>{@code until == from - 1} ist ein leerer Zeitraum und ergibt eine leere Liste. Liegt
     * {@code until} weiter zurück, wirft {@link LocalDate#datesUntil} eine
     * {@link IllegalArgumentException} — so wie die Schleife in {@code validateForRelease}, aus der
     * diese Regel stammt. Dorthin geriet bis #760 die Freigabe eines Monats vor der letzten
     * Freigabe. Die Regel fängt das bewusst nicht ab: ein verkehrter Zeitraum ist ein Befund über
     * den Zeitraum, nicht über einen Tag, und gehört zum Aufrufer — die Prüfung vor der Freigabe
     * meldet ihn als eigenen Befund ({@code RL-0009}) und fragt die Regel dann gar nicht erst.
     *
     * @param bookedDays     die Tage, an denen eine Buchung zählt — welche, entscheidet der Aufrufer
     * @param workingDays    die Arbeitstage des Vertrags im Zeitraum, nach Datum
     * @param publicHolidays die Feiertage im Zeitraum
     */
    public static List<LocalDate> between(LocalDate from, LocalDate until, Employeecontract contract,
                                          Set<LocalDate> bookedDays, Map<LocalDate, Workingday> workingDays,
                                          Set<LocalDate> publicHolidays) {
        return from.datesUntil(until.plusDays(1))
            .filter(contract::isValidAt)
            .filter(DateUtils::isWeekday)
            .filter(day -> !publicHolidays.contains(day))
            .filter(day -> !isMarkedNotWorked(workingDays.get(day)))
            .filter(day -> !bookedDays.contains(day))
            .toList();
    }

    private static boolean isMarkedNotWorked(Workingday workingDay) {
        return workingDay != null && workingDay.getType() == NOT_WORKED;
    }
}
