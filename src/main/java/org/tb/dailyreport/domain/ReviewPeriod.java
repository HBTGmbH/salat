package org.tb.dailyreport.domain;

import java.time.LocalDate;

/**
 * Der Zeitraum, den eine Freigabe oder Abnahme erfasst, beide Tage eingeschlossen (#760, #1122).
 *
 * <p>Der Zeitraum darf leer sein: {@code end} liegt dann vor {@code begin}, etwa wenn der gewählte
 * Monat schon freigegeben ist. Ein leerer Zeitraum ist ein Befund über den Zeitraum, kein Fehler
 * beim Rechnen — wer über seine Tage läuft, fragt deshalb zuerst {@link #isEmpty()}.
 */
public record ReviewPeriod(LocalDate begin, LocalDate end) {

    public boolean isEmpty() {
        return end.isBefore(begin);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(begin) && !date.isAfter(end);
    }
}
