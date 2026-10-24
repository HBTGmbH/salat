package de.hbt.salat.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.util.DateTimeUtils;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.domain.ReportGroupInfo;
import de.hbt.salat.reporting.domain.ReportParameter;

@FixedClock
class ReportFileNamesTest {

  @Test
  void testCreateFileNameWithValidData() {
    var reportDefinition = new ReportDefinition();
    reportDefinition.setName("Sales Report");

    List<ReportParameter> parameters = List.of(
        new ReportParameter("year", "number", "2025"),
        new ReportParameter("month", "number", "12")
    );

    String result = ReportFileNames.create(reportDefinition, ReportGroupInfo.NONE, parameters, "xlsx");

    assertEquals("report-Sales_Report-[2025-12]-" + expectedDateTimePart() + ".xlsx", result);
  }

  @Test
  void testCreateFileNameWithEmptyParameters() {
    var reportDefinition = new ReportDefinition();
    reportDefinition.setName("Summary Report");

    String result = ReportFileNames.create(reportDefinition, ReportGroupInfo.NONE, List.of(), "xlsx");

    assertEquals("report-Summary_Report-[]-" + expectedDateTimePart() + ".xlsx", result);
  }

  @Test
  void testCreateFileNameWithSpecialCharactersInName() {
    var reportDefinition = new ReportDefinition();
    reportDefinition.setName("Special*Report/2025");

    List<ReportParameter> parameters = List.of(
        new ReportParameter("from_date", "date", "2025-12-01"),
        new ReportParameter("to_date", "date", "2025-12-31")
    );

    String result = ReportFileNames.create(reportDefinition, ReportGroupInfo.NONE, parameters, "xlsx");

    assertEquals(
        "report-Special_Report_2025-[2025-12-01-2025-12-31]-" + expectedDateTimePart() + ".xlsx",
        result
    );
  }

  @Test
  void theExtensionIsWhatTheCallerAsksFor() {
    var reportDefinition = new ReportDefinition();
    reportDefinition.setName("Summary Report");

    String result = ReportFileNames.create(reportDefinition, ReportGroupInfo.NONE, List.of(), "csv");

    assertEquals("report-Summary_Report-[]-" + expectedDateTimePart() + ".csv", result);
  }

  /** Group and title make the name the prefix made before (#1523), so a filed export keeps its place. */
  @Test
  void theGroupStandsWhereThePrefixStood() {
    var before = new ReportDefinition();
    before.setName("Monatsabschluss: Offene Freigaben");
    var after = new ReportDefinition();
    after.setName("Offene Freigaben");

    assertEquals(ReportFileNames.create(before, ReportGroupInfo.NONE, List.of(), "xlsx"),
        ReportFileNames.create(after, new ReportGroupInfo(3L, "Monatsabschluss", false), List.of(), "xlsx"));
  }

  private static String expectedDateTimePart() {
    return DateTimeUtils.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "_");
  }

}
