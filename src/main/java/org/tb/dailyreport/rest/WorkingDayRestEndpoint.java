package org.tb.dailyreport.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.service.WorkingdayService;
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
    private final WorkingdayService workingdayService;
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

        var wd = ofNullable(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeecontract.getId())).orElseGet(Workingday::new);
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
            throw new ResponseStatusException(UNAUTHORIZED, e.getErrorCode().getCode());
        } catch(InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.getErrorCode().getCode());
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
                .type(wd.getType())
                .build();
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(OK)
    @Operation
    public void delete(@PathVariable String date) {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        var deleteDate = DateUtils.parse(date);

        var employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(authorizedUser.getEmployeeId(), deleteDate);
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        var workingDay = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(deleteDate, employeecontract.getId());
        if(workingDay == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        workingdayDAO.deleteWorkingdayById(workingDay.getId());
    }
}
