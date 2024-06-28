package org.tb.dailyreport.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.persistence.EmployeecontractDAO;

import java.time.LocalDate;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/workingday", "/rest/workingday" })
@Tag(name = "working days")
public class WorkingDayRestEndpoint {

    private final EmployeecontractDAO employeecontractDAO;
    private final WorkingdayDAO workingdayDAO;
    private final AuthorizedUser authorizedUser;

    @PutMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(CREATED)
    @Operation
    public void upsert(@RequestBody WorkingDayData data) {
        if(!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        var date = ofNullable(data.getDate()).map(DateUtils::parse).orElseGet(DateUtils::today);
        var employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(authorizedUser.getEmployeeId(), date);
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        try {
            var wd = ofNullable(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeecontract.getId())).orElseGet(Workingday::new);
            wd.setEmployeecontract(employeecontract);
            wd.setRefday(date);
            wd.setStarttimehour(data.getStarthour());
            wd.setStarttimeminute(data.getStartminute());
            wd.setBreakhours(data.getBreakhours());
            wd.setBreakminutes(data.getBreakminutes());
            workingdayDAO.save(wd);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create workingday. " + e.getErrorCode());
        } catch (InvalidDataException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create workingday. " + e.getErrorCode());
        }
    }

    @GetMapping(produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation
    public WorkingDayData getToday() {
        return doGet(DateUtils.today());
    }

    @GetMapping(path = "/{date}", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation
    public WorkingDayData get(@PathVariable String date) {
        return doGet(DateUtils.parse(date));
    }

    private WorkingDayData doGet(LocalDate date){
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        var employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(authorizedUser.getEmployeeId(), date);
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        var workingDay = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeecontract.getId());
        if(workingDay == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        return mapToWorkingDayData(workingDay);
    }

    private WorkingDayData mapToWorkingDayData(Workingday wd) {
        return WorkingDayData.builder()
                .id(wd.getId())
                .starthour(wd.getStarttimehour())
                .startminute(wd.getStarttimeminute())
                .breakhours(wd.getBreakhours())
                .breakminutes(wd.getBreakminutes())
                .date(DateUtils.format(wd.getRefday()))
                .build();
    }

    @DeleteMapping(consumes = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation
    public void delete(@RequestBody WorkingDayData data) {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        if (data.getId() != -1) {
            workingdayDAO.deleteWorkingdayById(data.getId());
        }
    }
}
