package org.tb.reporting.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.domain.ReportResultColumnHeader;
import org.tb.reporting.domain.ReportResultRow;

/**
 * Das Ergebnis eines Reports für einen maschinellen Abzug.
 *
 * <p>Spalten und Zeilen stehen getrennt, weil die Spaltenfolge zur Aussage gehört: sie ist die des
 * Reports, und sie steht auch dann fest, wenn der Report keine Zeile liefert. Die Werte einer Zeile
 * folgen der Reihenfolge von {@link #columns()} — anders als {@code ReportResultRow}, das seine
 * Werte in einer {@code HashMap} hält.
 *
 * <p>Das ausgeführte SQL ist nicht Teil der Antwort (#1035).
 */
@Schema(description = "Ergebnis eines Reports: Spaltennamen und Zeilen in der Reihenfolge der Spalten")
public record ReportData(

    @Schema(description = "Name des ausgeführten Reports", example = "Stunden pro Auftrag")
    String report,

    @Schema(description = "Spaltennamen in der Reihenfolge des Reports", example = "[\"auftrag\",\"stunden\"]")
    List<String> columns,

    @Schema(description = "Zeilen; jede Zeile hat so viele Werte wie es Spalten gibt")
    List<List<Object>> rows

) {

  public static ReportData valueOf(ReportDefinition reportDefinition, ReportResult reportResult) {
    var columns = reportResult.getColumnHeaders().stream()
        .map(ReportResultColumnHeader::getName)
        .toList();
    var rows = reportResult.getRows().stream()
        .map(row -> valuesOf(row, columns))
        .toList();
    return new ReportData(reportDefinition.getName(), columns, rows);
  }

  private static List<Object> valuesOf(ReportResultRow row, List<String> columns) {
    return columns.stream()
        .map(row.getColumnValues()::get)
        .map(value -> value == null ? null : value.getValue())
        .toList();
  }

  /** Die Werte einer Zeile als Text, wie sie in eine CSV-Zelle gehören; {@code null} wird leer. */
  static List<String> textRow(List<Object> row) {
    return row.stream().map(value -> value == null ? "" : String.valueOf(value)).toList();
  }

}
