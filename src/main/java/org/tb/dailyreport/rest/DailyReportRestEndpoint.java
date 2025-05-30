package org.tb.dailyreport.rest;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.tb.dailyreport.rest.DailyReportCsvConverter.TEXT_CSV_DAILY_REPORT;
import static org.tb.dailyreport.rest.DailyReportCsvConverter.TEXT_CSV_DAILY_REPORT_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/daily-reports", "/rest/daily-reports" })
@Tag(name = "daily report")
public class DailyReportRestEndpoint {

    private final EmployeecontractService employeecontractService;
    private final EmployeeorderService employeeorderService;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;

    @GetMapping(path = "/list", produces = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_REPORT_VALUE})
    @ResponseStatus(OK)
    @Operation
    public ResponseEntity<List<DailyReportData>> getBookings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refDate,
            @RequestParam(defaultValue = "1") int days,
            @RequestParam(defaultValue = "false") boolean csv
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
    @Operation
    public ResponseEntity<List<DailyReportData>> getBookingsForEmployee(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refDate,
            @RequestParam(defaultValue = "1") int days,
            @PathVariable Long employeeContractId,
            @RequestParam(defaultValue = "false") boolean csv
    ) {
        checkAuthenticated();
        if (refDate == null) refDate = DateUtils.today();
        var employeecontract = employeecontractService.getEmployeecontractById(employeeContractId);
        if (employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        if (!userIsAllowedToReadContract(employeecontract)) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        var response = ResponseEntity.ok();
        if (csv) {
            response = response.contentType(TEXT_CSV_DAILY_REPORT);
        }
        return response.body(getDailyReports(employeecontract.getId(), refDate, days));
    }

    @PostMapping(path = "/", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation
    public void createBooking(
            @RequestBody DailyReportData booking
    ) {
        checkAuthenticated();
        try {
            createDailyReport(booking);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereport. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    @PostMapping(path = "/list", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation
    public void createBookings(
            @RequestBody List<DailyReportData> bookings
    ) {
        checkAuthenticated();
        try {
            bookings.forEach(this::createDailyReport);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereports. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    @PutMapping(path = "/list", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation
    public void updateBookings(
            @RequestBody List<DailyReportData> bookings
    ) {
        checkAuthenticated();
        try {
            bookings.stream().collect(groupingBy(DailyReportData::getDate))
                    .forEach((day, bookingsOfDay)-> bookingsOfDay.stream().collect(groupingBy(DailyReportData::getEmployeeorderId))
                            .forEach((employeeOrderId, bookingsOfOrder) ->
                                    replaceDailyReports(DateUtils.parse(day), employeeOrderId, bookingsOfOrder))
                    );
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereports. " + e);
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
    @Operation
    public void deleteBooking(
            @RequestBody DailyReportData report
    ) {
        checkAuthenticated();
        timereportService.deleteTimereportById(report.getId());
    }

    private void checkAuthenticated() {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
    }

    private boolean userIsAllowedToReadContract(Employeecontract employeecontract) {
        if (authorizedUser.isManager()) {
            return true;
        }
        return Objects.equals(employeecontract.getEmployee().getId(), authorizedUser.getEmployeeId());
    }
}
