package org.tb.dailyreport.service;

import static java.util.stream.Collectors.toMap;
import static org.tb.common.util.DateUtils.isInRange;
import static org.tb.common.util.DateUtils.today;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.MatrixData;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.employee.service.EmployeecontractService;

@Service
@RequiredArgsConstructor
@Transactional
@Authorized
public class MatrixService {

    private final TimereportService timereportService;
    private final PublicholidayService publicholidayService;
    private final OvertimeService overtimeService;
    private final WorkingdayService workingdayService;
    private final EmployeecontractService employeecontractService;

    @Transactional(readOnly = true)
    public MatrixData buildMatrix(YearMonth yearMonth, long employeeContractId) {
        LocalDate dateFirst = yearMonth.atDay(1);
        LocalDate dateLast = yearMonth.atEndOfMonth();
        LocalDate today = today();

        var reports = employeeContractId > 0
            ? timereportService.getTimereportsByDatesAndEmployeeContractId(employeeContractId, dateFirst, dateLast)
            : timereportService.getTimereportsByDates(dateFirst, dateLast);

        Map<LocalDate, String> holidays = publicholidayService
            .getPublicHolidaysBetween(dateFirst, dateLast)
            .stream()
            .collect(toMap(Publicholiday::getRefdate, Publicholiday::getName));

        Map<LocalDate, Workingday> workingdays = employeeContractId > 0
            ? workingdayService.getWorkingdaysByEmployeeContractId(employeeContractId, dateFirst, dateLast)
                .stream().collect(toMap(Workingday::getRefday, Function.identity()))
            : Map.of();

        List<LocalDate> days = dateFirst.datesUntil(dateLast.plusDays(1)).collect(Collectors.toList());

        List<MatrixData.DayHeader> dayHeaders = days.stream()
            .map(d -> new MatrixData.DayHeader(
                d.getDayOfMonth(),
                d,
                weekdayKey(d.getDayOfWeek()),
                isWeekend(d),
                holidays.containsKey(d),
                d.isEqual(today)))
            .toList();

        List<MatrixData.Row> rows = reports.stream()
            .collect(Collectors.groupingBy(TimereportDTO::getSuborderId))
            .entrySet().stream()
            .sorted(Comparator.comparing(e -> e.getValue().getFirst().getCompleteOrderSign()))
            .map(e -> buildRow(e.getValue(), days, holidays))
            .toList();

        Map<LocalDate, Duration> durationByDay = reports
            .stream()
            .collect(toMap(
                TimereportDTO::getReferenceday,
                TimereportDTO::getDuration,
                Duration::plus
            )
        );

        var beginErrors = employeeContractId > 0
            ? timereportService.validateBeginOfWorkingDays(employeeContractId, dateFirst, dateLast)
            : Map.of();
        var breakErrors = employeeContractId > 0
            ? timereportService.validateBreakTimes(employeeContractId, dateFirst, dateLast)
            : Map.of();

        List<MatrixData.FooterDay> footerDays = days.stream()
            .map(d -> {
                Duration duration = durationByDay.getOrDefault(d, Duration.ZERO);
                Workingday wd = workingdays.get(d);
                boolean notWorked = wd != null && wd.getType() == Workingday.WorkingDayType.NOT_WORKED;
                String beginString = null;
                String breakString = null;
                String endString = null;
                if (wd != null && wd.getType() == Workingday.WorkingDayType.WORKED && !duration.isZero()) {
                    beginString = "%02d:%02d".formatted(wd.getStarttimehour(), wd.getStarttimeminute());
                    breakString = DurationUtils.format(wd.getBreakLength());
                    LocalTime end = LocalTime.of(wd.getStarttimehour(), wd.getStarttimeminute())
                        .plus(wd.getBreakLength())
                        .plus(duration);
                    endString = "%02d:%02d".formatted(end.getHour(), end.getMinute());
                }
                return new MatrixData.FooterDay(
                    DurationUtils.format(duration, false),
                    notWorked,
                    isWeekend(d),
                    holidays.containsKey(d),
                    duration.isZero(),
                    beginString,
                    breakString,
                    endString,
                    beginErrors.containsKey(d),
                    breakErrors.containsKey(d),
                    workingdayService.checkLaborTimeMaximum(duration));
            })
            .toList();

        Duration grand = reports.stream()
            .map(TimereportDTO::getDuration)
            .reduce(Duration.ZERO, Duration::plus);

        boolean hasTarget = employeeContractId > 0
            && !employeecontractService.getEmployeecontractById(employeeContractId).getDailyWorkingTime().isZero();

        String totalString = hasTarget ? DurationUtils.format(grand) : null;
        String targetString = null;
        String diffString = null;
        boolean diffNegative = false;
        if (hasTarget) {
            Duration target = overtimeService.calculateWorkingTimeTarget(employeeContractId, dateFirst, dateLast);
            Duration diff = grand.minus(target);
            targetString = DurationUtils.format(target);
            diffString = (diff.isNegative() ? "" : "+") + DurationUtils.format(diff);
            diffNegative = diff.isNegative();
        }

        String prevDayDiffString = null;
        boolean prevDayDiffNegative = false;
        if (hasTarget && isInRange(today, dateFirst, dateLast)) {
            var cutoff = today.minusDays(1);
            if (!cutoff.isBefore(dateFirst)) {
                Duration grandPrevDay = reports.stream()
                    .filter(r -> !r.getReferenceday().isAfter(cutoff))
                    .map(TimereportDTO::getDuration)
                    .reduce(Duration.ZERO, Duration::plus);
                Duration targetPrevDay = overtimeService.calculateWorkingTimeTarget(employeeContractId, dateFirst, cutoff);
                Duration prevDayDiff = grandPrevDay.minus(targetPrevDay);
                prevDayDiffString = (prevDayDiff.isNegative() ? "" : "+") + DurationUtils.format(prevDayDiff);
                prevDayDiffNegative = prevDayDiff.isNegative();
            }
        }

        return new MatrixData(dayHeaders, rows, footerDays, totalString, targetString, diffString, diffNegative, prevDayDiffString, prevDayDiffNegative);
    }

