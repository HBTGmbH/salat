package de.hbt.salat.dailyreport.rest;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static de.hbt.salat.dailyreport.rest.DailyWorkingReportCsvConverter.TEXT_CSV_DAILY_WORKING_REPORT;
import static de.hbt.salat.dailyreport.rest.DailyWorkingReportCsvConverter.TEXT_CSV_DAILY_WORKING_REPORT_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType;
import de.hbt.salat.dailyreport.service.DailyWorkingReportService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeecontractService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/daily-working-reports", "/rest/daily-working-reports" })
@Tag(name = "daily report", description = "API zum Verwalten von täglichen Zeiterfassungen und Zeitbuchungen")
public class DailyWorkingReportRestEndpoint {

    private final EmployeecontractService employeecontractService;
    private final TimereportService timereportService;
    private final WorkingdayService workingdayService;
    private final DailyWorkingReportService dailyWorkingReportService;
    private final AuthorizedUser authorizedUser;
    private final AuthorizedEmployee authorizedEmployee;

    @GetMapping(path = "/list", produces = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_WORKING_REPORT_VALUE})
    @ResponseStatus(OK)
    @Operation(
        summary = "Liefert tägliche Zeiterfassungen für einen bestimmten Zeitraum",
        description = "Gibt die täglichen Zeiterfassungen für den authentifizierten Benutzer für einen spezifizierten Zeitraum zurück. Kann als JSON oder CSV geliefert werden. "
            + "Für jeden Tag gilt der an diesem Tag gültige Mitarbeitervertrag, der Zeitraum darf also über einen Vertragswechsel reichen. "
            + "Tage ohne gültigen Vertrag tragen nichts bei."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Erfolgreiche Abfrage",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = DailyWorkingReportData.class)
            )
        ),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
        @ApiResponse(responseCode = "404", description = "An keinem Tag des Zeitraums gilt ein Mitarbeitervertrag; die Antwort nennt den Zeitraum")
    })
    public ResponseEntity<List<DailyWorkingReportData>> getReports(
            @Parameter(description = "Referenzdatum für den Beginn des Berichtszeitraums", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refDate,

            @Parameter(description = "Anzahl der Tage beginnend beim Referenzdatum, für die tägliche Zeiterfassungen abgerufen werden sollen", example = "7")
            @RequestParam(defaultValue = "1") int days,

            @Parameter(description = "Bei 'true' wird die Antwort als CSV-Datei zurückgegeben")
            @RequestParam(defaultValue = "false") boolean csv
    ) {
        checkAuthenticated();
        if (refDate == null) refDate = DateUtils.today();
        var contractIdsByDay = EmployeecontractsByDay.resolve(refDate, days,
                day -> employeecontractService.getEmployeeContractValidAt(authorizedEmployee.getEmployeeId(), day));
        var response = ResponseEntity.ok();
        if (csv) {
            var filename = String.format("%s-%sd.csv", DateUtils.format(refDate), days);
            response = response.header(CONTENT_DISPOSITION, "attachment; filename=" + filename);
            response = response.contentType(TEXT_CSV_DAILY_WORKING_REPORT);
        }
        return response.body(getReports(contractIdsByDay));
    }

    private List<DailyWorkingReportData> getReports(Map<LocalDate, Long> contractIdsByDay) {
        return contractIdsByDay.entrySet().stream()
                .map(entry -> getReport(entry.getValue(), entry.getKey()))
                .filter(Objects::nonNull)
                .toList();
    }

    private DailyWorkingReportData getReport(Long employeeContractId, LocalDate date) {
        var workingDay = workingdayService.getWorkingday(employeeContractId, date);

        var timeReports = timereportService.getTimereportsByDateAndEmployeeContractId(employeeContractId, date)
                .stream()
                .map(DailyReportData::valueOf)
                .toList();

        if(workingDay == null && timeReports.isEmpty()){
            return null;
        }

        var builder = DailyWorkingReportData.builder().date(date).dailyReports(timeReports);

        if(workingDay != null) {
            builder.type(workingDay.getType());
            if(workingDay.getType() != WorkingDayType.NOT_WORKED) {
                var bd = workingDay.getBreakLength();
                builder.startTime(workingDay.getStartOfWorkingDay().toLocalTime());
                builder.breakDuration(LocalTime.of(bd.toHoursPart(), bd.toMinutesPart()));
            }
        }

        return builder.build();
    }

    @PostMapping(path = "/", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_WORKING_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Erstellt eine neue tägliche Zeiterfassung",
        description = "Erstellt eine neue tägliche Zeiterfassung für den authentifizierten Benutzer. Kann als JSON oder CSV übermittelt werden."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Erfolgreich erstellt"),
        @ApiResponse(responseCode = "400", description = "Ungültige Daten oder Geschäftsregelverstoß"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
        @ApiResponse(responseCode = "403", description = "Keine Berechtigung für diesen Vorgang")
    })
    public void createReport(
            @Parameter(description = "Die zu erstellende tägliche Zeiterfassung", required = true)
            @RequestBody DailyWorkingReportData report
    ) {
        checkAuthenticated();
        try {
            dailyWorkingReportService.createReports(List.of(report));
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(FORBIDDEN, "Could not create timereport. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    @PutMapping(path = "/", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_WORKING_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation(
        summary = "Aktualisiert eine bestehende tägliche Zeiterfassung",
        description = "Ersetzt eine bestehende tägliche Zeiterfassung für den angegebenen Tag mit den neuen Daten. Der Bericht kann als JSON oder CSV übermittelt werden."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Erfolgreich aktualisiert"),
        @ApiResponse(responseCode = "400", description = "Ungültige Daten oder Geschäftsregelverstoß"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
        @ApiResponse(responseCode = "403", description = "Keine Berechtigung für diesen Vorgang")
    })
    public void replaceReport(
            @Parameter(description = "Die aktualisierte tägliche Zeiterfassung", required = true)
            @RequestBody DailyWorkingReportData report
    ) {
        checkAuthenticated();
        try {
            dailyWorkingReportService.updateReports(List.of(report));
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(FORBIDDEN, "Could not create timereport. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    @PostMapping(path = "/list", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_WORKING_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation(
        summary = "Erstellt mehrere tägliche Zeiterfassungen",
        description = "Erstellt mehrere tägliche Zeiterfassungen in einem Batch für den authentifizierten Benutzer. Die Berichte können als JSON oder CSV übermittelt werden."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Erfolgreich erstellt"),
        @ApiResponse(responseCode = "400", description = "Ungültige Daten oder Geschäftsregelverstoß"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
        @ApiResponse(responseCode = "403", description = "Keine Berechtigung für diesen Vorgang")
    })
    public void createReports(
            @Parameter(description = "Liste der zu erstellenden täglichen Zeiterfassungen", required = true)
            @RequestBody List<DailyWorkingReportData> reports
    ) {
        checkAuthenticated();
        try {
            dailyWorkingReportService.createReports(reports);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(FORBIDDEN, "Could not create timereport. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    @PutMapping(path = "/list", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_WORKING_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation(
        summary = "Aktualisiert mehrere tägliche Zeiterfassungen",
        description = "Ersetzt mehrere bestehende tägliche Zeiterfassungen in einem Batch für den authentifizierten Benutzer. Die Berichte können als JSON oder CSV übermittelt werden."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Erfolgreich aktualisiert"),
        @ApiResponse(responseCode = "400", description = "Ungültige Daten oder Geschäftsregelverstoß"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert"),
        @ApiResponse(responseCode = "403", description = "Keine Berechtigung für diesen Vorgang")
    })
    public void replaceReports(
            @Parameter(description = "Liste der aktualisierten täglichen Zeiterfassungen", required = true)
            @RequestBody List<DailyWorkingReportData> reports
    ) {
        checkAuthenticated();
        try {
            dailyWorkingReportService.updateReports(reports);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(FORBIDDEN, "Could not create timereport. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    private void checkAuthenticated() {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
    }
}
