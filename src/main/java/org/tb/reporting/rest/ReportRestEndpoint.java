package org.tb.reporting.rest;

import static java.util.Comparator.comparingDouble;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.tb.common.exception.ErrorCode.AA_REQUIRED;
import static org.tb.common.exception.ErrorCode.RP_REPORT_ID_INVALID;
import static org.tb.common.exception.ErrorCode.RP_REPORT_NOT_SPECIFIED;
import static org.tb.reporting.rest.ReportDataCsvConverter.TEXT_CSV;
import static org.tb.reporting.rest.ReportDataCsvConverter.TEXT_CSV_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.InvalidDataException;
import org.tb.reporting.domain.ReportDefinition;
import org.tb.reporting.service.ReportFileNames;
import org.tb.reporting.service.ReportParameters;
import org.tb.reporting.service.ReportService;

/**
 * Abzug von Reportergebnissen für maschinelle Aufrufer (#1035). Das Format wählt der {@code Accept}-Header:
 * {@code application/json} oder {@code text/csv}.
 *
 * <p>Der Report wird über seine Id oder über seinen Namen angesprochen; beides steht in der
 * Reportübersicht. Die Id ist eindeutig und übersteht eine Umbenennung (#1166). Der Name ist nicht
 * eindeutig; passt er auf mehrere Reports, wird keiner ausgeführt. Sind beide angegeben, gilt die Id,
 * und der Name wird nicht ausgewertet — sonst scheiterte ein Aufruf mit Id an einem Namen, der
 * inzwischen mehrdeutig ist.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/reports", "/rest/reports" })
@Tag(name = "report", description = "API zum Ausführen von Reports und zum Abzug der Ergebnisse")
public class ReportRestEndpoint {

  /**
   * Name und Id des Reports stehen in eigenen Anfrageparametern. Ein Report, dessen SQL einen
   * Parameter {@code :report} oder {@code :reportId} nennt, kann darum über diese API nicht
   * ausgeführt werden — sein Parameter bliebe unbesetzt und die Antwort sagt das (400). Für die Id
   * ist deshalb nicht das naheliegende {@code id} reserviert, das in Report-SQL leicht vorkommt.
   */
  static final String REPORT_PARAMETER = "report";
  static final String REPORT_ID_PARAMETER = "reportId";

  private final ReportService reportService;
  private final AuthorizedUser authorizedUser;

  @GetMapping(path = "/execute", produces = { APPLICATION_JSON_VALUE, TEXT_CSV_VALUE })
  @ResponseStatus(OK)
  @Operation(summary = "Führt einen Report aus und liefert das Ergebnis",
      description = """
          Führt den angegebenen Report aus. Das Format bestimmt der Accept-Header:
          `application/json` (Standard) oder `text/csv`.

          Der Report wird über `reportId` oder über `report` (seinen Namen) angegeben; beides steht in
          der Reportübersicht. Einer der beiden Parameter muss angegeben sein. Sind beide angegeben,
          gilt `reportId`, und `report` wird nicht ausgewertet — auch dann nicht, wenn der Name auf
          keinen oder auf mehrere Reports passt. Die Id ist eindeutig und bleibt bei einer
          Umbenennung gleich; ein Name, der auf mehrere Reports passt, führt keinen aus.

          Die Parameter des Reports werden als zusätzliche Anfrageparameter übergeben, benannt wie im
          SQL des Reports (`:jahr` wird `jahr=2025`). Ein Wert kann seinen Typ als Präfix tragen —
          `von=date,2025-09-01`; ohne Präfix gilt `string`. Die Parameternamen `reportId` und
          `report` sind für die Angabe des Reports reserviert.

          Für den heutigen Tag stehen Schlüsselwörter bereit, gerechnet in der Zeitzone der Anwendung:
          bei `date` etwa `HEUTE`, `GESTERN`, `BOM`, `VORMONAT`, `VORMONATSENDE`
          (`von=date,VORMONAT`), bei `number` etwa `KW`, `MONAT`, `JAHR` (`kw=number,KW`).
          Groß- und Kleinschreibung zählt; die englischen Namen (`TODAY`, `LAST_MONTH`, `WEEKNUM`, …)
          gelten ebenso.

          Das Ergebnis hängt am aufrufenden Benutzer: ein Report darf sein Kürzel und das heutige
          Datum verwenden, und es werden nur Reports ausgeführt, für die der Aufrufer die Berechtigung
          EXECUTE hat. Die Ergebnismenge ist nicht begrenzt.
          """,
      responses = {
          @ApiResponse(responseCode = "200", description = "Das Ergebnis des Reports",
              content = {
                  @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ReportData.class)),
                  @Content(mediaType = TEXT_CSV_VALUE, schema = @Schema(type = "string"))
              }),
          @ApiResponse(responseCode = "400", description = "Weder `reportId` noch `report` angegeben, `reportId` ist keine Zahl, oder ein Parameter des Reports fehlt oder ist unbrauchbar"),
          @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
          @ApiResponse(responseCode = "403", description = "Keine Berechtigung, diesen Report auszuführen"),
          @ApiResponse(responseCode = "404", description = "Kein Report mit dieser Id bzw. mit diesem Namen"),
          @ApiResponse(responseCode = "409", description = "Nur `report` angegeben, und der Name passt auf mehrere Reports — es wird keiner ausgeführt"),
          @ApiResponse(responseCode = "500", description = "Das SQL des Reports konnte nicht ausgeführt werden")
      })
  public ResponseEntity<ReportData> execute(
      @RequestParam(name = REPORT_ID_PARAMETER, required = false)
      @Parameter(description = "Id des Reports, wie sie in der Reportübersicht steht; hat Vorrang vor `report`",
          schema = @Schema(type = "integer", format = "int64"), example = "42")
      String reportId,

      @RequestParam(name = REPORT_PARAMETER, required = false)
      @Parameter(description = "Name des Reports, wie er in der Reportübersicht steht; wird nur ausgewertet, wenn `reportId` fehlt",
          example = "Stunden pro Auftrag")
      String report,

      @RequestParam
      @Parameter(hidden = true)
      Map<String, String> allParameters,

      @RequestHeader(name = ACCEPT, required = false)
      @Parameter(hidden = true)
      String acceptHeader
  ) {
    checkAuthenticated();

    var reportDefinition = reportDefinitionOf(reportId, report);
    var parameters = ReportParameters.nonEmpty(
        ReportParameters.fromRequest(reportParametersOf(allParameters), reportDefinition.getSql())
    );
    var reportResult = reportService.executeChecked(reportDefinition, parameters);

    var response = ResponseEntity.ok();
    if (wantsCsv(acceptHeader)) {
      var fileName = ReportFileNames.create(reportDefinition, reportResult.getParameters(), "csv");
      response = response.header(CONTENT_DISPOSITION, "attachment; filename=" + fileName);
    }
    return response.body(ReportData.valueOf(reportDefinition, reportResult));
  }

  /**
   * Die Id entscheidet, wenn sie angegeben ist; der Name wird dann gar nicht erst nachgeschlagen.
   * Ein leerer Wert ({@code reportId=}) zählt als nicht angegeben, wie es ein Skript mit leerer
   * Variable schickt.
   *
   * <p>Die Id kommt als Text an und wird hier gelesen, nicht von Spring gebunden: ein Bindungsfehler
   * liefe am {@link ReportRestExceptionHandler} vorbei und käme als Fehlerseite statt als
   * Problemdokument.
   */
  private ReportDefinition reportDefinitionOf(String reportId, String report) {
    if (reportId != null && !reportId.isBlank()) {
      return reportService.getReportDefinitionById(parseReportId(reportId));
    }
    if (report != null && !report.isBlank()) {
      return reportService.getReportDefinitionByName(report);
    }
    throw new InvalidDataException(RP_REPORT_NOT_SPECIFIED, REPORT_ID_PARAMETER, REPORT_PARAMETER);
  }

  private static long parseReportId(String reportId) {
    try {
      return Long.parseLong(reportId.strip());
    } catch (NumberFormatException e) {
      throw new InvalidDataException(RP_REPORT_ID_INVALID, reportId);
    }
  }

  /**
   * Name und Id des Reports dürfen nicht als Parameter des Reports durchgehen: sonst bekäme ein
   * Report mit einem Parameter {@code :report} den Reportnamen als Wert eingesetzt, ohne dass es
   * jemand merkt.
   *
   * <p>Der gemerkte Zustand der Oberfläche ist hier kein Thema — {@code UiStateFilter} lässt die
   * REST-Pfade aus, also enthält die Anfrage nur, was der Aufrufer geschickt hat.
   */
  private static Map<String, String> reportParametersOf(Map<String, String> allParameters) {
    var parameters = new HashMap<>(allParameters);
    parameters.remove(REPORT_PARAMETER);
    parameters.remove(REPORT_ID_PARAMETER);
    return parameters;
  }

  /**
   * Ob der Aufrufer CSV verlangt — nur dann trägt die Antwort einen Dateinamen. Maßgeblich ist der
   * erste konkret genannte Typ; einen Platzhalter beantwortet die Aushandlung mit JSON, weil die
   * Methode JSON zuerst anbietet.
   */
  static boolean wantsCsv(String acceptHeader) {
    if (acceptHeader == null || acceptHeader.isBlank()) {
      return false;
    }
    try {
      var acceptedTypes = new ArrayList<>(MediaType.parseMediaTypes(acceptHeader));
      // stabile Sortierung: bei gleichem q bleibt die Reihenfolge der Anfrage, wie die Aushandlung sie auch liest
      acceptedTypes.sort(comparingDouble(MediaType::getQualityValue).reversed());
      return acceptedTypes.stream()
          .filter(mediaType -> !mediaType.isWildcardType() && !mediaType.isWildcardSubtype())
          .findFirst()
          .filter(TEXT_CSV::isCompatibleWith)
          .isPresent();
    } catch (InvalidMediaTypeException e) {
      // Über den Header entscheidet danach die Aushandlung selbst und lehnt ihn gegebenenfalls ab.
      return false;
    }
  }

  private void checkAuthenticated() {
    if (!authorizedUser.isAuthenticated()) {
      throw new AuthorizationException(AA_REQUIRED);
    }
  }

}
