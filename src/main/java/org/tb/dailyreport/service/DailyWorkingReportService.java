package org.tb.dailyreport.service;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static org.tb.common.exception.ErrorCode.EC_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
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
@Authorized
public class DailyWorkingReportService {
    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final WorkingdayDAO workingdayDAO;
    private final WorkingdayService workingdayService;
    private final TimereportService timereportService;
    private final TimereportDAO timereportDAO;

    @Transactional(readOnly = true)
    public List<DailyWorkingReportData> getReportsForMonth(YearMonth month, long employeeContractId) {
        return month.atDay(1).datesUntil(month.atEndOfMonth().plusDays(1))
            .map(day -> buildReportForDay(employeeContractId, day))
            .filter(Objects::nonNull)
            .toList();
    }

    private DailyWorkingReportData buildReportForDay(long contractId, LocalDate date) {
        var workingDay = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, contractId);
        var timeReports = timereportService.getTimereportsByDateAndEmployeeContractId(contractId, date)
            .stream().map(DailyReportData::valueOf).toList();
        if (workingDay == null && timeReports.isEmpty()) return null;
        var builder = DailyWorkingReportData.builder().date(date).dailyReports(timeReports);
        if (workingDay != null) {
            builder.type(workingDay.getType());
            if (workingDay.getType() != WorkingDayType.NOT_WORKED) {
                var bd = workingDay.getBreakLength();
                builder.startTime(workingDay.getStartOfWorkingDay().toLocalTime());
                builder.breakDuration(LocalTime.of(bd.toHoursPart(), bd.toMinutesPart()));
            }
        }
        return builder.build();
    }

    public ImportReport createReports(List<DailyWorkingReportData> reports, long contractId)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        return new ImportReport(reports.stream().map(r -> doCreateReport(r, false, contractId)).toList());
    }

    public ImportReport updateReports(List<DailyWorkingReportData> reports, long contractId)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        return new ImportReport(reports.stream().map(r -> doCreateReport(r, true, contractId)).toList());
    }

    private ImportReport.DayResult doCreateReport(DailyWorkingReportData report, boolean upsert, long contractId)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        var employeecontract = employeecontractDAO.getEmployeecontractById(contractId);
        if(employeecontract == null) {
            throw new AuthorizationException(EC_EMPLOYEE_CONTRACT_NOT_FOUND);
        }

        boolean workingDayCreated = doCreateWorkingDay(report, employeecontract);

        var totals = report.getDailyReports().stream()
            .collect(groupingBy(DailyReportData::getEmployeeorderId))
            .entrySet().stream()
            .map(e -> doCreateDailyReports(report.getDate(), e.getKey(), e.getValue(), upsert))
            .reduce(new BookingCounts(List.of(), List.of()), BookingCounts::add);

        return new ImportReport.DayResult(report.getDate(), workingDayCreated, totals.created(), totals.deleted());
    }

    private boolean doCreateWorkingDay(DailyWorkingReportData report, Employeecontract employeecontract) {
        var existingWorkingDay = ofNullable(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(
                report.getDate(), requireNonNull(employeecontract.getId(), "ID of contract is required")));

        boolean created = existingWorkingDay.isEmpty();
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
        return created;
    }

    private BookingCounts doCreateDailyReports(LocalDate day, Long employeeOrderId, List<DailyReportData> bookings, boolean upsert) {
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
            var ids = oldBookings.stream().map(DailyReportData::getId).filter(Objects::nonNull).toList();
            timereportService.deleteTimereportsById(ids);
        }

        newBookings.forEach(booking -> doCreateDailyReport(day, booking, employeeOrder, employeeContract));
        var createdDetails = newBookings.stream().map(DailyWorkingReportService::toBookingDetail).toList();
        var deletedDetails = upsert ? oldBookings.stream().map(DailyWorkingReportService::toBookingDetail).toList() : List.<ImportReport.BookingDetail>of();
        return new BookingCounts(createdDetails, deletedDetails);
    }

    private static ImportReport.BookingDetail toBookingDetail(DailyReportData b) {
        return new ImportReport.BookingDetail(b.getSuborderSign(), b.getSuborderLabel(), b.getHours(), b.getMinutes(), b.getComment());
    }

    private record BookingCounts(List<ImportReport.BookingDetail> created, List<ImportReport.BookingDetail> deleted) {
        BookingCounts add(BookingCounts other) {
            return new BookingCounts(
                Stream.concat(created.stream(), other.created.stream()).toList(),
                Stream.concat(deleted.stream(), other.deleted.stream()).toList()
            );
        }
    }

    private void doCreateDailyReport(LocalDate day, DailyReportData booking, Employeeorder employeeorder, Employeecontract employeeContract) {
        timereportService.createTimereports(
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
