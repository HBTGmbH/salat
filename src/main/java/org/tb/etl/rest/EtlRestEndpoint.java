package org.tb.etl.rest;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.websocket.server.PathParam;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.etl.service.ETLService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/etl", "/rest/etl" })
@Tag(name = "etl", description = "API zum Ausführen von ETL Definitionen")
public class EtlRestEndpoint {

  private final ETLService etlService;
  private final AuthorizedUser authorizedUser;

  @GetMapping(path = "/execute-all", produces = APPLICATION_JSON_VALUE)
  @ResponseStatus(OK)
  @Operation(summary = "Führt alle ETL-Definitionen aus",
      description = "Führt alle ETL-Definitionen für den gegebenen Zeitraum aus",
      responses = {
          @ApiResponse(responseCode = "200", description = "Die ETL-Definitionen wurden ausgeführt."),
          @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
          @ApiResponse(responseCode = "403", description = "Keine Berechtigung für die Ausführung von ETL-Definitionen")
      })
  public void execute(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      @Parameter(description = "Zeitraum für ETL-Definition, Anfang", example = "2025-09-01")
      LocalDate fromDate,

      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      @Parameter(description = "Zeitraum für ETL-Definition, Ende", example = "2025-09-30")
      LocalDate untilDate
  ) {
    checkAuthenticated();

    try {
      etlService.executeAll(new LocalDateRange(fromDate, untilDate));
    } catch (AuthorizationException e) {
      throw new ResponseStatusException(FORBIDDEN, "Could not execute all ETL definitions. " + e);
    } catch (InvalidDataException | BusinessRuleException e) {
      throw new ResponseStatusException(BAD_REQUEST, "Could not execute all ETL definitions. " + e);
    }
  }

  @GetMapping(path = "/{etl-name}/execute", produces = APPLICATION_JSON_VALUE)
  @ResponseStatus(OK)
  @Operation(summary = "Führt eine ETL-Definitionen aus",
      description = "Führt eine bestimmte ETL-Definitionen für den gegebenen Zeitraum aus",
      responses = {
          @ApiResponse(responseCode = "200", description = "Die ETL-Definition wurde ausgeführt."),
          @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
          @ApiResponse(responseCode = "403", description = "Keine Berechtigung für die Ausführung von ETL-Definitionen"),
          @ApiResponse(responseCode = "404", description = "Die ETL-Definition wurde nicht gefunden")
      })
  public void executeEtl(
      @PathVariable(name = "etl-name")
      @Parameter(description = "Name der ETL-Definition", example = "worked-hours-daily-by-emp")
      String etlName,

      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      @Parameter(description = "Zeitraum für ETL-Definition, Anfang", example = "2025-09-01")
      LocalDate fromDate,

      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      @Parameter(description = "Zeitraum für ETL-Definition, Ende", example = "2025-09-30")
      LocalDate untilDate
  ) {
    checkAuthenticated();

    try {
      if(!etlService.isETLExisting(etlName)) {
        throw new ResponseStatusException(NOT_FOUND, "ETL not found: " + etlName);
      }
      etlService.execute(new LocalDateRange(fromDate, untilDate), Set.of(etlName));
    } catch (AuthorizationException e) {
      throw new ResponseStatusException(FORBIDDEN, "Could not execute ETL definition. " + e);
    } catch (InvalidDataException | BusinessRuleException e) {
      throw new ResponseStatusException(BAD_REQUEST, "Could not execute ETL definition. " + e);
    }
  }

  private void checkAuthenticated() {
    if (!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }
  }

}
