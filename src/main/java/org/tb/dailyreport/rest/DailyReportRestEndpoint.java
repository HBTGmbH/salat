package org.tb.dailyreport.rest;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.Timereport;
import org.tb.dailyreport.TimereportDAO;
import org.tb.dailyreport.TimereportService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.Employeeorder;
import org.tb.order.EmployeeorderDAO;

@RestController
@RequiredArgsConstructor
@SecurityScheme(name = "basicAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "basic"
)
@RequestMapping(path = "/rest/daily-reports")
public class DailyReportRestEndpoint {

    private final EmployeecontractDAO employeecontractDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;

    @GetMapping(path = "/list", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation(security = @SecurityRequirement(name = "basicAuth"))
    public List<DailyReportData> getBookings(
        @RequestParam("refDate")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate refDate
    ) {
        if(!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        if (refDate == null) refDate = DateUtils.today();
        Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(
            authorizedUser.getEmployeeId(),
            refDate
        );
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        List<Timereport> timeReports = timereportDAO.getTimereportsByDateAndEmployeeContractId(
            employeecontract.getId(),
            refDate
        );
        return timeReports.stream()
            .map(tr ->
                DailyReportData.builder()
                    .employeeorderId(tr.getEmployeeorder().getId())
                    .date(DateUtils.format(tr.getReferenceday().getRefdate()))
                    .orderLabel(tr.getSuborder().getCustomerorder().getShortdescription())
                    .suborderLabel(tr.getSuborder().getDescription())
                    .comment(tr.getTaskdescription())
                    .isTraining(tr.getSuborder().getCommentnecessary())
                    .hours(tr.getDurationhours())
                    .minutes(tr.getDurationminutes())
                    .suborderSign(tr.getSuborder().getSign())
                    .orderSign(tr.getSuborder().getCustomerorder().getSign())
                    .build()
            )
            .collect(Collectors.toList());
    }

    @PostMapping(path = "/", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(CREATED)
    @Operation(security = @SecurityRequirement(name = "basicAuth"))
    public void createBooking(@RequestBody DailyReportData booking) {
        if(!authorizedUser.isAuthenticated()) {
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
}
