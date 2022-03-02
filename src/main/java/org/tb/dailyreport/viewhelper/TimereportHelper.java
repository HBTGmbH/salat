package org.tb.dailyreport.viewhelper;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.AddDailyReportForm;
import org.tb.dailyreport.Publicholiday;
import org.tb.dailyreport.PublicholidayDAO;
import org.tb.dailyreport.Timereport;
import org.tb.dailyreport.TimereportDAO;
import org.tb.dailyreport.Workingday;
import org.tb.employee.Employeecontract;
import org.tb.employee.Overtime;
import org.tb.employee.OvertimeDAO;
import org.tb.order.Employeeorder;
import org.tb.order.EmployeeorderDAO;

/**
 * Helper class for timereport handling which does not directly deal with persistence
 *
 * @author oda
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TimereportHelper {

    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final PublicholidayDAO publicholidayDAO;
    private final OvertimeDAO overtimeDAO;
    private final AuthorizedUser authorizedUser;

    /**
     * refreshes hours after change of begin/end times
     */
    public void refreshHours(AddDailyReportForm reportForm) {
        reportForm.recalcDurationFromBeginAndEnd();
    }

    /**
     * refreshes period after change of hours
     */
    public boolean refreshPeriod(HttpServletRequest request, AddDailyReportForm reportForm) {
        reportForm.recalcEndFromBeginAndDuration();
        return true;
    }

    public ActionMessages validateNewDate(
            ActionMessages errors,
            LocalDate theNewDate,
            Timereport timereport,
            Employeecontract loginEmployeeContract) {

        // check date range (must be in current or previous year)
        if (DateUtils.getCurrentYear() - DateUtils.getYear(theNewDate).getValue() >= 2) {
            errors.add("referenceday", new ActionMessage("form.timereport.error.date.invalidyear"));
        }

        // if sort of report is not 'W' reports are only allowed for workdays
        // e.g., vacation cannot be set on a Sunday
        boolean valid = DateUtils.isWeekday(theNewDate);

        // checks for public holidays
        if (valid) {
            Optional<Publicholiday> publicHoliday = publicholidayDAO.getPublicHoliday(theNewDate);
            if (publicHoliday.isPresent()) {
                valid = false;
            }
        }

        // check date vs release status
        Employeecontract employeecontract = timereport.getEmployeecontract();
        LocalDate releaseDate = employeecontract.getReportReleaseDate();
        if (releaseDate == null) {
            releaseDate = employeecontract.getValidFrom();
        }
        LocalDate acceptanceDate = employeecontract.getReportAcceptanceDate();
        if (acceptanceDate == null) {
            acceptanceDate = employeecontract.getValidFrom();
        }

        // check, if refDate is first day
        boolean firstday = false;
        if (!releaseDate.isAfter(employeecontract.getValidFrom()) &&
                !theNewDate.isAfter(employeecontract.getValidFrom())) {
            firstday = true;
        }

        if (!authorizedUser.isAdmin()) {
            if (authorizedUser.isManager() && !Objects.equals(loginEmployeeContract.getId(), timereport.getEmployeecontract().getId())) {
                if (releaseDate.isBefore(theNewDate) || firstday) {
                    errors.add("release", new ActionMessage("form.timereport.error.not.released"));
                }
            } else {
                if (!releaseDate.isBefore(theNewDate) && !firstday) {
                    errors.add("release", new ActionMessage("form.timereport.error.released"));
                }
            }
            if (!theNewDate.isAfter(acceptanceDate) && !firstday) {
                errors.add("release", new ActionMessage("form.timereport.error.accepted"));
            }
        }

        // check for adequate employee order
        List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(timereport.getEmployeecontract().getId(), timereport.getSuborder().getId(), theNewDate);
        if (employeeorders == null || employeeorders.isEmpty()) {
            errors.add("employeeorder", new ActionMessage("form.timereport.error.employeeorder.notfound"));
        } else if (employeeorders.size() > 1) {
            errors.add("employeeorder", new ActionMessage("form.timereport.error.employeeorder.multiplefound"));
        }

        return errors;
    }

    /**
     * @return Returns the working time for one day as an int array with length 2. The hours are at index[0], the minutes at index[1].
     */
    private int[] getWorkingTimeForDateAndEmployeeContract(LocalDate date, long employeeContractId) {
        int[] workingTime = new int[2];
        List<Timereport> timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContractId, date);
        int hours = 0;
        int minutes = 0;
        for (Timereport timereport : timereports) {
            hours += timereport.getDurationhours();
            minutes += timereport.getDurationminutes();
        }
        hours += minutes / MINUTES_PER_HOUR;
        minutes = minutes % MINUTES_PER_HOUR;
        workingTime[0] = hours;
        workingTime[1] = minutes;

        return workingTime;
    }

    /**
     * @return Returns int[]  0=hours 1=minutes
     */
    public int[] determineBeginTimeToDisplay(long ecId, LocalDate date, Workingday workingday) {
        int[] beginTime = getWorkingTimeForDateAndEmployeeContract(date, ecId);
        if (workingday != null) {
            beginTime[0] += workingday.getStarttimehour();
            beginTime[1] += workingday.getStarttimeminute();
            beginTime[0] += workingday.getBreakhours();
            beginTime[1] += workingday.getBreakminutes();
            beginTime[0] += beginTime[1] / MINUTES_PER_HOUR;
            beginTime[1] = beginTime[1] % MINUTES_PER_HOUR;
        }
        return beginTime;
    }

    public int[] determineTimesToDisplay(long ecId, LocalDate date, Workingday workingday, Timereport tr) {
        List<Timereport> timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, date);
        if (workingday != null) {
            int hourBegin = workingday.getStarttimehour();
            int minuteBegin = workingday.getStarttimeminute();
            hourBegin += workingday.getBreakhours();
            minuteBegin += workingday.getBreakminutes();
            for (Timereport timereport : timereports) {
                if (Objects.equals(timereport.getId(), tr.getId())) {
                    break;
                }
                hourBegin += timereport.getDurationhours();
                minuteBegin += timereport.getDurationminutes();
            }
            int hourEnd = hourBegin + tr.getDurationhours();
            int minuteEnd = minuteBegin + tr.getDurationminutes();
            hourBegin += minuteBegin / MINUTES_PER_HOUR;
            minuteBegin = minuteBegin % MINUTES_PER_HOUR;
            hourEnd += minuteEnd / MINUTES_PER_HOUR;
            minuteEnd = minuteEnd % MINUTES_PER_HOUR;

            return new int[]{hourBegin, minuteBegin, hourEnd, minuteEnd};
        } else {
            return new int[4];
        }
    }

    /**
     * Calculates the overall labortime for a list of {@link Timereport}s.
     *
     * @return Returns the calculated time as String (hh:mm)
     */
    public String calculateLaborTime(List<Timereport> timereports) {
        int[] labortime = calculateLaborTimeAsArray(timereports);
        int laborTimeHour = labortime[0];
        int laborTimeMinute = labortime[1];

        String laborTimeString;
        if (laborTimeHour < 10) {
            laborTimeString = "0" + laborTimeHour + ":";
        } else {
            laborTimeString = laborTimeHour + ":";
        }
        if (laborTimeMinute < 10) {
            return laborTimeString + "0" + laborTimeMinute;
        } else {
            return laborTimeString + laborTimeMinute;
        }
    }

    /**
     * Calculates the overall labortime for a list of {@link Timereport}s.
     *
     * @return Returns the calculated time as int[] (index 0: hours, index 1: minutes)
     */
    public int[] calculateLaborTimeAsArray(List<Timereport> timereports) {
        int[] labortime = new int[2];
        int laborTimeHour = 0;
        int laborTimeMinute = 0;

        for (Timereport timereport : timereports) {

            int hours = timereport.getDurationhours();
            int minutes = timereport.getDurationminutes();

            laborTimeHour += hours;
            laborTimeMinute += minutes;
        }
        laborTimeHour += laborTimeMinute / MINUTES_PER_HOUR;
        laborTimeMinute = laborTimeMinute % MINUTES_PER_HOUR;
        labortime[0] = laborTimeHour;
        labortime[1] = laborTimeMinute;
        return labortime;
    }

    /**
     * Checks, if the overall labortime for a list of {@link Timereport}s extends the maximal daily labor time.
     *
     * @return Returns true, if the maximal labor time is extended, false otherwise
     */
    public boolean checkLaborTimeMaximum(List<Timereport> timereports, int maxDailyLaborTimeHours) {

        Duration actual = timereports.stream()
            .map(t -> Duration.ofHours(t.getDurationhours()).plusMinutes(t.getDurationminutes()))
            .reduce(Duration.ZERO, Duration::plus);

        // check actual is not greater than the max labor time
        return !Duration.ofHours(maxDailyLaborTimeHours).minus(actual).isNegative();
    }

    /**
     * @return Returns a string with the calculated quitting time (hh:mm). If something fails (may happen for missing workingday, etc.), "n/a" will be returned.
     */
    public String calculateQuittingTime(Workingday workingday, HttpServletRequest request, String timeSwitch) {
        String N_A = "n/a";
        if (workingday == null) return N_A;
        try {
            long timeHoursLong = 0;
            int timeMinutesInt = 0;

            if (timeSwitch.equals("quittingtime")) {
                String labortimeString = (String) request.getSession().getAttribute("labortime");
                String[] laborTimeArray = labortimeString.split(":");
                String laborTimeHoursString = laborTimeArray[0];
                String laborTimeMinutesString = laborTimeArray[1];
                timeHoursLong = Long.parseLong(laborTimeHoursString);
                timeMinutesInt = Integer.parseInt(laborTimeMinutesString);
            }
            if (timeSwitch.equals("workingDayEnds")) {
                Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
                timeHoursLong = employeecontract.getDailyWorkingTime().toHours();
                timeMinutesInt = employeecontract.getDailyWorkingTime().toMinutesPart();
            }

            long quittingtimeHours = workingday.getStarttimehour() + workingday.getBreakhours() + timeHoursLong;
            int quittingtimeMinutes = workingday.getStarttimeminute() + workingday.getBreakminutes() + timeMinutesInt;
            quittingtimeHours += quittingtimeMinutes / MINUTES_PER_HOUR;
            quittingtimeMinutes = quittingtimeMinutes % MINUTES_PER_HOUR;

            // format return string
            StringBuilder quittingTime = new StringBuilder();
            if (quittingtimeHours < 10) {
                quittingTime.append("0");
            }
            quittingTime.append(quittingtimeHours);
            quittingTime.append(":");
            if (quittingtimeMinutes < 10) {
                quittingTime.append("0");
            }
            quittingTime.append(quittingtimeMinutes);
            return quittingTime.toString();
        } catch (Exception e) {
            return N_A;
        }
    }

    public long calculateOvertime(LocalDate start, LocalDate end, Employeecontract employeecontract, boolean useOverTimeAdjustment) {

        // do not consider invalid(outside of the validity of the contract) days
        if (employeecontract.getValidUntil() != null && end.isAfter(employeecontract.getValidUntil())) {
            end = employeecontract.getValidUntil();
        }

        if (employeecontract.getValidFrom() != null && start.isBefore(employeecontract.getValidFrom())) {
            start = employeecontract.getValidFrom();
        }

        int numberOfHolidays = 0;
        var holidays = publicholidayDAO.getPublicHolidaysBetween(start, end);
        for (var publicholiday : holidays) {
            var dayOfWeek = publicholiday.getRefdate().getDayOfWeek();
            if (dayOfWeek != SATURDAY && dayOfWeek != SUNDAY) {
                numberOfHolidays += 1;
            }
        }

        var diffDays = DateUtils.getWeekdaysDistance(start, end);
        // substract holidays
        diffDays -= numberOfHolidays;

        // calculate working time
        long dailyWorkingTime = employeecontract.getDailyWorkingTime().toMinutes();
        long expectedWorkingTimeInMinutes = (long) dailyWorkingTime * diffDays;
        long actualWorkingTimeInMinutes = 0;
        List<Timereport> reports = timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeecontract.getId(), start, end);
        if (reports != null) {
            for (Timereport timereport : reports) {
                actualWorkingTimeInMinutes += timereport.getDurationhours() * MINUTES_PER_HOUR + timereport.getDurationminutes();
            }
        }

        long overtimeMinutes;
        if (useOverTimeAdjustment && start.equals(employeecontract.getValidFrom())) {
            long overtimeAdjustmentMinutes = 0;
            List<Overtime> overtimes = overtimeDAO
                    .getOvertimesByEmployeeContractId(employeecontract.getId());
            for (Overtime ot : overtimes) {
                overtimeAdjustmentMinutes += ot.getTimeMinutes().toMinutes();
            }
            overtimeMinutes = actualWorkingTimeInMinutes - expectedWorkingTimeInMinutes + overtimeAdjustmentMinutes;
        } else {
            overtimeMinutes = actualWorkingTimeInMinutes - expectedWorkingTimeInMinutes;
        }

        if (end.isAfter(start) || end.isEqual(start)) {
            return overtimeMinutes;
        } else {
            //startdate > enddate, should only happen when reopened on day of contractbegin (because then, enddate is set to (contractbegin - 1))
            return 0;
        }
    }

}
