package org.tb.dailyreport.rest;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.tb.dailyreport.rest.DailyWorkingReportCsvConverter.TEXT_CSV_DAILY_WORKING_REPORT;
import static org.tb.dailyreport.rest.DailyWorkingReportCsvConverter.TEXT_CSV_DAILY_WORKING_REPORT_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.dailyreport.service.DailyWorkingReportService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.service.EmployeecontractService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/daily-working-reports", "/rest/daily-working-reports" })
@Tag(name = "daily report")
public class DailyWorkingReportRestEndpoint {

    private final EmployeecontractService employeecontractService;
    private final TimereportService timereportService;
    private final WorkingdayService workingdayService;
    private final DailyWorkingReportService dailyWorkingReportService;
    private final AuthorizedUser authorizedUser;

    @GetMapping(path = "/list", produces = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_WORKING_REPORT_VALUE})
    @ResponseStatus(OK)
    @Operation
    public ResponseEntity<List<DailyWorkingReportData>> getReports(
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
            var filename = String.format("%s-%sd.csv", DateUtils.format(refDate), days);
            response = response.header(CONTENT_DISPOSITION, "attachment; filename=" + filename);
            response = response.contentType(TEXT_CSV_DAILY_WORKING_REPORT);
        }
        return response.body(getReports(employeecontract.getId(), refDate, days));
    }

    private List<DailyWorkingReportData> getReports(Long employeeContractId, LocalDate startDay, int days) {
        return IntStream.range(0, days)
                .mapToObj(day -> DateUtils.addDays(startDay, day))
                .map(day -> getReport(employeeContractId, day))
                .filter(Objects::nonNull)
                .toList();
    }

    private DailyWorkingReportData getReport(Long employeeContractId, LocalDate date) {
        var workingDay = workingdayService.getWorkingday(employeeContractId, date);

        var timeReports = timereportService.getTimereportsByDateAndEmployeeContractId(employeeContractId, date)
                .stream()
                .map(DailyReportData::valueOf)
                .toList();

        if(workingDay == null && timeReports.isEmpty()){
            return null;
        }

        var builder = DailyWorkingReportData.builder().date(date).dailyReports(timeReports);

        if(workingDay != null) {
            builder.type(workingDay.getType());
            if(workingDay.getType() != WorkingDayType.NOT_WORKED) {
                var bd = workingDay.getBreakLength();
                builder.startTime(workingDay.getStartOfWorkingDay().toLocalTime());
                builder.breakDuration(LocalTime.of(bd.toHoursPart(), bd.toMinutesPart()));
            }
        }

        return builder.build();
    }

    @PostMapping(path = "/", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_WORKING_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation
    public void createReport(
            @RequestBody DailyWorkingReportData report
    ) {
        checkAuthenticated();
        try {
            dailyWorkingReportService.createReports(List.of(report));
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereport. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    @PutMapping(path = "/", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_WORKING_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation
    public void replaceReport(
            @RequestBody DailyWorkingReportData report
    ) {
        checkAuthenticated();
        try {
            dailyWorkingReportService.updateReports(List.of(report));
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereport. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    @PostMapping(path = "/list", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_WORKING_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation
    public void createReports(
            @RequestBody List<DailyWorkingReportData> reports
    ) {
        checkAuthenticated();
        try {
            dailyWorkingReportService.createReports(reports);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereport. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    @PutMapping(path = "/list", consumes = {APPLICATION_JSON_VALUE, TEXT_CSV_DAILY_WORKING_REPORT_VALUE})
    @ResponseStatus(CREATED)
    @Operation
    public void replaceReports(
            @RequestBody List<DailyWorkingReportData> reports
    ) {
        checkAuthenticated();
        try {
            dailyWorkingReportService.updateReports(reports);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereport. " + e);
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e);
        }
    }

    private void checkAuthenticated() {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
    }
}
