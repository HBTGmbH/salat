package org.tb.dailyreport.rest;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.tb.common.ErrorCode.EC_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.common.ErrorCode.TR_UPSERT_WORKING_DAY;
import static org.tb.dailyreport.rest.DailyReportCsvConverter.TEXT_CSV_DAILY_REPORT;
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
import org.tb.auth.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/daily-working-reports", "/rest/daily-working-reports" })
@Tag(name = "daily report")
public class DailyWorkingReportRestEndpoint {

    private final EmployeecontractDAO employeecontractDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final WorkingdayDAO workingdayDAO;
    private final WorkingdayService workingdayService;
    private final TimereportService timereportService;
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
        var employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(authorizedUser.getEmployeeId(), refDate);
        if(employeecontract == null) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        var response = ResponseEntity.ok();
        if (csv) {
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

    private DailyWorkingReportData getReport(Long employeeContractId, LocalDate day) {
        var workingDay = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(day, employeeContractId);

        var timeReports = timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContractId, day)
                .stream()
                .map(DailyReportData::valueOf)
                .toList();

        if(workingDay == null && timeReports.isEmpty()){
            return null;
        }

        var builder = DailyWorkingReportData.builder().date(day).dailyReports(timeReports);

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
            createReport(report, false);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereport. " + e.getErrorCode());
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e.getErrorCode() + ": " + e.getMessage());
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
            createReport(report, true);
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereport. " + e.getErrorCode());
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e.getErrorCode() + ": " + e.getMessage());
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
            reports.forEach(report -> createReport(report, false));
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereport. " + e.getErrorCode());
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e.getErrorCode() + ": " + e.getMessage());
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
            reports.forEach(report -> createReport(report, true));
        } catch (AuthorizationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Could not create timereport. " + e.getErrorCode());
        } catch (InvalidDataException | BusinessRuleException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not create timereports. " + e.getErrorCode() + ": " + e.getMessage());
        }
    }

    private void createReport(DailyWorkingReportData report, boolean upsert)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        var employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(authorizedUser.getEmployeeId(), report.getDate());
        if(employeecontract == null) {
            throw new AuthorizationException(EC_EMPLOYEE_CONTRACT_NOT_FOUND);
        }

        var existingWorkingDay = ofNullable(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(
                report.getDate(), employeecontract.getId()));
        if (existingWorkingDay.isPresent() && !upsert) {
            throw new BusinessRuleException(TR_UPSERT_WORKING_DAY);
        }

        var workingDay = existingWorkingDay.orElseGet(Workingday::new);
        workingDay.setEmployeecontract(employeecontract);
        workingDay.setRefday(report.getDate());
        ofNullable(report.getBreakDuration()).ifPresentOrElse(bd -> {
            workingDay.setBreakhours(bd.getHour());
            workingDay.setBreakminutes(bd.getMinute());
        }, () -> {
            workingDay.setBreakhours(0);
            workingDay.setBreakminutes(0);
        });
        ofNullable(report.getStartTime()).ifPresentOrElse(st -> {
            workingDay.setStarttimehour(st.getHour());
            workingDay.setStarttimeminute(st.getMinute());
        }, () -> {
            workingDay.setStarttimehour(0);
            workingDay.setStarttimeminute(0);
        });
        workingDay.setType(report.getType());
        workingdayService.upsertWorkingday(workingDay);
        report.getDailyReports().stream().collect(groupingBy(DailyReportData::getEmployeeorderId))
                        .forEach((employeeOrderId, bookingsOfOrder) -> {
                            createDailyReports(report.getDate(), employeeOrderId, bookingsOfOrder, upsert);
                        });
    }

    private void createDailyReports(LocalDate day, Long employeeOrderId, List<DailyReportData> bookings, boolean upsert) {
        var employeeorder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
        if (employeeorder == null) {
            throw new ResponseStatusException(NOT_FOUND, "Could not find employeeorder with id " + employeeOrderId);
        }
        if (upsert) {
            timereportService.deleteTimeReports(day, employeeOrderId, authorizedUser);
        }
        bookings.forEach(booking -> doCreateDailyReport(day, booking, employeeorder));
    }

    private void doCreateDailyReport(LocalDate day, DailyReportData booking, Employeeorder employeeorder) {
        timereportService.createTimereports(
                authorizedUser,
                employeeorder.getEmployeecontract().getId(),
                employeeorder.getId(),
                day,
                booking.getComment(),
                booking.isTraining(),
                booking.getHours(),
                booking.getMinutes(),
                1
        );
    }

    private void checkAuthenticated() {
        if (!authorizedUser.isAuthenticated()) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
    }
}
