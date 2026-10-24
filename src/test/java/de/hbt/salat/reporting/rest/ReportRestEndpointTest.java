package de.hbt.salat.reporting.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.AA_REQUIRED;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static de.hbt.salat.common.exception.ErrorCode.RP_REPORT_ID_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.RP_REPORT_ID_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.RP_REPORT_BY_NAME_REMOVED;
import static de.hbt.salat.common.exception.ErrorCode.RP_REPORT_NOT_SPECIFIED;
import static de.hbt.salat.common.exception.ErrorCode.RP_REPORT_PARAMETERS_MISSING;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.domain.ReportGroupInfo;
import de.hbt.salat.reporting.domain.ReportParameter;
import de.hbt.salat.reporting.domain.ReportResult;
import de.hbt.salat.reporting.domain.ReportResultColumnHeader;
import de.hbt.salat.reporting.domain.ReportResultColumnValue;
import de.hbt.salat.reporting.domain.ReportResultRow;
import de.hbt.salat.reporting.service.ReportService;

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

    assertThatThrownBy(() -> endpoint.execute("42", null, Map.of("reportId", "42"), null))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(AA_REQUIRED.getCode());

    verify(reportService, never()).getReportDefinitionById(anyLong());
  }

  @Test
  void shouldAnswerWithColumnsAndRows() {
    authenticated();
    var reportDefinition = definition("Stunden", "select auftrag, stunden from t");
    when(reportService.getReportDefinitionById(42L)).thenReturn(reportDefinition);
    when(reportService.executeChecked(any(), any())).thenReturn(result("auftrag", "111", "stunden", 7));

    var response = endpoint.execute("42", null, Map.of("reportId", "42"), "application/json");

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().report()).isEqualTo("Stunden");
    assertThat(response.getBody().columns()).containsExactly("auftrag", "stunden");
    assertThat(response.getBody().rows()).containsExactly(List.of("111", 7));
    assertThat(response.getHeaders().get(CONTENT_DISPOSITION)).isNull();
  }

  @Test
  void shouldNameTheFileWhenCsvIsRequested() {
    authenticated();
    when(reportService.getReportDefinitionById(42L)).thenReturn(definition("Stunden", "select 1"));
    when(reportService.getGroupOf(any())).thenReturn(ReportGroupInfo.NONE);
    when(reportService.executeChecked(any(), any())).thenReturn(result("auftrag", "111"));

    var response = endpoint.execute("42", null, Map.of("reportId", "42"), "text/csv");

    assertThat(response.getHeaders().getFirst(CONTENT_DISPOSITION))
        .startsWith("attachment; filename=report-Stunden-")
        .endsWith(".csv");
  }

  /** Group and title, as the prefix stood in the name before (#1523). */
  @Test
  void theFileNameCarriesTheGroup() {
    authenticated();
    when(reportService.getReportDefinitionById(42L)).thenReturn(definition("Stunden", "select 1"));
    when(reportService.getGroupOf(any())).thenReturn(new ReportGroupInfo(3L, "Monatsabschluss", false));
    when(reportService.executeChecked(any(), any())).thenReturn(result("auftrag", "111"));

    var response = endpoint.execute("42", null, Map.of("reportId", "42"), "text/csv");

    assertThat(response.getHeaders().getFirst(CONTENT_DISPOSITION))
        .startsWith("attachment; filename=report-Monatsabschluss_Stunden-");
  }

  @Test
  void aWildcardAcceptHeaderIsAnsweredWithJsonAndCarriesNoFileName() {
    authenticated();
    when(reportService.getReportDefinitionById(42L)).thenReturn(definition("Stunden", "select 1"));
    when(reportService.executeChecked(any(), any())).thenReturn(result("auftrag", "111"));

    var response = endpoint.execute("42", null, Map.of("reportId", "42"), "*/*");

    assertThat(response.getHeaders().get(CONTENT_DISPOSITION)).isNull();
  }

  @Test
  void shouldPassOnTheParametersTheSqlNames() {
    authenticated();
    when(reportService.getReportDefinitionById(42L))
        .thenReturn(definition("Stunden", "select 1 from t where jahr = :jahr"));
    when(reportService.executeChecked(any(), parametersCaptor.capture())).thenReturn(result("a", 1));

    endpoint.execute("42", null, Map.of("reportId", "42", "jahr", "number,2025", "egal", "x"), "application/json");

    assertThat(parametersCaptor.getValue()).singleElement().satisfies(parameter -> {
      assertThat(parameter.getName()).isEqualTo("jahr");
      assertThat(parameter.getType()).isEqualTo("number");
      assertThat(parameter.getValue()).isEqualTo("2025");
    });
  }

  @Test
  void theReportNameIsNotPassedOnAsAReportParameter() {
    authenticated();
    // "report" stays reserved (#1523): a report whose SQL names ":report" must not silently receive it
    when(reportService.getReportDefinitionById(42L))
        .thenReturn(definition("Stunden", "select 1 from t where r = :report"));
    when(reportService.executeChecked(any(), parametersCaptor.capture())).thenReturn(result("a", 1));

    endpoint.execute("42", "Stunden", Map.of("reportId", "42", "report", "Stunden"), "application/json");

    assertThat(parametersCaptor.getValue()).isEmpty();
  }

  /** Called by name alone, the answer says what to use instead (#1523). */
  @Test
  void aReportIsNoLongerCalledByItsName() {
    authenticated();

    assertThatThrownBy(() -> endpoint.execute(null, "Stunden", Map.of("report", "Stunden"), null))
        .isInstanceOfSatisfying(InvalidDataException.class, e -> {
          assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(RP_REPORT_BY_NAME_REMOVED);
          assertThat(e.getMessages().getFirst().getArguments()).containsExactly("reportId");
        });
    verify(reportService, never()).getReportDefinitionById(anyLong());
  }

  @Test
  void aFailedExecutionIsPassedOnUnchangedForTheAdviceToMap() {
    authenticated();
    when(reportService.getReportDefinitionById(42L))
        .thenReturn(definition("Stunden", "select 1 from t where jahr = :jahr"));
    var failure = new InvalidDataException(RP_REPORT_PARAMETERS_MISSING, "jahr");
    when(reportService.executeChecked(any(), any())).thenThrow(failure);

    assertThatThrownBy(() -> endpoint.execute("42", null, Map.of("reportId", "42"), null)).isSameAs(failure);
  }

  @Test
  void aReportIsFoundByItsIdAlone() {
    authenticated();
    when(reportService.getReportDefinitionById(42L)).thenReturn(definition("Stunden", "select 1"));
    when(reportService.executeChecked(any(), any())).thenReturn(result("a", 1));

    var response = endpoint.execute("42", null, Map.of("reportId", "42"), "application/json");

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().report()).isEqualTo("Stunden");
  }

  @Test
  void withAnIdANameAlongsideIsIgnored() {
    authenticated();
    when(reportService.getReportDefinitionById(42L)).thenReturn(definition("Stunden", "select 1"));
    when(reportService.executeChecked(any(), any())).thenReturn(result("a", 1));

    var response = endpoint.execute("42", "Alt", Map.of("reportId", "42", "report", "Alt"), "application/json");

    assertThat(response.getBody()).isNotNull();
  }

  @Test
  void anEmptyIdCountsAsNotGiven() {
    authenticated();

    assertThatThrownBy(() -> endpoint.execute(" ", null, Map.of("reportId", " "), "application/json"))
        .isInstanceOfSatisfying(InvalidDataException.class,
            e -> assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(RP_REPORT_NOT_SPECIFIED));
    verify(reportService, never()).getReportDefinitionById(anyLong());
  }

  @Test
  void withoutAnIdTheParameterIsNamed() {
    authenticated();

    assertThatThrownBy(() -> endpoint.execute(null, null, Map.of(), null))
        .isInstanceOfSatisfying(InvalidDataException.class, e -> {
          assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(RP_REPORT_NOT_SPECIFIED);
          assertThat(e.getMessages().getFirst().getArguments()).containsExactly("reportId");
        });
  }

  @Test
  void anIdThatIsNotANumberIsRejectedBeforeAnyLookup() {
    authenticated();

    assertThatThrownBy(() -> endpoint.execute("abc", "Stunden", Map.of("reportId", "abc", "report", "Stunden"), null))
        .isInstanceOfSatisfying(InvalidDataException.class,
            e -> assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(RP_REPORT_ID_INVALID));

    verify(reportService, never()).getReportDefinitionById(anyLong());
  }

  @Test
  void aFailedLookupByIdIsPassedOnUnchangedForTheAdviceToMap() {
    authenticated();
    ErrorCodeException unknown = new InvalidDataException(RP_REPORT_ID_NOT_FOUND, "7");
    ErrorCodeException forbidden = new AuthorizationException(AA_NOT_ATHORIZED);
    when(reportService.getReportDefinitionById(7L)).thenThrow(unknown);
    when(reportService.getReportDefinitionById(8L)).thenThrow(forbidden);

    assertThatThrownBy(() -> endpoint.execute("7", null, Map.of("reportId", "7"), null)).isSameAs(unknown);
    assertThatThrownBy(() -> endpoint.execute("8", null, Map.of("reportId", "8"), null)).isSameAs(forbidden);
  }

  @Test
  void theReportIdIsNotPassedOnAsAReportParameter() {
    authenticated();
    when(reportService.getReportDefinitionById(42L))
        .thenReturn(definition("Stunden", "select 1 from t where r = :reportId"));
    when(reportService.executeChecked(any(), parametersCaptor.capture())).thenReturn(result("a", 1));

    endpoint.execute("42", "Stunden", Map.of("reportId", "42", "report", "Stunden"), "application/json");

    assertThat(parametersCaptor.getValue()).isEmpty();
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
