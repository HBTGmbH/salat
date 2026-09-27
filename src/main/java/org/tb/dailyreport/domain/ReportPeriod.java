package org.tb.dailyreport.domain;

import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;

import java.time.LocalDate;
import org.tb.employee.domain.Employeecontract;

/**
 * In welchem Zeitraum ein Tag eines Vertrags liegt: abgenommen, freigegeben oder offen — ausgedrückt als der Status,
 * den eine Buchung an diesem Tag bekommt. Die Grenzen sind einschließlich: ein Tag am Freigabedatum ist freigegeben.
 *
 * <p>Die Frage steht hier einmal, weil drei Stellen sie stellen und dieselbe Antwort brauchen (#1164): der Status
 * einer neuen oder verschobenen Buchung, das Recht am Arbeitstag und das, was Tagesansicht und Liste je Tag anbieten.
 * Solange die Tagesansicht nach dem ganzen Monat entschied, bot sie an freigegebenen Tagen eines nur zum Teil
 * freigegebenen Monats das Anlegen an, das dann scheiterte.
 */
public final class ReportPeriod {

  private ReportPeriod() {
  }

  public static String statusOn(Employeecontract contract, LocalDate day) {
    LocalDate acceptedUntil = contract.getReportAcceptanceDate();
    if (acceptedUntil != null && !acceptedUntil.isBefore(day)) {
      return TIMEREPORT_STATUS_CLOSED;
    }
    LocalDate releasedUntil = contract.getReportReleaseDate();
    if (releasedUntil != null && !releasedUntil.isBefore(day)) {
      return TIMEREPORT_STATUS_COMMITED;
    }
    return TIMEREPORT_STATUS_OPEN;
  }
}
