package org.tb.reporting.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportParameter;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

  @InjectMocks
  private ReportController reportController;

  @Mock
  private ReportDefinition reportDefinition;

  @Test
  void testCreateFileNameWithValidData() {
    reportDefinition = new ReportDefinition();
    reportDefinition.setName("Sales Report");

    List<ReportParameter> parameters = List.of(
        new ReportParameter("year", "number", "2025"),
        new ReportParameter("month", "number", "12")
    );

    String result = ReportController.createFileName(reportDefinition, parameters);

    String expectedDateTimePart = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "_");
    assertEquals(
        "report-Sales_Report-[2025-12]-" + expectedDateTimePart + ".xlsx",
        result
    );
  }

  @Test
  void testCreateFileNameWithEmptyParameters() {
    reportDefinition = new ReportDefinition();
    reportDefinition.setName("Summary Report");

    List<ReportParameter> parameters = List.of();

    String result = ReportController.createFileName(reportDefinition, parameters);

    String expectedDateTimePart = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "_");
    assertEquals(
        "report-Summary_Report-[]-" + expectedDateTimePart + ".xlsx",
        result
    );
  }

  @Test
  void testCreateFileNameWithSpecialCharactersInName() {
    reportDefinition = new ReportDefinition();
    reportDefinition.setName("Special*Report/2025");

    List<ReportParameter> parameters = List.of(
        new ReportParameter("from_date", "date", "2025-12-01"),
        new ReportParameter("to_date", "date", "2025-12-31")
    );

    String result = ReportController.createFileName(reportDefinition, parameters);

    String expectedDateTimePart = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "_");
    assertEquals(
        "report-Special_Report_2025-[2025-12-01-2025-12-31]-" + expectedDateTimePart + ".xlsx",
        result
    );
  }

}