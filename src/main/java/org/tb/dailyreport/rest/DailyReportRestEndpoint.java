package org.tb.dailyreport.rest;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/daily-reports", "/rest/daily-reports" })
@Tag(name = "daily report")
public class DailyReportRestEndpoint {

    private final EmployeecontractDAO employeecontractDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;

    @GetMapping(path = "/list", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation
    public List<DailyReportData> getBookings(
        @RequestParam
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
        return getDailyReports(employeecontract.getId(), refDate);
    }

    @GetMapping(path = "/{employeeContractId}/list", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation
    public List<DailyReportData> getBookingsForEmployee(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refDate,
            @PathVariable Long employeeContractId
    ) {
        if(!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(employeeContractId);
        if (employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        if (!userIsAllowedToReadContract(employeecontract)) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
        return getDailyReports(employeeContractId, refDate);
    }

    @PostMapping(path = "/", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(CREATED)
    @Operation
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
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereport. " + e.getErrorCode());
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereport. " + e.getErrorCode());
        }
    }

    private List<DailyReportData> getDailyReports(Long employeeContractId, LocalDate refDate) {
        if (employeeContractId == null) {
            return Collections.emptyList();
        }
        LocalDate date = Optional.ofNullable(refDate).orElseGet(DateUtils::today);
        return timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContractId, date)
                .stream()
                .map(this::mapToDailyReportData)
                .collect(Collectors.toList());
    }

    private DailyReportData mapToDailyReportData(TimereportDTO tr) {
        return DailyReportData.builder()
                .id(tr.getId())
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
                .build();
    }

    private boolean userIsAllowedToReadContract(Employeecontract employeecontract) {
        if (authorizedUser.isManager()) {
            return true;
        }
        return Objects.equals(employeecontract.getEmployee().getId(), authorizedUser.getEmployeeId());
    }

    @DeleteMapping(path = "/", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation
    public void deleteBooking(@RequestBody DailyReportData report) {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        if (report.getId() != -1) {
            timereportDAO.deleteTimereportById(report.getId());
        }
    }
}
