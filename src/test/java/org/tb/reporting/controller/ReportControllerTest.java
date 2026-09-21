package org.tb.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.common.test.FixedClock;
import org.tb.reporting.domain.ReportParameter;

@FixedClock
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

  @InjectMocks
  private ReportController reportController;

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
    // URLEncoder does not encode '*', so it passes through for toParameterMap to convert to '%'
    var params = List.of(new ReportParameter("sign", "string", "G*"));
    assertThat(ReportController.renderQueryParams(params)).isEqualTo("&sign=G*");
  }

}
