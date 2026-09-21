package org.tb.reporting.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tb.common.exception.ErrorCode.AA_REQUIRED;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.tb.common.exception.ErrorCode.RP_REPORT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.RP_REPORT_PARAMETERS_MISSING;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.test.FixedClock;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportParameter;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.domain.ReportResultColumnHeader;
import org.tb.reporting.domain.ReportResultColumnValue;
import org.tb.reporting.domain.ReportResultRow;
import org.tb.reporting.service.ReportService;

@FixedClock
@ExtendWith(MockitoExtension.class)
class ReportRestEndpointTest {

  @Mock
  ReportService reportService;

  @Mock
  AuthorizedUser authorizedUser;

  @Captor
  ArgumentCaptor<List<ReportParameter>> parametersCaptor;

  @InjectMocks
  ReportRestEndpoint endpoint;

  @Test
  void shouldRejectUnauthenticated() {
    when(authorizedUser.isAuthenticated()).thenReturn(false);

    assertThatThrownBy(() -> endpoint.execute("Stunden", Map.of("report", "Stunden"), null))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(AA_REQUIRED.getCode());

    verify(reportService, never()).getReportDefinitionByName(anyString());
  }

  @Test
  void shouldAnswerWithColumnsAndRows() {
    authenticated();
    var reportDefinition = definition("Stunden", "select auftrag, stunden from t");
    when(reportService.getReportDefinitionByName("Stunden")).thenReturn(reportDefinition);
    when(reportService.executeChecked(any(), any())).thenReturn(result("auftrag", "111", "stunden", 7));

    var response = endpoint.execute("Stunden", Map.of("report", "Stunden"), "application/json");

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().report()).isEqualTo("Stunden");
    assertThat(response.getBody().columns()).containsExactly("auftrag", "stunden");
    assertThat(response.getBody().rows()).containsExactly(List.of("111", 7));
    assertThat(response.getHeaders().get(CONTENT_DISPOSITION)).isNull();
  }

  @Test
  void shouldNameTheFileWhenCsvIsRequested() {
    authenticated();
    when(reportService.getReportDefinitionByName("Stunden")).thenReturn(definition("Stunden", "select 1"));
    when(reportService.executeChecked(any(), any())).thenReturn(result("auftrag", "111"));

    var response = endpoint.execute("Stunden", Map.of("report", "Stunden"), "text/csv");

    assertThat(response.getHeaders().getFirst(CONTENT_DISPOSITION))
        .startsWith("attachment; filename=report-Stunden-")
        .endsWith(".csv");
  }

  @Test
  void aWildcardAcceptHeaderIsAnsweredWithJsonAndCarriesNoFileName() {
    authenticated();
    when(reportService.getReportDefinitionByName("Stunden")).thenReturn(definition("Stunden", "select 1"));
    when(reportService.executeChecked(any(), any())).thenReturn(result("auftrag", "111"));

    var response = endpoint.execute("Stunden", Map.of("report", "Stunden"), "*/*");

    assertThat(response.getHeaders().get(CONTENT_DISPOSITION)).isNull();
  }

  @Test
  void shouldPassOnTheParametersTheSqlNames() {
    authenticated();
    when(reportService.getReportDefinitionByName("Stunden"))
        .thenReturn(definition("Stunden", "select 1 from t where jahr = :jahr"));
    when(reportService.executeChecked(any(), parametersCaptor.capture())).thenReturn(result("a", 1));

    endpoint.execute("Stunden", Map.of("report", "Stunden", "jahr", "number,2025", "egal", "x"), "application/json");

    assertThat(parametersCaptor.getValue()).singleElement().satisfies(parameter -> {
      assertThat(parameter.getName()).isEqualTo("jahr");
      assertThat(parameter.getType()).isEqualTo("number");
      assertThat(parameter.getValue()).isEqualTo("2025");
    });
  }

  @Test
  void theReportNameIsNotPassedOnAsAReportParameter() {
    authenticated();
    // a report whose SQL names ":report" must not silently receive the report name as its value
    when(reportService.getReportDefinitionByName("Stunden"))
        .thenReturn(definition("Stunden", "select 1 from t where r = :report"));
    when(reportService.executeChecked(any(), parametersCaptor.capture())).thenReturn(result("a", 1));

    endpoint.execute("Stunden", Map.of("report", "Stunden"), "application/json");

    assertThat(parametersCaptor.getValue()).isEmpty();
  }

  @Test
  void aFailedLookupIsPassedOnUnchangedForTheAdviceToMap() {
    authenticated();
    var failure = new InvalidDataException(RP_REPORT_NOT_FOUND, "Fehlt");
    when(reportService.getReportDefinitionByName("Fehlt")).thenThrow(failure);

    assertThatThrownBy(() -> endpoint.execute("Fehlt", Map.of("report", "Fehlt"), null)).isSameAs(failure);
  }

  @Test
  void aFailedExecutionIsPassedOnUnchangedForTheAdviceToMap() {
    authenticated();
    when(reportService.getReportDefinitionByName("Stunden"))
        .thenReturn(definition("Stunden", "select 1 from t where jahr = :jahr"));
    var failure = new InvalidDataException(RP_REPORT_PARAMETERS_MISSING, "jahr");
    when(reportService.executeChecked(any(), any())).thenThrow(failure);

    assertThatThrownBy(() -> endpoint.execute("Stunden", Map.of("report", "Stunden"), null)).isSameAs(failure);
  }

  @Test
  void csvIsWantedOnlyWhenItIsNamedFirst() {
    assertThat(ReportRestEndpoint.wantsCsv("text/csv")).isTrue();
    assertThat(ReportRestEndpoint.wantsCsv("text/csv, application/json")).isTrue();
    assertThat(ReportRestEndpoint.wantsCsv("application/json;q=0.8, text/csv;q=0.9")).isTrue();
    assertThat(ReportRestEndpoint.wantsCsv("application/json, text/csv")).isFalse();
    assertThat(ReportRestEndpoint.wantsCsv("application/json")).isFalse();
    assertThat(ReportRestEndpoint.wantsCsv("*/*")).isFalse();
    assertThat(ReportRestEndpoint.wantsCsv("text/*")).isFalse();
    assertThat(ReportRestEndpoint.wantsCsv(null)).isFalse();
    assertThat(ReportRestEndpoint.wantsCsv("")).isFalse();
    assertThat(ReportRestEndpoint.wantsCsv("nicht/ein/medientyp")).isFalse();
  }

  private void authenticated() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
  }

  private static ReportDefinition definition(String name, String sql) {
    var reportDefinition = new ReportDefinition();
    reportDefinition.setName(name);
    reportDefinition.setSql(sql);
    return reportDefinition;
  }

  private static ReportResult result(Object... namesAndValues) {
    var row = new ReportResultRow();
    var headers = new java.util.ArrayList<ReportResultColumnHeader>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      var name = (String) namesAndValues[i];
      headers.add(new ReportResultColumnHeader(name));
      row.getColumnValues().put(name, new ReportResultColumnValue(namesAndValues[i + 1]));
    }
    return ReportResult.builder().parameters(List.of()).columnHeaders(headers).row(row).build();
  }

}
