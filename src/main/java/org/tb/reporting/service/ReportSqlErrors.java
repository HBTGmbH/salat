package org.tb.reporting.service;

import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.tb.reporting.domain.ReportResult.ErrorInfo;

/**
 * Was die Oberfläche über einen fehlgeschlagenen Reportlauf anzeigt. Ein Fehlschlag gehört zur
 * Reportpflege und ist kein Anwendungsfehler, deshalb wird jede {@link DataAccessException}
 * ausgewertet und nicht nur der Syntaxfehler (#1110).
 *
 * <p>Nur ein Teil dieser Ausnahmen führt eine {@link SQLException} mit sich. Wo keine dabei ist,
 * bleiben {@code sqlState} und {@code errorCode} leer — die Ansicht lässt beide Angaben von sich aus
 * weg, und eine fehlende Nebenangabe darf die Fehleranzeige nicht ihrerseits scheitern lassen.
 */
public final class ReportSqlErrors {

  private ReportSqlErrors() {
  }

  public static ErrorInfo describe(DataAccessException exception) {
    var sqlException = sqlExceptionOf(exception);
    return ErrorInfo.builder()
        .errorClass(exception.getClass().getSimpleName())
        .errorMessage(exception.getMostSpecificCause().getMessage())
        .sqlState(sqlException != null ? sqlException.getSQLState() : null)
        .errorCode(sqlException != null ? sqlException.getErrorCode() : null)
        .build();
  }

  private static SQLException sqlExceptionOf(DataAccessException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof SQLException sqlException) {
        return sqlException;
      }
      cause = cause.getCause() == cause ? null : cause.getCause();
    }
    return null;
  }

}
