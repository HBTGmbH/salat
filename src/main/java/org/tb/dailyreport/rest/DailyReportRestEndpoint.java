package org.tb.dailyreport.rest;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.tb.dailyreport.rest.DailyReportCsvConverter.TEXT_CSV_DAILY_REPORT;
import static org.tb.dailyreport.rest.DailyReportCsvConverter.TEXT_CSV_DAILY_REPORT_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/daily-reports", "/rest/daily-reports" })
@Tag(name = "daily report", description = "API zum Verwalten von täglichen Zeiterfassungen und Zeitbuchungen")
public class DailyReportRestEndpoint {

    private final EmployeecontractService employeecontractService;
    private final EmployeeorderService employeeorderService;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;

    @GetMapping(path = "/list", produces = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_REPORT_VALUE})
    @ResponseStatus(OK)
    @Operation(summary = "Liefert Zeitbuchungen für den aktuellen Benutzer",
              description = "Ruft Zeitbuchungen für den angemeldeten Benutzer in einem angegebenen Zeitraum ab. Der Benutzer muss authentifiziert sein.",
              responses = {
                  @ApiResponse(responseCode = "200", description = "Erfolgreiche Abfrage der Zeitbuchungen",
                      content = @Content(array = @ArraySchema(schema = @Schema(implementation = DailyReportData.class)))),
                  @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
                  @ApiResponse(responseCode = "404", description = "Kein gültiger Mitarbeitervertrag zum Referenzdatum gefunden")
              })
    public ResponseEntity<List<DailyReportData>> getBookings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            @Parameter(description = "Referenzdatum für die Abfrage", example = "2025-09-09")
            LocalDate refDate,

            @RequestParam(defaultValue = "1") 
            @Parameter(description = "Anzahl der Tage, die abgefragt werden sollen", example = "5")
            int days,

            @RequestParam(defaultValue = "false") 
            @Parameter(description = "Gibt an, ob die Daten im CSV-Format zurückgegeben werden sollen", example = "false") 
            boolean csv
    ) {
        checkAuthenticated();
        if (refDate == null) refDate = DateUtils.today();
        var employeecontract = employeecontractService.getEmployeeContractValidAt(authorizedUser.getEmployeeId(), refDate);
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        var response = ResponseEntity.ok();
        if (csv) {
            response = response.contentType(TEXT_CSV_DAILY_REPORT);
        }
        return response.body(getDailyReports(employeecontract.getId(), refDate, days));
    }

    @GetMapping(path = "/{employeeContractId}/list", produces = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_REPORT_VALUE})
    @ResponseStatus(OK)
    @Operation(summary = "Liefert Zeitbuchungen für einen bestimmten Mitarbeitervertrag", 
              description = "Ruft Zeitbuchungen für einen spezifischen Mitarbeitervertrag in einem angegebenen Zeitraum ab. Der Benutzer muss authentifiziert sein und Zugriffsrechte auf die Zeitbuchungen haben.",
              responses = {
                  @ApiResponse(responseCode = "200", description = "Erfolgreiche Abfrage der Zeitbuchungen",
                      content = @Content(array = @ArraySchema(schema = @Schema(implementation = DailyReportData.class)))),
                  @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
                  @ApiResponse(responseCode = "404", description = "Mitarbeitervertrag nicht gefunden")
              })
    public ResponseEntity<List<DailyReportData>> getBookingsForEmployee(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
            @Parameter(description = "Referenzdatum für die Abfrage", example = "2025-09-09") 
            LocalDate refDate,

            @RequestParam(defaultValue = "1") 
            @Parameter(description = "Anzahl der Tage, die abgefragt werden sollen", example = "5")
            int days,

            @PathVariable 
            @Parameter(description = "ID des angeforderten Mitarbeitervertrags", example = "42")
            Long employeeContractId,

            @RequestParam(defaultValue = "false") 
            @Parameter(description = "Gibt an, ob die Daten im CSV-Format zurückgegeben werden sollen", example = "false") 
            boolean csv
    ) {
        checkAuthenticated();
        if (refDate == null) refDate = DateUtils.today();
        var employeecontract = employeecontractService.getEmployeecontractById(employeeContractId);
        if (employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        var response = ResponseEntity.ok();
        if (csv) {
            response = response.contentType(TEXT_CSV_DAILY_REPORT);
        }
        return response.body(getDailyReports(employeecontract.getId(), refDate, days));
    }

    @PostMapping(path = "/", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation(summary = "Erstellt eine neue Zeitbuchung", 
              description = "Erstellt eine einzelne neue Zeitbuchung. Der Benutzer muss authentifiziert sein und Zugriffsrechte für den zugehörigen Mitarbeiterauftrag haben.",
              responses = {
                  @ApiResponse(responseCode = "201", description = "Zeitbuchung erfolgreich erstellt"),
                  @ApiResponse(responseCode = "400", description = "Ungültige Daten oder Geschäftsregel verletzt"),
                  @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
                  @ApiResponse(responseCode = "403", description = "Keine Berechtigung für den angegebenen Mitarbeiterauftrag"),
                  @ApiResponse(responseCode = "404", description = "Mitarbeiterauftrag nicht gefunden")
              })
    public void createBooking(
            @RequestBody 
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Die zu erstellende Zeitbuchung",
                required = true, 
                content = @Content(schema = @Schema(implementation = DailyReportData.class))) 
            DailyReportData booking
    ) {
        checkAuthenticated();
        try {
            createDailyReport(booking);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(FORBIDDEN, "Could not create timereport. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    @PostMapping(path = "/list", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation(summary = "Erstellt mehrere neue Zeitbuchungen", 
              description = "Erstellt mehrere neue Zeitbuchungen in einem Stapel. Der Benutzer muss authentifiziert sein und Zugriffsrechte für die zugehörigen Mitarbeiteraufträge haben.",
              responses = {
                  @ApiResponse(responseCode = "201", description = "Zeitbuchungen erfolgreich erstellt"),
                  @ApiResponse(responseCode = "400", description = "Ungültige Daten oder Geschäftsregel verletzt"),
                  @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
                  @ApiResponse(responseCode = "403", description = "Keine Berechtigung für einen der angegebenen Mitarbeiteraufträge"),
                  @ApiResponse(responseCode = "404", description = "Mitarbeiterauftrag nicht gefunden")
              })
    public void createBookings(
            @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Liste der zu erstellenden Zeitbuchungen",
                required = true, 
                content = @Content(array = @ArraySchema(schema = @Schema(implementation = DailyReportData.class)))) 
            List<DailyReportData> bookings
    ) {
        checkAuthenticated();
        try {
            bookings.forEach(this::createDailyReport);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(FORBIDDEN, "Could not create timereports. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    @PutMapping(path = "/list", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation(summary = "Aktualisiert mehrere Zeitbuchungen", 
              description = "Aktualisiert mehrere Zeitbuchungen, gruppiert nach Datum und Mitarbeiterauftrag. Bestehende Buchungen für die gleiche Kombination aus Datum und Mitarbeiterauftrag werden gelöscht und durch die neuen ersetzt. Der Benutzer muss authentifiziert sein und Zugriffsrechte für die zugehörigen Mitarbeiteraufträge haben.",
              responses = {
                  @ApiResponse(responseCode = "201", description = "Zeitbuchungen erfolgreich aktualisiert"),
                  @ApiResponse(responseCode = "400", description = "Ungültige Daten oder Geschäftsregel verletzt"),
                  @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
                  @ApiResponse(responseCode = "403", description = "Keine Berechtigung für einen der angegebenen Mitarbeiteraufträge"),
                  @ApiResponse(responseCode = "404", description = "Mitarbeiterauftrag nicht gefunden")
              })
    public void updateBookings(
            @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Liste der zu aktualisierenden Zeitbuchungen",
                required = true, 
                content = @Content(array = @ArraySchema(schema = @Schema(implementation = DailyReportData.class)))) 
            List<DailyReportData> bookings
    ) {
        checkAuthenticated();
        try {
            bookings.stream().collect(groupingBy(DailyReportData::getDate))
                    .forEach((day, bookingsOfDay)-> bookingsOfDay.stream().collect(groupingBy(DailyReportData::getEmployeeorderId))
                            .forEach((employeeOrderId, bookingsOfOrder) ->
                                    replaceDailyReports(DateUtils.parse(day), employeeOrderId, bookingsOfOrder))
                    );
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(FORBIDDEN, "Could not create timereports. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    private void replaceDailyReports(LocalDate day, Long employeeOrderId, List<DailyReportData> bookings) {
        var employeeorder = employeeorderService.getEmployeeorderById(employeeOrderId);
        if (employeeorder == null) {
            throw new ResponseStatusException(NOT_FOUND, "Could not find employeeorder with id " + employeeOrderId);
        }
        timereportService.deleteTimeReports(day, employeeOrderId);
        bookings.forEach(booking -> doCreateDailyReport(booking, employeeorder));
    }

    private void createDailyReport(DailyReportData booking) throws AuthorizationException, InvalidDataException, BusinessRuleException {
        var employeeorder = employeeorderService.getEmployeeorderById(booking.getEmployeeorderId());
        if (employeeorder == null) {
            throw new ResponseStatusException(NOT_FOUND, "Could not find employeeorder with id " + booking.getEmployeeorderId());
        }
        doCreateDailyReport(booking, employeeorder);
    }

    private void doCreateDailyReport(DailyReportData booking, Employeeorder employeeorder) {
        timereportService.createTimereports(
                employeeorder.getEmployeecontract().getId(),
                employeeorder.getId(),
                DateUtils.parse(booking.getDate()),
                booking.getComment(),
                booking.isTraining(),
                booking.getHours(),
                booking.getMinutes(),
                1
        );
    }

    private List<DailyReportData> getDailyReports(Long employeeContractId, LocalDate startDay, int days) {
        return IntStream.range(0, days)
                .mapToObj(day -> DateUtils.addDays(startDay, day))
                .map(day -> timereportService.getTimereportsByDateAndEmployeeContractId(employeeContractId, day))
                .flatMap(List::stream)
                .map(DailyReportData::valueOf)
                .collect(toList());
    }

    @DeleteMapping(path = "/", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation(summary = "Löscht eine Zeitbuchung", 
              description = "Löscht eine bestehende Zeitbuchung anhand ihrer ID. Der Benutzer muss authentifiziert sein und Zugriffsrechte für diese Zeitbuchung haben.",
              responses = {
                  @ApiResponse(responseCode = "200", description = "Zeitbuchung erfolgreich gelöscht"),
                  @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
                  @ApiResponse(responseCode = "403", description = "Keine Berechtigung zum Löschen dieser Zeitbuchung"),
                  @ApiResponse(responseCode = "404", description = "Zeitbuchung nicht gefunden")
              })
    public void deleteBooking(
            @RequestBody 
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Die zu löschende Zeitbuchung (nur die ID wird benötigt)",
                required = true, 
                content = @Content(schema = @Schema(implementation = DailyReportData.class))) 
            DailyReportData report
    ) {
        checkAuthenticated();
        try {
            timereportService.deleteTimereportById(report.getId());
        } catch(AuthorizationException e) {
            throw new ResponseStatusException(FORBIDDEN);
        }
    }

    private void checkAuthenticated() {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
    }

}
