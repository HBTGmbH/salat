package org.tb.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.H2;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.test.FixedClock;
import org.tb.common.viewhelper.FilterHintViewHelper;
import org.tb.reporting.auth.ReportAuthorization;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.persistence.ReportDefinitionRepository;
import org.tb.reporting.service.ExcelExportService;
import org.tb.reporting.service.ReportParameterResolver;
import org.tb.reporting.service.ReportService;

/**
 * Ein fehlgeschlagener Reportlauf endet in der Oberfläche mit der Ergebnisansicht und deren
 * Fehlermeldung, nicht mit der allgemeinen Fehlerseite (#1110). Diese Zusage hängt daran, dass die
 * Ausnahme den Controller nicht verlässt, und ist deshalb über die Web-Ebene geprüft — mit einer
 * echten Datenbank, weil erst sie entscheidet, welche Ausnahme ein Fehler auslöst.
 */
@FixedClock
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportControllerExecutionErrorTest {

  private static final long REPORT_ID = 1L;

  private final ReportDefinitionRepository reportDefinitionRepository = mock(ReportDefinitionRepository.class);
  private final ReportAuthorization reportAuthorization = mock(ReportAuthorization.class);

  private EmbeddedDatabase database;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    database = new EmbeddedDatabaseBuilder().setType(H2).generateUniqueName(true).build();

    when(reportAuthorization.isAuthorized(any(), any())).thenReturn(true);

    var reportService = new ReportService(reportDefinitionRepository, database, reportAuthorization,
        mock(AuthorizedUser.class), new ReportParameterResolver());
    var reportController = new ReportController(reportService, reportAuthorization,
        mock(ExcelExportService.class), mock(FilterHintViewHelper.class));

    mockMvc = MockMvcBuilders.standaloneSetup(reportController).build();
  }

  @AfterEach
  void shutDownDatabase() {
    database.shutdown();
  }

  @Test
  void a_report_failing_only_while_executing_answers_with_the_result_view() throws Exception {
    // syntaktisch gültig: der Teiler steht erst mit der gelesenen Zeile fest
    var sql = "select 100 / (t.n - t.n) as quotient from (select 1 as n) t";
    givenReport(sql);

    var reportResult = executeReport();

    assertThat(reportResult.isError()).isTrue();
    assertThat(reportResult.getErrorInfo()).isNotNull();
    // der Fall, der vorher als HTTP 500 endete — sonst prüft dieser Test den alten Zweig mit
    assertThat(reportResult.getErrorInfo().getErrorClass()).isNotEqualTo("BadSqlGrammarException");
    assertThat(reportResult.getErrorInfo().getErrorMessage()).isNotBlank();
    assertThat(reportResult.getSql()).isEqualTo(sql);
  }

  @Test
  void a_report_with_a_syntax_error_answers_with_the_result_view_as_before() throws Exception {
    var sql = "select * from a_table_that_does_not_exist";
    givenReport(sql);

    var reportResult = executeReport();

    assertThat(reportResult.isError()).isTrue();
    assertThat(reportResult.getErrorInfo().getErrorClass()).isEqualTo("BadSqlGrammarException");
    assertThat(reportResult.getErrorInfo().getSqlState()).isNotBlank();
    assertThat(reportResult.getErrorInfo().getErrorCode()).isNotNull();
    assertThat(reportResult.getSql()).isEqualTo(sql);
  }

  private void givenReport(String sql) {
    var reportDefinition = new ReportDefinition();
    reportDefinition.setName("kaputt");
    reportDefinition.setSql(sql);
    when(reportDefinitionRepository.findById(REPORT_ID)).thenReturn(Optional.of(reportDefinition));
  }

  private ReportResult executeReport() throws Exception {
    var modelAndView = mockMvc.perform(get("/reporting/reports/execute").param("id", String.valueOf(REPORT_ID)))
        .andExpect(status().isOk())
        .andExpect(view().name("reporting/report-result"))
        .andReturn()
        .getModelAndView();

    assertThat(modelAndView).isNotNull();
    return (ReportResult) modelAndView.getModel().get("reportResult");
  }

}
