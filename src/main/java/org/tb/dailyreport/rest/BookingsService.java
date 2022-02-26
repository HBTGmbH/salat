package org.tb.dailyreport.rest;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import org.tb.employee.Employeecontract;
import org.tb.employee.EmployeecontractDAO;
import org.tb.order.Employeeorder;
import org.tb.order.EmployeeorderDAO;

@RestController("/rest/buchungen")
@RequiredArgsConstructor
public class BookingsService {

    private final EmployeecontractDAO employeecontractDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;

    @GetMapping(path = "/list", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    public List<Booking> getBookings(
        @RequestParam("datum") LocalDate refDate
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
                Booking.builder()
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

    @GetMapping(path = "/list", consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(CREATED)
    public void createBooking(@RequestBody Booking booking) {
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
        } catch (ParseException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not parse provided date. Please ensure format is DD.MM.YYYY");
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could create timereport. " + e.getErrorCode());
        } catch (InvalidDataException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could create timereport. " + e.getErrorCode());
        } catch (BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could create timereport. " + e.getErrorCode());
        }
    }
}