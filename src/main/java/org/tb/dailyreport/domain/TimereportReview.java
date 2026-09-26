package org.tb.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.tb.common.exception.ServiceFeedbackMessage;

/**
 * Die Übersicht über einen Zeitraum, bevor er freigegeben (#760) oder abgenommen (#1122) wird: die
 * Bilanz, die Buchungen nach Auftrag und nach Tag, die Befunde der Prüfung und was der angemeldete
 * Benutzer darin bearbeiten darf. Der Service stellt sie zusammen; die Seite formatiert nur noch
 * und entscheidet keine Regel selbst.
 *
 * @param ownContract           der Vertrag gehört dem angemeldeten Benutzer
 * @param releasedUntil         Freigabedatum des Vertrags beim Anzeigen, {@code null} ohne Freigabe
 * @param acceptedUntil         Abnahmedatum des Vertrags beim Anzeigen, {@code null} ohne Abnahme
 * @param period                der Zeitraum, den die Aktion erfasst; er darf leer sein
 * @param balance               die Bilanz des Zeitraums; {@code null}, wenn der Zeitraum leer oder
 *                              gesperrt ist oder der Vertrag keine Tagesarbeitszeit hat
 * @param overtimeAccount       für den Vertrag wird ein Überstundenkonto geführt — er hat eine
 *                              Tagesarbeitszeit
 * @param standby               Bereitschaft im Zeitraum; sie zählt nicht zur Arbeitszeit (#463)
 * @param periodFindings        Befunde über den ganzen Zeitraum, und Befunde eines Tages, der
 *                              außerhalb des Zeitraums liegt — sie sperren die Aktion ebenso
 * @param dayFindings           Befunde einzelner Tage im Zeitraum, nach Datum sortiert
 * @param byOrder               die Buchungen des Zeitraums nach Auftrag
 * @param byMonth               alle Tage des Zeitraums, nach Monat gegliedert
 * @param beforePeriod          Buchungen vor dem Zeitraum, die die Aktion trotzdem erfasst —
 *                              Altbestand, normalerweise keine; sie stehen hier, damit die
 *                              Aktion nichts erfasst, was die Übersicht nicht gezeigt hat
 * @param editableTimereportIds die gezeigten Buchungen, die der angemeldete Benutzer bearbeiten
 *                              darf
 * @param canCreate             der angemeldete Benutzer darf an einem Tag ohne Buchung eine anlegen
 * @param actionAllowed         der Zeitraum ist nicht leer, und es gibt keinen Befund
 * @param timereportCount       die Zahl der Buchungen im Zeitraum
 */
public record TimereportReview(
    long employeecontractId, String employeeName, String employeeSign, boolean ownContract,
    LocalDate releasedUntil, LocalDate acceptedUntil,
    ReviewPeriod period,
    OvertimeBalance balance,
    boolean overtimeAccount,
    Duration standby,
    List<ServiceFeedbackMessage> periodFindings,
    List<DayFinding> dayFindings,
    List<OrderGroup> byOrder,
    List<MonthGroup> byMonth,
    List<TimereportDTO> beforePeriod,
    Set<Long> editableTimereportIds,
    boolean canCreate,
    boolean actionAllowed,
    int timereportCount) {

    /** Ein Befund der Prüfung an einem Tag. */
    public record DayFinding(LocalDate date, ServiceFeedbackMessage message) {}

    /**
     * Die Buchungen eines Unterauftrags, nach Datum und Reihenfolge des Tages.
     *
     * @param standby  der Unterauftrag ist eine Bereitschaft
     * @param duration die Summe der Buchungen, bei einer Bereitschaft also deren Dauer
     */
    public record OrderGroup(long suborderId, String completeOrderSign, String customerorderSign,
        String customerorderDescription, String suborderDescription, String customerShortname,
        boolean standby, Duration duration, List<TimereportDTO> timereports) {}

    /**
     * Die Tage eines Monats im Zeitraum.
     *
     * @param workingTime die Arbeitszeit des Monats ohne Bereitschaft
     * @param standby     die Bereitschaft des Monats
     */
    public record MonthGroup(YearMonth month, Duration workingTime, Duration standby, List<DayEntry> days) {}

    /**
     * Ein Tag im Zeitraum.
     *
     * @param workingTime       die Arbeitszeit des Tages ohne Bereitschaft
     * @param standby           die Bereitschaft des Tages
     * @param publicHolidayName der Name des Feiertags, {@code null} an einem anderen Tag
     * @param notWorked         der Tag ist als nicht gearbeitet markiert
     * @param withoutBooking    ein Arbeitstag ohne Buchung — wie die Regel
     *                          {@link UnbookedWorkingDays} ihn bestimmt, nie aus den Buchungen
     *                          dieses Eintrags abgeleitet
     * @param findings          die Befunde der Prüfung an diesem Tag
     */
    public record DayEntry(LocalDate date, List<TimereportDTO> timereports, Duration workingTime,
        Duration standby, boolean weekend, String publicHolidayName, boolean notWorked,
        boolean withoutBooking, List<ServiceFeedbackMessage> findings) {}
}
