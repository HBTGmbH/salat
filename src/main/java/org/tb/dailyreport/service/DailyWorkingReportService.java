package org.tb.dailyreport.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.rest.DailyReportData;
import org.tb.dailyreport.rest.DailyWorkingReportData;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;

import java.time.LocalDate;
import java.util.List;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.tb.common.ErrorCode.EC_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.common.ErrorCode.TR_UPSERT_WORKING_DAY;

@Service
@AllArgsConstructor
@Transactional
public class DailyWorkingReportService {
    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final WorkingdayDAO workingdayDAO;
    private final WorkingdayService workingdayService;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;

    public void createReport(DailyWorkingReportData report)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        doCreateReport(report, false);
    }

    public void updateReport(DailyWorkingReportData report)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        doCreateReport(report, true);
    }

    private void doCreateReport(DailyWorkingReportData report, boolean upsert)
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
                    doCreateDailyReports(report.getDate(), employeeOrderId, bookingsOfOrder, upsert);
                });
    }

    private void doCreateDailyReports(LocalDate day, Long employeeOrderId, List<DailyReportData> bookings, boolean upsert) {
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
}
