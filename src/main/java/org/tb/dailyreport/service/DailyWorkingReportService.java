package org.tb.dailyreport.service;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static org.tb.common.exception.ErrorCode.EC_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.rest.DailyReportData;
import org.tb.dailyreport.rest.DailyWorkingReportData;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;

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
    private final TimereportDAO timereportDAO;

    public void createReports(List<DailyWorkingReportData> reports)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        reports.forEach(report -> doCreateReport(report, false));
    }

    public void updateReports(List<DailyWorkingReportData> reports)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        reports.forEach(report -> doCreateReport(report, true));
    }

    private void doCreateReport(DailyWorkingReportData report, boolean upsert)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        var employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(authorizedUser.getEmployeeId(), report.getDate());
        if(employeecontract == null) {
            throw new AuthorizationException(EC_EMPLOYEE_CONTRACT_NOT_FOUND);
        }

        doCreateWorkingDay(report, employeecontract);

        report.getDailyReports().stream().collect(groupingBy(DailyReportData::getEmployeeorderId))
                .forEach((employeeOrderId, bookingsOfOrder) -> {
                    doCreateDailyReports(report.getDate(), employeeOrderId, bookingsOfOrder, upsert);
                });
    }

    private void doCreateWorkingDay(DailyWorkingReportData report, Employeecontract employeecontract) {
        var existingWorkingDay = ofNullable(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(
                report.getDate(), requireNonNull(employeecontract.getId(), "ID of contract is required")));

        var workingDay = existingWorkingDay.orElseGet(() -> {
            var newWorkingDay = new Workingday();
            newWorkingDay.setEmployeecontract(employeecontract);
            newWorkingDay.setRefday(report.getDate());
            return newWorkingDay;
        });
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
    }

    private void doCreateDailyReports(LocalDate day, Long employeeOrderId, List<DailyReportData> bookings, boolean upsert) {
        var employeeOrder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
        if (employeeOrder == null) {
            throw new InvalidDataException(TR_EMPLOYEE_ORDER_NOT_FOUND);
        }

        var employeeContract = employeeOrder.getEmployeecontract();
        if (employeeContract == null) {
            throw new InvalidDataException(TR_EMPLOYEE_CONTRACT_NOT_FOUND);
        }

        var existingBookings = timereportDAO.getTimereportsByDateAndEmployeeOrderId(day, employeeOrderId)
                .stream().map(DailyReportData::valueOf).toList();
        var existingBookingsWithoutId = existingBookings
                .stream().map(DailyReportData::withoutId).toList();

        var newBookings = bookings.stream().filter(not(existingBookingsWithoutId::contains)).toList();
        var oldBookings = existingBookings.stream().filter(booking -> !bookings.contains(booking.withoutId())).toList();

        if (!oldBookings.isEmpty() && upsert){
            oldBookings.stream().map(DailyReportData::getId).filter(Objects::nonNull).forEach(timereportDAO::deleteTimereportById);
        }

        newBookings.forEach(booking -> doCreateDailyReport(day, booking, employeeOrder, employeeContract));
    }

    private void doCreateDailyReport(LocalDate day, DailyReportData booking, Employeeorder employeeorder, Employeecontract employeeContract) {
        timereportService.createTimereports(
                authorizedUser,
                requireNonNull(employeeContract.getId(), "ID of contract is required"),
                requireNonNull(employeeorder.getId(), "ID of order is required"),
                day,
                booking.getComment(),
                booking.isTraining(),
                booking.getHours(),
                booking.getMinutes(),
                1
        );
    }
}
