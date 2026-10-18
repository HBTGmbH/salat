package de.hbt.salat.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.reporting.domain.ReportParameter;

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

  /** The focus starts behind what the request brought along — the first value still to type (#1493). */
  @Test
  void the_first_open_row_lies_behind_the_given_values() {
    var form = new ReportController.ExecuteForm();

    form.initParameters(List.of(new ReportParameter("start", "date", "2026-06-24")),
        new java.util.LinkedHashSet<>(List.of("person", "jahr")));

    assertThat(form.getParameters()).extracting(ReportParameter::getName).containsExactly("start", "person", "jahr");
    assertThat(form.getFirstOpen()).isEqualTo(1);
    assertThat(form.getParameters().get(2).getType()).isEqualTo("number");
    assertThat(form.getParameters().get(2).getValue()).isEqualTo("2026");
  }

  @Test
  void without_given_values_the_focus_starts_in_the_first_row() {
    var form = new ReportController.ExecuteForm();

    form.initParameters(List.of(), new java.util.LinkedHashSet<>(List.of("monat")));

    assertThat(form.getFirstOpen()).isZero();
  }

}
