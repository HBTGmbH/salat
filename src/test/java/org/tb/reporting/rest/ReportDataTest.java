package org.tb.reporting.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.domain.ReportResultColumnHeader;
import org.tb.reporting.domain.ReportResultColumnValue;
import org.tb.reporting.domain.ReportResultRow;

class ReportDataTest {

  @Test
  void valuesFollowTheColumnOrderOfTheReport() {
    // ReportResultRow holds its values in a HashMap - the order can only come from the headers
    var reportResult = ReportResult.builder()
        .parameters(List.of())
        .columnHeaders(List.of(
            new ReportResultColumnHeader("zulu"),
            new ReportResultColumnHeader("alpha"),
            new ReportResultColumnHeader("mike")
        ))
        .row(row("zulu", 1, "alpha", 2, "mike", 3))
        .build();

    var result = ReportData.valueOf(definition("Report"), reportResult);

    assertThat(result.columns()).containsExactly("zulu", "alpha", "mike");
    assertThat(result.rows()).containsExactly(List.of(1, 2, 3));
  }

  @Test
  void anEmptyResultStillKnowsItsColumns() {
    var reportResult = ReportResult.builder()
        .parameters(List.of())
        .columnHeaders(List.of(new ReportResultColumnHeader("jahr")))
        .build();

    var result = ReportData.valueOf(definition("Report"), reportResult);

    assertThat(result.columns()).containsExactly("jahr");
    assertThat(result.rows()).isEmpty();
  }

  @Test
  void aMissingValueIsNullAndKeepsItsPlace() {
    var row = new ReportResultRow();
    row.getColumnValues().put("a", new ReportResultColumnValue(null));

    var reportResult = ReportResult.builder()
        .parameters(List.of())
        .columnHeaders(List.of(new ReportResultColumnHeader("a"), new ReportResultColumnHeader("b")))
        .row(row)
        .build();

    var result = ReportData.valueOf(definition("Report"), reportResult);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().getFirst()).containsExactly(null, null);
  }

  @Test
  void theReportIsNamedInTheAnswer() {
    var reportResult = ReportResult.builder().parameters(List.of()).columnHeaders(List.of()).build();

    assertThat(ReportData.valueOf(definition("Stunden pro Auftrag"), reportResult).report())
        .isEqualTo("Stunden pro Auftrag");
  }

  @Test
  void nullBecomesAnEmptyCsvCell() {
    assertThat(ReportData.textRow(List.of(1, "x"))).containsExactly("1", "x");
    assertThat(ReportData.textRow(java.util.Arrays.asList(null, "x"))).containsExactly("", "x");
  }

  private static ReportDefinition definition(String name) {
    var reportDefinition = new ReportDefinition();
    reportDefinition.setName(name);
    return reportDefinition;
  }

  private static ReportResultRow row(Object... namesAndValues) {
    var row = new ReportResultRow();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      row.getColumnValues().put((String) namesAndValues[i], new ReportResultColumnValue(namesAndValues[i + 1]));
    }
    return row;
  }

}
