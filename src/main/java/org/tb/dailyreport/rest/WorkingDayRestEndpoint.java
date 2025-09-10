package org.tb.dailyreport.rest;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.tb.common.util.DateUtils.today;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/workingday", "/rest/workingday" })
@Tag(name = "working days", description = "API zur Verwaltung von Arbeitstagen der Mitarbeiter")
public class WorkingDayRestEndpoint {

    private final EmployeeService employeeService;
    private final EmployeecontractService employeecontractService;
    private final WorkingdayService workingdayService;
    private final AuthorizedUser authorizedUser;

    @PutMapping(path = "/{employeeSign}", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(CREATED)
    @Operation(
        summary = "Erstellt oder aktualisiert Arbeitstaginformationen zu einem Arbeitstag",
        description = "Speichert oder aktualisiert Arbeitstaginformationen zu einem Arbeitstag für einen bestimmten Mitarbeiter. Wenn kein Datum angegeben ist, wird der heutige Tag verwendet.",
        tags = {"working days"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201", 
            description = "Erfolgreich erstellt oder aktualisiert"
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Ungültige Daten oder Verletzung von Geschäftsregeln",
            content = @Content(mediaType = "application/json")
        ),
        @ApiResponse(
            responseCode = "401", 
            description = "Nicht authentifiziert",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Nicht berechtigt",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "Kein gültiger Mitarbeitervertrag gefunden",
            content = @Content
        )
    })
    public void upsert(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Daten zum Arbeitstag",
            required = true,
            content = @Content(schema = @Schema(implementation = WorkingDayData.class))
        )
        @RequestBody WorkingDayData data, 

        @Parameter(
            description = "Kürzel des Mitarbeiters",
            required = true,
            example = "tst"
        )
        @PathVariable String employeeSign) {
        if(!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        var date = ofNullable(data.getDate()).map(DateUtils::parse).orElseGet(DateUtils::today);
        var employee = employeeService.getEmployeeBySign(employeeSign);
        var employeecontract = employeecontractService.getEmployeeContractValidAt(employee.getId(), date);
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        var wd = ofNullable(workingdayService.getWorkingday(employeecontract.getId(), date)).orElseGet(Workingday::new);
        wd.setEmployeecontract(employeecontract);
        wd.setRefday(date);
        wd.setStarttimehour(data.getStarthour());
        wd.setStarttimeminute(data.getStartminute());
        wd.setBreakhours(data.getBreakhours());
        wd.setBreakminutes(data.getBreakminutes());
        if(data.getType() != null) {
            wd.setType(data.getType());
        }
        try {
            workingdayService.upsertWorkingday(wd);
        } catch(AuthorizationException e) {
            throw new ResponseStatusException(FORBIDDEN, e.toString());
        } catch(InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.toString());
        }
    }

    @GetMapping(path = "/{employeeSign}", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation(
        summary = "Gibt Arbeitstaginformationen zu heute zurück",
        description = "Liefert Arbeitstaginformationen des heutigen Tages für einen bestimmten Mitarbeiter",
        tags = {"working days"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Erfolgreiche Abfrage",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = WorkingDayData.class))
        ),
        @ApiResponse(
            responseCode = "401", 
            description = "Nicht authentifiziert",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "Kein gültiger Mitarbeitervertrag oder Arbeitstag gefunden",
            content = @Content
        )
    })
    public WorkingDayData getToday(
        @Parameter(
            description = "Kürzel des Mitarbeiters",
            required = true,
            example = "tst"
        )
        @PathVariable String employeeSign) {
        return doGet(today(), employeeSign);
    }

    @GetMapping(path = "/{employeeSign}/{date}", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation(
        summary = "Gibt Arbeitstaginformationen zu einem Arbeitstag für ein bestimmtes Datum zurück",
        description = "Liefert Arbeitstaginformationen zu einem Arbeitstag für einen bestimmten Mitarbeiter und ein spezifisches Datum",
        tags = {"working days"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Erfolgreiche Abfrage",
            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = WorkingDayData.class))
        ),
        @ApiResponse(
            responseCode = "401", 
            description = "Nicht authentifiziert",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "Kein gültiger Mitarbeitervertrag oder Arbeitstag gefunden",
            content = @Content
        )
    })
    public WorkingDayData get(
        @Parameter(
            description = "Kürzel des Mitarbeiters",
            required = true,
            example = "tst"
        )
        @PathVariable String employeeSign, 

        @Parameter(
            description = "Das Datum im Format yyyy-MM-dd",
            required = true,
            example = "2023-09-01"
        )
        @PathVariable String date) {
        return doGet(DateUtils.parse(date), employeeSign);
    }

    private WorkingDayData doGet(LocalDate date, String employeeSign){
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        var employee = employeeService.getEmployeeBySign(employeeSign);
        var employeecontract = employeecontractService.getEmployeeContractValidAt(employee.getId(), date);
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        var workingDay = workingdayService.getWorkingday(employeecontract.getId(), date);
        if(workingDay == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        return WorkingDayData.valueOf(workingDay);
    }

    @DeleteMapping("/{employeeSign}/{date}")
    @ResponseStatus(OK)
    @Operation(
        summary = "Löscht Arbeitstaginformationen zu einem Arbeitstag",
        description = "Löscht Arbeitstaginformationen zu einem Arbeitstag für einen bestimmten Mitarbeiter und ein spezifisches Datum",
        tags = {"working days"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Erfolgreich gelöscht"
        ),
        @ApiResponse(
            responseCode = "401", 
            description = "Nicht authentifiziert",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Nicht berechtigt",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "Kein gültiger Mitarbeitervertrag oder keine Arbeitstaginformationen gefunden",
            content = @Content
        )
    })
    public void delete(
        @Parameter(
            description = "Kürzel des Mitarbeiters",
            required = true,
            example = "tst"
        )
        @PathVariable String employeeSign, 

        @Parameter(
            description = "Das Datum im Format yyyy-MM-dd",
            required = true,
            example = "2023-09-01"
        )
        @PathVariable String date) {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        var deleteDate = DateUtils.parse(date);

        var employee = employeeService.getEmployeeBySign(employeeSign);
        var employeecontract = employeecontractService.getEmployeeContractValidAt(employee.getId(), deleteDate);
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        var workingDay = workingdayService.getWorkingday(employeecontract.getId(), deleteDate);
        if(workingDay == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        try {
            workingdayService.deleteWorkingdayById(workingDay.getId());
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(FORBIDDEN, e.toString(), e);
        }
    }
}
