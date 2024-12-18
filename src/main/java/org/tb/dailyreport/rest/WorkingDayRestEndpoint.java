package org.tb.dailyreport.rest;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
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
import org.tb.employee.service.EmployeecontractService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/workingday", "/rest/workingday" })
@Tag(name = "working days")
public class WorkingDayRestEndpoint {

    private final EmployeecontractService employeecontractService;
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
        var employeecontract = employeecontractService.getEmployeeContractValidAt(authorizedUser.getEmployeeId(), date);
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
            throw new ResponseStatusException(UNAUTHORIZED, e.toString());
        } catch(InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, e.toString());
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

        var employeecontract = employeecontractService.getEmployeeContractValidAt(authorizedUser.getEmployeeId(), date);
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        var workingDay = workingdayService.getWorkingday(employeecontract.getId(), date);
        if(workingDay == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        return WorkingDayData.valueOf(workingDay);
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(OK)
    @Operation
    public void delete(@PathVariable String date) {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        var deleteDate = DateUtils.parse(date);

        var employeecontract = employeecontractService.getEmployeeContractValidAt(authorizedUser.getEmployeeId(), deleteDate);
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        var workingDay = workingdayService.getWorkingday(employeecontract.getId(), deleteDate);
        if(workingDay == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        workingdayService.deleteWorkingdayById(workingDay.getId());
    }
}
