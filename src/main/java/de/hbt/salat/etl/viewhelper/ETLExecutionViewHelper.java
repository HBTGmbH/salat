package de.hbt.salat.etl.viewhelper;

import static de.hbt.salat.etl.domain.ETLExecutionHistory.DATE_RANGE_PREFIX;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import de.hbt.salat.etl.domain.ETLExecutionHistory;

/**
 * Eine Ausführung, wie sie die Seite eines Laufs zeigt (#1324).
 *
 * @param position die laufende Nummer im Lauf, ab 1 — in der Reihenfolge, in der die Ausführungen
 *     liefen
 * @param periodFrom Beginn der Referenzperiode, oder {@code null}, wenn die Meldung keine nennt
 * @param periodUntil Ende der Referenzperiode, oder {@code null}
 */
public record ETLExecutionViewHelper(int position, String definitionName, LocalDate periodFrom,
                                     LocalDate periodUntil, LocalDateTime executedAt,
                                     boolean success, String message) {

  /**
   * Die Referenzperiode steht nur in der ersten Zeile der Meldung, eine eigene Spalte hat sie nicht:
   * {@code Date Range: 2026-09-01 - 2026-09-30} ({@code LocalDateRange#toString}).
   */
  private static final Pattern PERIOD = Pattern.compile(
      "^" + Pattern.quote(DATE_RANGE_PREFIX) + "(\\d{4}-\\d{2}-\\d{2}) - (\\d{4}-\\d{2}-\\d{2})");

  public static ETLExecutionViewHelper from(int position, ETLExecutionHistory execution) {
    var message = execution.getMessage();
    var period = message == null ? null : PERIOD.matcher(message);
    var periodFound = period != null && period.find();
    return new ETLExecutionViewHelper(position, execution.getEtlName(),
        periodFound ? parse(period.group(1)) : null,
        periodFound ? parse(period.group(2)) : null,
        execution.getExecutedAt(), execution.isSuccess(), message);
  }

  public boolean hasPeriod() {
    return periodFrom != null && periodUntil != null;
  }

  private static LocalDate parse(String isoDate) {
    try {
      return LocalDate.parse(isoDate);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

}
