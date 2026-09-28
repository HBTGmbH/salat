package de.hbt.salat.dailyreport.service;

import static java.time.DayOfWeek.MONDAY;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static de.hbt.salat.common.util.DateUtils.isInRange;
import static de.hbt.salat.common.util.DateUtils.today;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.DailyViewData;
import de.hbt.salat.dailyreport.domain.DailyViewData.WeekStripDay;
import de.hbt.salat.dailyreport.domain.ListViewData;
import de.hbt.salat.dailyreport.domain.ListViewData.ListDay;
import de.hbt.salat.dailyreport.domain.Publicholiday;
import de.hbt.salat.dailyreport.domain.ReportPeriod;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.employee.service.EmployeecontractService;

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
    private final TimereportAuthorization timereportAuthorization;

    @Transactional(readOnly = true)
    public DailyViewData buildDailyView(LocalDate date, long employeeContractId) {
        var contract = employeecontractService.getEmployeecontractById(employeeContractId);
        boolean hasTarget = !contract.getDailyWorkingTime().isZero();

        // #857: the target of a single day is not simply the contract's daily working time -
        // weekends, public holidays falling on a weekday and days outside the contract's validity
        // carry no target at all. This is the same calculation the month sum in buildListView
        // uses, called for a single day.
        Duration dayTarget = overtimeService.calculateWorkingTimeTarget(employeeContractId, date, date);
        boolean hasDayTarget = !dayTarget.isZero();

        List<TimereportDTO> timereports = timereportService.getTimereportsByDateAndEmployeeContractId(employeeContractId, date);
        // standby is booked on this day like any other time, but it is no working time and therefore
        // part of no sum here - not of the total, not of the progress, not of the quitting time (#463)
        Duration totalBooked = timereports.stream().map(TimereportDTO::getWorkingTime).reduce(Duration.ZERO, Duration::plus);
        Workingday workingday = workingdayService.getWorkingday(employeeContractId, date);

        // the quitting time follows from what has been booked, not from a target - it is useful on a
        // day without one too, so it stays (#857)
        String quittingTime = workingdayService.calculateQuittingTime(employeeContractId, date);
        String targetEndTime = hasDayTarget ? workingdayService.calculateWorkingDayEnds(employeeContractId, date) : null;
        boolean overMaxHours = workingdayService.checkLaborTimeMaximum(timereports);

        long targetMinutes = dayTarget.toMinutes();
        int progressPercent = targetMinutes > 0 ? (int) Math.min(100, totalBooked.toMinutes() * 100 / targetMinutes) : 0;

        List<WeekStripDay> weekStrip = buildWeekStrip(date, employeeContractId);

        boolean notWorked = workingday != null && workingday.getType() == Workingday.WorkingDayType.NOT_WORKED;
        var effectiveStart = workingdayService.getEffectiveStart(workingday, employeeContractId);
        String startTime = String.format("%02d:%02d", effectiveStart.getHour(), effectiveStart.getMinute());
        String breakTime = workingday != null
            ? String.format("%02d:%02d", workingday.getBreakhours(), workingday.getBreakminutes())
            : "00:00";
        String dailyWorkingTimeFormatted = hasDayTarget
            ? DurationUtils.format(dayTarget)
            : null;

        Set<Long> editableIds = timereports.stream()
            .filter(tr -> timereportAuthorization.isWriteAllowed(contract, tr.getStatus()))
            .map(TimereportDTO::getId)
            .collect(toSet());
        // the working day and a new booking follow the rule of the day they belong to, the same the
        // saving applies (#1164) - offered is only what can be saved
        boolean canWriteDay = timereportAuthorization.isWriteAllowedOn(contract, date);
        boolean workingdayEditable = canWriteDay;
        boolean canCreate = canWriteDay;

        return new DailyViewData(timereports, totalBooked, workingday, quittingTime, targetEndTime,
            hasTarget, hasDayTarget, overMaxHours, progressPercent, weekStrip,
            notWorked, startTime, breakTime, dailyWorkingTimeFormatted,
            editableIds, workingdayEditable, canCreate, ReportPeriod.statusOn(contract, date));
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
            Duration dayTotal = dayReports.stream().map(TimereportDTO::getWorkingTime).reduce(Duration.ZERO, Duration::plus);
            boolean isWeekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            boolean isHoliday = holidays.containsKey(day);
            Workingday wd = workingdays.get(day);
            boolean notWorked = wd != null && wd.getType() == Workingday.WorkingDayType.NOT_WORKED;
            return new ListDay(day, dayReports, DurationUtils.format(dayTotal, false), isWeekend, isHoliday, holidays.get(day), notWorked, day.isEqual(today),
                timereportAuthorization.isWriteAllowedOn(contract, day));
        }).collect(Collectors.toList());

        Duration grand = timereports.stream().map(TimereportDTO::getWorkingTime).reduce(Duration.ZERO, Duration::plus);
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

        String prevDayDiffString = null;
        boolean prevDayDiffNegative = false;
        if (hasTarget && isInRange(today, first, last)) {
            var cutoff = today.minusDays(1);
            if (!cutoff.isBefore(first)) {
                Duration grandPrevDay = timereports.stream()
                    .filter(r -> !r.getReferenceday().isAfter(cutoff))
                    .map(TimereportDTO::getWorkingTime)
                    .reduce(Duration.ZERO, Duration::plus);
                Duration targetPrevDay = overtimeService.calculateWorkingTimeTarget(employeeContractId, first, cutoff);
                Duration prevDayDiff = grandPrevDay.minus(targetPrevDay);
                prevDayDiffString = (prevDayDiff.isNegative() ? "" : "+") + DurationUtils.format(prevDayDiff);
                prevDayDiffNegative = prevDayDiff.isNegative();
            }
        }

        boolean monthReleased = contract.getReportReleaseDate() != null
            && !contract.getReportReleaseDate().isBefore(last);

        Set<Long> editableIds = timereports.stream()
            .filter(tr -> timereportAuthorization.isWriteAllowed(contract, tr.getStatus()))
            .map(TimereportDTO::getId)
            .collect(toSet());

        return new ListViewData(days, monthTotal, monthTarget, monthDiff, monthDiffNegative, prevDayDiffString, prevDayDiffNegative, hasTarget, monthReleased, editableIds,
            ReportPeriod.Month.of(contract, yearMonth));
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
            toMap(TimereportDTO::getReferenceday, TimereportDTO::getWorkingTime, Duration::plus));

        Map<LocalDate, Long> countByDay = weekReports.stream().collect(
            Collectors.groupingBy(TimereportDTO::getReferenceday, Collectors.counting()));

        Map<LocalDate, Workingday> workingdays = workingdayService.getWorkingdaysByEmployeeContractId(employeeContractId, monday, sunday)
            .stream().collect(toMap(Workingday::getRefday, identity()));

        return monday.datesUntil(sunday.plusDays(1)).map(day -> {
            Duration booked = bookedByDay.getOrDefault(day, Duration.ZERO);
            int count = countByDay.getOrDefault(day, 0L).intValue();
            Workingday wd = workingdays.get(day);
            boolean notWorked = wd != null && wd.getType() == Workingday.WorkingDayType.NOT_WORKED;
            return new WeekStripDay(day, booked, count, day.isEqual(today), day.isEqual(date), holidays.containsKey(day), holidays.get(day), notWorked);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    /**
     * The status rule of {@link TimereportAuthorization}, the one saving applies. It used to be a copy
     * of its own here, and the copy let managers edit accepted bookings after saving no longer did (#1164).
     */
    public boolean isTimereportEditable(TimereportDTO tr, long employeeContractId) {
        var contract = employeecontractService.getEmployeecontractById(employeeContractId);
        return timereportAuthorization.isWriteAllowed(contract, tr.getStatus());
    }

}
