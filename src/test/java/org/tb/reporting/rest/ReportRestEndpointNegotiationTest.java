package org.tb.reporting.rest;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.tb.common.exception.ErrorCode.RP_REPORT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.RP_REPORT_PARAMETERS_MISSING;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.test.FixedClock;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.domain.ReportResult;
import org.tb.reporting.domain.ReportResultColumnHeader;
import org.tb.reporting.domain.ReportResultColumnValue;
import org.tb.reporting.domain.ReportResultRow;
import org.tb.reporting.service.ReportService;

/**
 * Die Formatwahl hängt am {@code Accept}-Header und damit an der Aushandlung von Spring MVC, nicht an
 * einer Verzweigung im Endpunkt. Diese Prüfung geht deshalb über die Web-Ebene: ein reiner Unit-Test
 * erreicht die Stelle nicht, an der die Zusage des Tickets eingelöst wird.
 *
 * <p>Die Konverter stehen in derselben Reihenfolge wie im laufenden Programm — eigene vor den
 * mitgelieferten, so setzt Spring Boot sie zusammen. Dass JSON trotzdem gewinnt, wenn der Aufrufer
 * keinen Typ nennt, entscheidet allein die Reihenfolge in {@code produces}.
 */
@FixedClock
class ReportRestEndpointNegotiationTest {

  private final ReportService reportService = mock(ReportService.class);
  private final AuthorizedUser authorizedUser = mock(AuthorizedUser.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(reportService.getReportDefinitionByName("Stunden")).thenReturn(definition());
    when(reportService.executeChecked(any(), any())).thenReturn(result());

    mockMvc = MockMvcBuilders
        .standaloneSetup(new ReportRestEndpoint(reportService, authorizedUser))
        .setMessageConverters(new ReportDataCsvConverter(), new JacksonJsonHttpMessageConverter())
        .setControllerAdvice(new ReportRestExceptionHandler())
        .build();
  }

  @Test
  void jsonIsTheAnswerToAJsonRequest() throws Exception {
    mockMvc.perform(get("/api/reports/execute").param("report", "Stunden").header(ACCEPT, "application/json"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(content().json("""
            {"report":"Stunden","columns":["auftrag","stunden"],"rows":[["111",7],["222",3]]}
            """));
  }

  @Test
  void csvIsTheAnswerToACsvRequest() throws Exception {
    mockMvc.perform(get("/api/reports/execute").param("report", "Stunden").header(ACCEPT, "text/csv"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(content().string("""
            "auftrag","stunden"
            "111","7"
            "222","3"
            """))
        .andExpect(header().string(CONTENT_DISPOSITION, endsWith(".csv")));
  }

  @Test
  void aCallerWithoutAPreferenceGetsJson() throws Exception {
    mockMvc.perform(get("/api/reports/execute").param("report", "Stunden").header(ACCEPT, "*/*"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(header().doesNotExist(CONTENT_DISPOSITION));
  }

  @Test
  void theSecondPathServesTheSameEndpoint() throws Exception {
    mockMvc.perform(get("/rest/reports/execute").param("report", "Stunden").header(ACCEPT, "text/csv"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"));
  }

  @Test
  void anUnsupportedFormatIsNotAcceptable() throws Exception {
    mockMvc.perform(get("/api/reports/execute").param("report", "Stunden").header(ACCEPT, "application/xml"))
        .andExpect(status().isNotAcceptable());
  }

  @Test
  void anErrorIsAProblemDocumentAndNotTheHtmlErrorPage() throws Exception {
    when(reportService.getReportDefinitionByName("Fehlt"))
        .thenThrow(new InvalidDataException(RP_REPORT_NOT_FOUND, "Fehlt"));

    mockMvc.perform(get("/api/reports/execute").param("report", "Fehlt").header(ACCEPT, "application/json"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.title").value("RP-0001"))
        .andExpect(jsonPath("$.arguments[0]").value("Fehlt"));
  }

  @Test
  void aMissingParameterIsNamedInTheAnswer() throws Exception {
    when(reportService.getReportDefinitionByName("Stunden")).thenReturn(definition());
    when(reportService.executeChecked(any(), any()))
        .thenThrow(new InvalidDataException(RP_REPORT_PARAMETERS_MISSING, "jahr, monat"));

    mockMvc.perform(get("/api/reports/execute").param("report", "Stunden").header(ACCEPT, "application/json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("RP-0003"))
        .andExpect(jsonPath("$.arguments[0]").value("jahr, monat"));
  }

  @Test
  void aCsvCallerGetsTheErrorToo() throws Exception {
    // without an explicit content type on the problem document this would be a 406
    when(reportService.getReportDefinitionByName("Fehlt"))
        .thenThrow(new InvalidDataException(RP_REPORT_NOT_FOUND, "Fehlt"));

    mockMvc.perform(get("/api/reports/execute").param("report", "Fehlt").header(ACCEPT, "text/csv"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  void withoutAReportNameTheRequestIsIncomplete() throws Exception {
    mockMvc.perform(get("/api/reports/execute").header(ACCEPT, "application/json"))
        .andExpect(status().isBadRequest());
  }

  private static ReportDefinition definition() {
    var reportDefinition = new ReportDefinition();
    reportDefinition.setName("Stunden");
    reportDefinition.setSql("select auftrag, stunden from t");
    return reportDefinition;
  }

  private static ReportResult result() {
    return ReportResult.builder()
        .parameters(List.of())
        .columnHeaders(List.of(new ReportResultColumnHeader("auftrag"), new ReportResultColumnHeader("stunden")))
        .row(row("111", 7))
        .row(row("222", 3))
        .build();
  }

  private static ReportResultRow row(String auftrag, int stunden) {
    var row = new ReportResultRow();
    row.getColumnValues().put("auftrag", new ReportResultColumnValue(auftrag));
    row.getColumnValues().put("stunden", new ReportResultColumnValue(stunden));
    return row;
  }

}
