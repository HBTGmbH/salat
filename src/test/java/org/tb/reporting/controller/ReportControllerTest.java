package org.tb.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.common.test.FixedClock;
import org.tb.common.util.DateTimeUtils;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportParameter;

@FixedClock
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

  @InjectMocks
  private ReportController reportController;

  @Mock
  private ReportDefinition reportDefinition;

  @Test
  void renderQueryParams_encodes_percent_in_value() {
    var params = List.of(new ReportParameter("sign", "string", "G%"));
    assertThat(ReportController.renderQueryParams(params)).isEqualTo("&sign=G%25");
  }

  @Test
  void renderQueryParams_encodes_percent_with_type_prefix() {
    var params = List.of(new ReportParameter("name", "number", "G%"));
    assertThat(ReportController.renderQueryParams(params)).isEqualTo("&name=number,G%25");
  }

  @Test
  void renderQueryParams_star_is_preserved_for_later_sql_translation() {
    // URLEncoder does not encode '*', so it passes through for getParameterMap to convert to '%'
    var params = List.of(new ReportParameter("sign", "string", "G*"));
    assertThat(ReportController.renderQueryParams(params)).isEqualTo("&sign=G*");
  }

  @Test
  void testCreateFileNameWithValidData() {
    reportDefinition = new ReportDefinition();
    reportDefinition.setName("Sales Report");

    List<ReportParameter> parameters = List.of(
        new ReportParameter("year", "number", "2025"),
        new ReportParameter("month", "number", "12")
    );

    String result = ReportController.createFileName(reportDefinition, parameters);

    String expectedDateTimePart = DateTimeUtils.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "_");
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

    String expectedDateTimePart = DateTimeUtils.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "_");
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

    String expectedDateTimePart = DateTimeUtils.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "_");
    assertEquals(
        "report-Special_Report_2025-[2025-12-01-2025-12-31]-" + expectedDateTimePart + ".xlsx",
        result
    );
  }

}