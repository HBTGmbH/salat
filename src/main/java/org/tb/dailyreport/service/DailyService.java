package org.tb.dailyreport.service;

import static java.time.DayOfWeek.MONDAY;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.tb.common.util.DateUtils.today;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.DailyViewData;
import org.tb.dailyreport.domain.DailyViewData.WeekStripDay;
import org.tb.dailyreport.domain.ListViewData;
import org.tb.dailyreport.domain.ListViewData.ListDay;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.employee.service.EmployeecontractService;

@Service
@RequiredArgsConstructor
@Transactional
@Authorized
public class DailyService {

    private final TimereportService timereportService;
    private final WorkingdayService workingdayService;
    private final PublicholidayService publicholidayService;
    private final OvertimeService overtimeService;
    private final EmployeecontractService employeecontractService;

    @Transactional(readOnly = true)
    public DailyViewData buildDailyView(LocalDate date, long employeeContractId) {
        var contract = employeecontractService.getEmployeecontractById(employeeContractId);
        boolean hasTarget = !contract.getDailyWorkingTime().isZero();

        List<TimereportDTO> timereports = timereportService.getTimereportsByDateAndEmployeeContractId(employeeContractId, date);
        Duration totalBooked = timereports.stream().map(TimereportDTO::getDuration).reduce(Duration.ZERO, Duration::plus);
        Workingday workingday = workingdayService.getWorkingday(employeeContractId, date);

        String quittingTime = workingdayService.calculateQuittingTime(workingday);
        String targetEndTime = hasTarget ? workingdayService.calculateWorkingDayEnds(workingday) : null;
        boolean overMaxHours = workingdayService.checkLaborTimeMaximum(timereports);

        long targetMinutes = hasTarget && workingday != null ? workingday.getEmployeecontract().getDailyWorkingTime().toMinutes() : 0;
        int progressPercent = targetMinutes > 0 ? (int) Math.min(100, totalBooked.toMinutes() * 100 / targetMinutes) : 0;

        List<WeekStripDay> weekStrip = buildWeekStrip(date, employeeContractId);

        boolean notWorked = workingday != null && workingday.getType() == Workingday.WorkingDayType.NOT_WORKED;
        int startHour   = workingday != null ? workingday.getStarttimehour()   : 8;
        int startMinute = workingday != null ? workingday.getStarttimeminute() : 0;
        int breakHours  = workingday != null ? workingday.getBreakhours()      : 0;
        int breakMinutes = workingday != null ? workingday.getBreakminutes()   : 30;
        String startTime = String.format("%02d:%02d", startHour, startMinute);
        String breakTime = String.format("%02d:%02d", breakHours, breakMinutes);
        String dailyWorkingTimeFormatted = workingday != null
            ? DurationUtils.format(workingday.getEmployeecontract().getDailyWorkingTime())
            : null;

        return new DailyViewData(timereports, totalBooked, workingday, quittingTime, targetEndTime,
            hasTarget, overMaxHours, progressPercent, weekStrip,
            notWorked, startHour, startMinute, startTime, breakHours, breakMinutes, breakTime,
            dailyWorkingTimeFormatted);
    }

    @Transactional(readOnly = true)
    public ListViewData buildListView(YearMonth yearMonth, long employeeContractId) {
        var contract = employeecontractService.getEmployeecontractById(employeeContractId);
        boolean hasTarget = !contract.getDailyWorkingTime().isZero();
        LocalDate first = yearMonth.atDay(1);
        LocalDate last = yearMonth.atEndOfMonth();
        LocalDate today = today();

        List<TimereportDTO> timereports = timereportService.getTimereportsByDatesAndEmployeeContractId(employeeContractId, first, last);
        Map<LocalDate, List<TimereportDTO>> reportsByDate = timereports.stream().collect(groupingBy(TimereportDTO::getReferenceday));

        Map<LocalDate, Workingday> workingdays = workingdayService.getWorkingdaysByEmployeeContractId(employeeContractId, first, last)
            .stream().collect(toMap(Workingday::getRefday, identity()));

        Map<LocalDate, String> holidays = publicholidayService.getPublicHolidaysBetween(first, last)
            .stream().collect(toMap(Publicholiday::getRefdate, Publicholiday::getName));

        List<ListDay> days = first.datesUntil(last.plusDays(1)).map(day -> {
            List<TimereportDTO> dayReports = reportsByDate.getOrDefault(day, List.of());
            Duration dayTotal = dayReports.stream().map(TimereportDTO::getDuration).reduce(Duration.ZERO, Duration::plus);
            boolean isWeekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            boolean isHoliday = holidays.containsKey(day);
            Workingday wd = workingdays.get(day);
            boolean notWorked = wd != null && wd.getType() == Workingday.WorkingDayType.NOT_WORKED;
            return new ListDay(day, dayReports, DurationUtils.format(dayTotal, false), isWeekend, isHoliday, holidays.get(day), notWorked, day.isEqual(today));
        }).collect(Collectors.toList());

        Duration grand = timereports.stream().map(TimereportDTO::getDuration).reduce(Duration.ZERO, Duration::plus);
        String monthTotal = hasTarget ? DurationUtils.format(grand) : null;
        String monthTarget = null;
        String monthDiff = null;
        boolean monthDiffNegative = false;
        if (hasTarget) {
            Duration target = overtimeService.calculateWorkingTimeTarget(employeeContractId, first, last);
            Duration diff = grand.minus(target);
            monthTarget = DurationUtils.format(target);
            monthDiff = (diff.isNegative() ? "" : "+") + DurationUtils.format(diff);
            monthDiffNegative = diff.isNegative();
        }

        boolean monthReleased = contract.getReportReleaseDate() != null
            && !contract.getReportReleaseDate().isBefore(last);

        return new ListViewData(days, monthTotal, monthTarget, monthDiff, monthDiffNegative, hasTarget, monthReleased);
    }

    @Transactional(readOnly = true)
    public List<WeekStripDay> buildWeekStrip(LocalDate date, long employeeContractId) {
        LocalDate monday = date.with(MONDAY);
        LocalDate sunday = monday.plusDays(6);
        LocalDate today = today();

        Map<LocalDate, String> holidays = publicholidayService.getPublicHolidaysBetween(monday, sunday)
            .stream().collect(toMap(Publicholiday::getRefdate, Publicholiday::getName));

        List<TimereportDTO> weekReports = timereportService.getTimereportsByDatesAndEmployeeContractId(employeeContractId, monday, sunday);
        Map<LocalDate, Duration> bookedByDay = weekReports.stream().collect(
            toMap(TimereportDTO::getReferenceday, TimereportDTO::getDuration, Duration::plus));

        Map<LocalDate, Workingday> workingdays = workingdayService.getWorkingdaysByEmployeeContractId(employeeContractId, monday, sunday)
            .stream().collect(toMap(Workingday::getRefday, identity()));

        return monday.datesUntil(sunday.plusDays(1)).map(day -> {
            Duration booked = bookedByDay.getOrDefault(day, Duration.ZERO);
            Workingday wd = workingdays.get(day);
            boolean notWorked = wd != null && wd.getType() == Workingday.WorkingDayType.NOT_WORKED;
            return new WeekStripDay(day, booked, day.isEqual(today), day.isEqual(date), holidays.containsKey(day), holidays.get(day), notWorked);
        }).collect(Collectors.toList());
    }
}
