package de.hbt.salat.dailyreport.domain;

import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_COMMITTED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;

import java.time.LocalDate;
import java.time.YearMonth;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * In welchem Zeitraum ein Tag eines Vertrags liegt: abgenommen, freigegeben oder offen — ausgedrückt als der Status,
 * den eine Buchung an diesem Tag bekommt. Die Grenzen sind einschließlich: ein Tag am Freigabedatum ist freigegeben.
 *
 * <p>Die Frage steht hier einmal, weil drei Stellen sie stellen und dieselbe Antwort brauchen (#1164): der Status
 * einer neuen oder verschobenen Buchung, das Recht am Arbeitstag und das, was Tagesansicht und Liste je Tag anbieten.
 * Solange die Tagesansicht nach dem ganzen Monat entschied, bot sie an freigegebenen Tagen eines nur zum Teil
 * freigegebenen Monats das Anlegen an, das dann scheiterte. Dieselbe Antwort steht als Badge in der Überschrift des
 * Tages und des Monats, damit sichtbar ist, warum dort (je nach Rolle) nichts mehr geändert werden kann.
 */
public final class ReportPeriod {

  private ReportPeriod() {
  }

  /**
   * Wie weit Abnahme und Freigabe in einen Monat reichen, für die Überschrift der Monatsliste. Jedes
   * Datum ist auf den Monat zugeschnitten: {@code null}, wenn es vor dem Monat endet, sein letzter Tag,
   * wenn es darüber hinausreicht. Eine Freigabe, die nicht über die Abnahme hinausgeht, fehlt — der
   * Teil ist dann nur noch abgenommen.
   */
  public record Month(YearMonth month, LocalDate acceptedUntil, LocalDate releasedUntil) {

    public static Month of(Employeecontract contract, YearMonth month) {
      LocalDate accepted = within(month, contract.getReportAcceptanceDate());
      LocalDate released = within(month, contract.getReportReleaseDate());
      if (released != null && accepted != null && !released.isAfter(accepted)) {
        released = null;
      }
      return new Month(month, accepted, released);
    }

    public boolean acceptedWholeMonth() {
      return month.atEndOfMonth().equals(acceptedUntil);
    }

    public boolean releasedWholeMonth() {
      return month.atEndOfMonth().equals(releasedUntil);
    }

    private static LocalDate within(YearMonth month, LocalDate until) {
      if (until == null || until.isBefore(month.atDay(1))) {
        return null;
      }
      return until.isAfter(month.atEndOfMonth()) ? month.atEndOfMonth() : until;
    }
  }

  public static String statusOn(Employeecontract contract, LocalDate day) {
    LocalDate acceptedUntil = contract.getReportAcceptanceDate();
    if (acceptedUntil != null && !acceptedUntil.isBefore(day)) {
      return TIMEREPORT_STATUS_CLOSED;
    }
    LocalDate releasedUntil = contract.getReportReleaseDate();
    if (releasedUntil != null && !releasedUntil.isBefore(day)) {
      return TIMEREPORT_STATUS_COMMITTED;
    }
    return TIMEREPORT_STATUS_OPEN;
  }
}
