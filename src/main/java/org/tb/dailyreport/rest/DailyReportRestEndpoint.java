package org.tb.dailyreport.rest;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AccessLevel;
import org.tb.auth.AuthService;
import org.tb.auth.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;

@RestController
@RequiredArgsConstructor
@SecurityScheme(name = "apikey",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "x-api-key",
        scheme = "tokenId:secret",
        description = "tokenId:secret"
)

@CrossOrigin(origins = "*")
@RequestMapping(path = "/rest/daily-reports")
public class DailyReportRestEndpoint {

    private final EmployeecontractDAO employeecontractDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;
    private final AuthService authService;

    @GetMapping(path = "/list", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation(security = @SecurityRequirement(name = "apikey"))
    public List<DailyReportData> getBookings(
            @RequestParam("refDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate refDate
    ) {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        if (refDate == null) refDate = DateUtils.today();
        Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(
                authorizedUser.getEmployeeId(),
                refDate
        );
        if (employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        List<TimereportDTO> timeReports = timereportDAO.getTimereportsByDateAndEmployeeContractId(
                employeecontract.getId(),
                refDate
        );
        return timeReports.stream()
                .map(tr ->
                        DailyReportData.builder()
                                .employeeorderId(tr.getEmployeeorderId())
                                .date(DateUtils.format(tr.getReferenceday()))
                                .orderLabel(tr.getCustomerorderDescription())
                                .suborderLabel(tr.getSuborderDescription())
                                .comment(tr.getTaskdescription())
                                .training(tr.isTraining())
                                .hours(tr.getDuration().toHours())
                                .minutes(tr.getDuration().toMinutesPart())
                                .suborderSign(tr.getSuborderSign())
                                .orderSign(tr.getCustomerorderSign())
                                .build()
                )
                .collect(Collectors.toList());
    }

    @GetMapping(path = "/listFrequent", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation(security = @SecurityRequirement(name = "apikey"))
    public List<DailyReportData> listFrequentTimereport(
            @RequestParam(name = "employeeId", required = false)
            Long employeeId
    ) {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        if (employeeId == null) {
            employeeId = authorizedUser.getEmployeeId();
        }
        LocalDate refDate = DateUtils.today();
        Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(
                employeeId,
                refDate
        );
        if (employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        List<TimereportDTO> timeReports = timereportDAO.listFrequentByEmployeecontractIdAndReferencedayBetween(
                employeecontract.getId(),
                refDate.plusDays(-20),
                refDate,
                this::accessible
        );
        return timeReports.stream()
                .map(tr ->
                        DailyReportData.builder()
                                .employeeorderId(tr.getEmployeeorderId())
                                .date(DateUtils.format(tr.getReferenceday()))
                                .orderLabel(tr.getCustomerorderDescription())
                                .suborderLabel(tr.getSuborderDescription())
                                .comment(tr.getTaskdescription())
                                .training(tr.isTraining())
                                .hours(tr.getDuration().toHours())
                                .minutes(tr.getDuration().toMinutesPart())
                                .suborderSign(tr.getSuborderSign())
                                .orderSign(tr.getCustomerorderSign())
                                .build()
                )
                .collect(Collectors.toList());
    }

    @PostMapping(path = "/", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(CREATED)
    @Operation(security = @SecurityRequirement(name = "apikey"))
    public void createBooking(@RequestBody DailyReportData booking) {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        Employeeorder employeeorder = employeeorderDAO.getEmployeeorderById(booking.getEmployeeorderId());
        if (employeeorder == null) {
            throw new ResponseStatusException(NOT_FOUND, "Could not find employeeorder with id " + booking.getEmployeeorderId());
        }

        try {
            timereportService.createTimereports(
                    authorizedUser,
                    employeeorder.getEmployeecontract().getId(),
                    employeeorder.getId(),
                    DateUtils.parse(booking.getDate()),
                    booking.getComment(),
                    booking.isTraining(),
                    booking.getHours(),
                    booking.getMinutes(),
                    1
            );
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could create timereport. " + e.getErrorCode());
        } catch (InvalidDataException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could create timereport. " + e.getErrorCode());
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could create timereport. " + e.getErrorCode());
        }
    }

    private boolean accessible(Timereport timereport) {
        return authService.isAuthorized(timereport, authorizedUser, AccessLevel.READ);
    }
}
