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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.LocalDateRange;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.MatrixData;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.UnbookedWorkingDays;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.TimereportRepository;
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
    private final TimereportRepository timereportRepository;

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

        // standby is no working time, so its orders are kept apart: they are listed below the sum
        // row and are part of neither the daily sums nor the total, SOLL and difference (#463)
        var reportsByKind = reports.stream().collect(Collectors.partitioningBy(TimereportDTO::isStandby));
        List<MatrixData.Row> rows = buildRows(reportsByKind.get(false), days, holidays);
        List<MatrixData.Row> standbyRows = buildRows(reportsByKind.get(true), days, holidays);

        Map<LocalDate, Duration> durationByDay = reports
            .stream()
            .collect(toMap(
                TimereportDTO::getReferenceday,
                TimereportDTO::getWorkingTime,
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
            .map(TimereportDTO::getWorkingTime)
            .reduce(Duration.ZERO, Duration::plus);

        boolean hasTarget = employeeContractId > 0
            && !employeecontractService.getEmployeecontractById(employeeContractId).getDailyWorkingTime().isZero();

        // the grand total is nothing but the sum of the bookings shown above it, so it exists
        // whenever the matrix does. It used to be suppressed together with SOLL and difference for
        // a contract without a daily working time, which left the GESAMT row without its Σ (#887)
        String totalString = DurationUtils.format(grand);
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
                    .map(TimereportDTO::getWorkingTime)
                    .reduce(Duration.ZERO, Duration::plus);
                Duration targetPrevDay = overtimeService.calculateWorkingTimeTarget(employeeContractId, dateFirst, cutoff);
                Duration prevDayDiff = grandPrevDay.minus(targetPrevDay);
                prevDayDiffString = (prevDayDiff.isNegative() ? "" : "+") + DurationUtils.format(prevDayDiff);
                prevDayDiffNegative = prevDayDiff.isNegative();
            }
        }

        return new MatrixData(dayHeaders, rows, standbyRows, footerDays, totalString, targetString, diffString, diffNegative, prevDayDiffString, prevDayDiffNegative);
    }

    /**
     * "Rest nicht gearbeitet": marks every working day of the month without a booking as not worked.
     * Which days those are is decided by {@link UnbookedWorkingDays}, the same rule the release and
     * the dashboard hint apply (#1124); it also keeps to the validity of the contract.
     *
     * <p>A booking of any status makes a day booked, read without the per-row READ filter of
     * {@code TimereportDAO}: all that matters is whether something is booked on the day. Who passes
     * the working-day check below is the owner, the Geschäftsführung (admins included), the
     * supervising people lead or a holder of a {@code WORKINGDAY}/{@code WRITE} rule. The first three
     * read every booking of the contract anyway. A rule holder may not, and for him the unfiltered
     * read is what keeps a day with a booking he cannot see from being marked: the
     * {@code WD_NOT_WORKED_TIMEREPORTS_FOUND} check in {@link WorkingdayService#upsertWorkingday}
     * reads through the READ filter, finds nothing for him and would let the day pass. Until #1124
     * this action read through the filter as well and marked such days. Do not bring that read
     * back. What the rule holder learns is only that a day carries a booking: it is the day the
     * action leaves unmarked.
     *
     * <p>The working days are loaded first because that is where the right to read the working days
     * of the contract is checked; only then are the booked days read. A month the contract does not
     * reach ends the action before anything is read or checked: there is nothing to fill, and
     * whoever may not fill the month gets no error then, as before #1124. In a month the contract
     * reaches, the right is checked whether or not a day is left to
     * fill. Before #1124 it was only checked at the first day without a booking the caller could
     * see, so someone who could read every booking of a fully booked month, but not its working
     * days, got the success message; now he gets {@code WD_READ_REQ_EMPLOYEE_OR_MANAGER}. That
     * difference is intended: whether a caller is refused should not depend on what is booked.
     *
     * <p>Booked days, working days and public holidays are loaded once for the whole month. A day
     * already marked as not worked is left as it is and not saved again.
     */
    public void fillNotWorked(YearMonth yearMonth, long employeeContractId) {
        var employeecontract = employeecontractService.getEmployeecontractById(employeeContractId);
        if (!employeecontract.getValidity().overlaps(new LocalDateRange(yearMonth))) {
            return;
        }
        LocalDate first = yearMonth.atDay(1);
        LocalDate last = yearMonth.atEndOfMonth();

        Map<LocalDate, Workingday> workingdays = workingdayService
            .getWorkingdaysByEmployeeContractId(employeeContractId, first, last)
            .stream().collect(toMap(Workingday::getRefday, Function.identity()));
        Set<LocalDate> bookedDays = Set.copyOf(timereportRepository.findBookedDaysBetween(employeeContractId, first, last));
        Set<LocalDate> publicHolidays = publicholidayService.getPublicHolidaysBetween(first, last)
            .stream().map(Publicholiday::getRefdate).collect(Collectors.toSet());

        UnbookedWorkingDays.between(first, last, employeecontract, bookedDays, workingdays, publicHolidays)
            .forEach(day -> {
                var workingday = workingdays.get(day);
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
            });
    }

    private List<MatrixData.Row> buildRows(
            List<TimereportDTO> reports,
            List<LocalDate> days,
            Map<LocalDate, String> holidays) {
        return reports.stream()
            .collect(Collectors.groupingBy(TimereportDTO::getSuborderId))
            .entrySet().stream()
            .sorted(Comparator.comparing(e -> e.getValue().getFirst().getCompleteOrderSign()))
            .map(e -> buildRow(e.getValue(), days, holidays))
            .toList();
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