    public void fillNotWorked(YearMonth yearMonth, long employeeContractId) {
        var employeecontract = employeecontractService.getEmployeecontractById(employeeContractId);
        LocalDate first = yearMonth.atDay(1);
        LocalDate last = yearMonth.atEndOfMonth();
        if (first.isBefore(employeecontract.getValidFrom())) {
            first = employeecontract.getValidFrom();
        }
        if (employeecontract.getValidUntil() != null && last.isAfter(employeecontract.getValidUntil())) {
            last = employeecontract.getValidUntil();
        }
        if (first.isAfter(last)) return;
        LocalDate effectiveFirst = first;
        LocalDate effectiveLast = last;
        effectiveFirst.datesUntil(effectiveLast.plusDays(1)).forEach(day -> {
            if (workingdayService.isRegularWorkingday(day)) {
                boolean hasBookings = !timereportService.getTimereportsByDateAndEmployeeContractId(employeeContractId, day).isEmpty();
                if (!hasBookings) {
                    var workingday = workingdayService.getWorkingday(employeeContractId, day);
                    if (workingday == null) {
                        workingday = new Workingday();
                        workingday.setEmployeecontract(employeecontract);
                        workingday.setRefday(day);
                    }
                    workingday.setType(Workingday.WorkingDayType.NOT_WORKED);
                    workingday.setStarttimehour(0);
                    workingday.setStarttimeminute(0);
                    workingday.setBreakhours(0);
                    workingday.setBreakminutes(0);
                    workingdayService.upsertWorkingday(workingday);
                }
            }
        });
    }

    private MatrixData.Row buildRow(
            List<org.tb.dailyreport.domain.TimereportDTO> suborderReports,
            List<LocalDate> days,
            Map<LocalDate, String> holidays) {

        var first = suborderReports.getFirst();
        String suborderSign = first.getCompleteOrderSign();
        String customerOrderSign = first.getCustomerorderSign();
        String customer = first.getCustomerShortname();
        String customerOrderDesc = first.getCustomerorderDescription();
        String suborderDesc = first.getSuborderDescription();

        Map<LocalDate, List<org.tb.dailyreport.domain.TimereportDTO>> reportsByDate = suborderReports.stream()
            .collect(Collectors.groupingBy(TimereportDTO::getReferenceday));

        Duration rowTotal = Duration.ZERO;
        List<MatrixData.Cell> cells = new ArrayList<>();
        for (LocalDate day : days) {
            var dayReports = reportsByDate.getOrDefault(day, List.of());
            Duration duration = dayReports.stream().map(TimereportDTO::getDuration).reduce(Duration.ZERO, Duration::plus);
            rowTotal = rowTotal.plus(duration);
            var details = dayReports.stream()
                .map(r -> new MatrixData.ReportDetail(DurationUtils.format(r.getDuration()), r.getTaskdescription()))
                .toList();
            cells.add(new MatrixData.Cell(
                DurationUtils.format(duration, false),
                duration.isZero(),
                isWeekend(day),
                holidays.containsKey(day),
                details));
        }

        return new MatrixData.Row(customerOrderSign, suborderSign, customer, customerOrderDesc, suborderDesc, cells, DurationUtils.format(rowTotal));
    }

    private String weekdayKey(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "main.matrixoverview.weekdays.monday.text";
            case TUESDAY -> "main.matrixoverview.weekdays.tuesday.text";
            case WEDNESDAY -> "main.matrixoverview.weekdays.wednesday.text";
            case THURSDAY -> "main.matrixoverview.weekdays.thursday.text";
            case FRIDAY -> "main.matrixoverview.weekdays.friday.text";
            case SATURDAY -> "main.matrixoverview.weekdays.saturday.text";
            case SUNDAY -> "main.matrixoverview.weekdays.sunday.text";
        };
    }

    private boolean isWeekend(LocalDate d) {
        var dow = d.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
