package org.tb.restful.bookings;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.tb.GlobalConstants.SORT_OF_REPORT_WORK;

import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.bdom.AuthorizedUser;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Timereport;
import org.tb.exception.AuthorizationException;
import org.tb.exception.BusinessRuleException;
import org.tb.exception.InvalidDataException;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.ReferencedayDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.service.TimereportService;
import org.tb.util.DateUtils;

@RestController("/rest/buchungen")
@RequiredArgsConstructor
public class BookingsService {

    private final EmployeeDAO employeeDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final ReferencedayDAO referencedayDAO;
    private final TimereportService timereportService;

    @GetMapping(path = "/list", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    public List<Booking> getBookings(
        @RequestParam("datum") Date date,
        @RequestParam("mitarbeiter") String employeeStr
    ) {
        Long employeeId = null;
        Employee employee;
        if (employeeStr != null) {
            employee = employeeDAO.getEmployeeBySign(employeeStr);
            if (employee == null) {
                return Collections.emptyList();
            }
            employeeId = employee.getId();
        }
        if (date == null) date = DateUtils.today();

        // FIXME check that provided employeeId matches the authenticated user?

        Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, date);
        if(ec == null) {
            return Collections.emptyList();
        }

        List<Timereport> timeReports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ec.getId(), date);
        return timeReports.stream()
            .map(tr ->
                Booking.builder()
                    .employeeorderId(tr.getEmployeeorder().getId())
                    .costs(tr.getCosts())
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
        Employeeorder employeeorder = employeeorderDAO.getEmployeeorderById(booking.getEmployeeorderId());
        if (employeeorder == null) {
            throw new ResponseStatusException(NOT_FOUND, "Could not find employeeorder with id " + booking.getEmployeeorderId());
        }
        // FIXME check that provided Employeeorder.employee.id matches the authenticated user

        AuthorizedUser authorizedUser = new AuthorizedUser();
        authorizedUser.setEmployeeId(employeeorder.getEmployeecontract().getEmployee().getId());
        authorizedUser.setSign(employeeorder.getEmployeecontract().getEmployee().getSign());
        authorizedUser.setAuthenticated(true); // TODO introduce real authentication
        authorizedUser.setAdmin(employeeorder.getEmployeecontract().getEmployee().getRestricted());
        authorizedUser.setManager(employeeorder.getEmployeecontract().getEmployee().getRestricted());
        authorizedUser.setRestricted(employeeorder.getEmployeecontract().getEmployee().getRestricted());
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
                SORT_OF_REPORT_WORK,
                0.0,
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
