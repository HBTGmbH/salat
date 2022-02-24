package org.tb.helper;

import static java.util.Calendar.SATURDAY;
import static java.util.Calendar.SUNDAY;
import static org.tb.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.util.DateUtils.today;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.action.dailyreport.AddDailyReportForm;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Overtime;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.DateUtils;

/**
 * Helper class for timereport handling which does not directly deal with persistence
 *
 * @author oda
 */
@Component
@Slf4j
@RequiredArgsConstructor(onConstructor_ = { @Autowired})
public class TimereportHelper {

    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final PublicholidayDAO publicholidayDAO;
    private final OvertimeDAO overtimeDAO;

    /**
     * refreshes hours after change of begin/end times
     */
    public void refreshHours(AddDailyReportForm reportForm) {
        Double hours = reportForm.getHours();
        if (hours < 0.0) {
            reportForm.setSelectedHourDuration(0);
            reportForm.setSelectedMinuteDuration(0);
            return;
        }

        reportForm.setHours(hours);

        //		int hourDuration = hours.intValue();
        //		int minuteDuration = (int) ((hours.doubleValue() - Math.floor(hours.doubleValue()) )*60.);

        // in der alten rechnug gabs einen rundungsfehler, der geschied jezt nicht mehr
        long minuteDurationlong;
        int hourDurationInteger = hours.intValue();
        minuteDurationlong = Math.round((hours - hours.intValue()) * MINUTES_PER_HOUR);
        int minuteDuration = (int) minuteDurationlong;

        // clean possible truncation errors	
        if (minuteDuration % GlobalConstants.MINUTE_INCREMENT != 0) {
            if (minuteDuration % GlobalConstants.MINUTE_INCREMENT > 2.5) {
                minuteDuration += 5 - minuteDuration % GlobalConstants.MINUTE_INCREMENT;
            } else if (minuteDuration % GlobalConstants.MINUTE_INCREMENT < 2.5) {
                minuteDuration -= minuteDuration % GlobalConstants.MINUTE_INCREMENT;
            }
        }

        reportForm.setSelectedHourDuration(hourDurationInteger);
        reportForm.setSelectedMinuteDuration(minuteDuration);
    }

    /**
     * refreshes period after change of hours
     */
    public boolean refreshPeriod(HttpServletRequest request, AddDailyReportForm reportForm) {
        // calculate end hour/minute
        double hours = reportForm.getSelectedHourDuration() + reportForm.getSelectedMinuteDuration() / (double)MINUTES_PER_HOUR;
        reportForm.setHours(hours);
        request.getSession().setAttribute("hourDuration", hours);

        int hoursEnd = reportForm.getSelectedHourBegin() + reportForm.getHours().intValue();
        double dMinutes = (reportForm.getHours() -
                Math.floor(reportForm.getHours())) * MINUTES_PER_HOUR;

        int minutesEnd = reportForm.getSelectedMinuteBegin() + Double.valueOf(dMinutes).intValue();

        //		// clean possible truncation errors
        if (minutesEnd % GlobalConstants.MINUTE_INCREMENT != 0) {
            if (minutesEnd % GlobalConstants.MINUTE_INCREMENT > 2.5) {
                minutesEnd += 5 - minutesEnd % GlobalConstants.MINUTE_INCREMENT;
            } else if (minutesEnd % GlobalConstants.MINUTE_INCREMENT < 2.5) {
                minutesEnd -= minutesEnd % GlobalConstants.MINUTE_INCREMENT;
            }
        }

        if (minutesEnd >= MINUTES_PER_HOUR) {
            minutesEnd -= MINUTES_PER_HOUR;
            hoursEnd++;
        }

        reportForm.setSelectedHourEnd(hoursEnd);
        reportForm.setSelectedMinuteEnd(minutesEnd);

        return true;
    }

    public ActionMessages validateNewDate(
            ActionMessages errors,
            LocalDate theNewDate,
            Timereport timereport,
            Employeecontract loginEmployeeContract,
            boolean authorized) {

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

        if (!loginEmployeeContract.getEmployee().getSign().equals("adm")) {
            if (authorized && loginEmployeeContract.getId() != timereport.getEmployeecontract().getId()) {
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

            //			// clean possible truncation errors
            if (minuteBegin % GlobalConstants.MINUTE_INCREMENT != 0) {
                if (minuteBegin % GlobalConstants.MINUTE_INCREMENT > 2.5) {
                    minuteBegin += 5 - minuteBegin % GlobalConstants.MINUTE_INCREMENT;
                } else if (minuteBegin % GlobalConstants.MINUTE_INCREMENT < 2.5) {
                    minuteBegin -= minuteBegin % GlobalConstants.MINUTE_INCREMENT;
                }
            }
            if (minuteEnd % GlobalConstants.MINUTE_INCREMENT != 0) {
                if (minuteEnd % GlobalConstants.MINUTE_INCREMENT > 2.5) {
                    minuteEnd += 5 - minuteEnd % GlobalConstants.MINUTE_INCREMENT;
                } else if (minuteEnd % GlobalConstants.MINUTE_INCREMENT < 2.5) {
                    minuteEnd -= minuteEnd % GlobalConstants.MINUTE_INCREMENT;
                }
            }
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
    public boolean checkLaborTimeMaximum(List<Timereport> timereports, double maximalDailyLaborTime) {
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

        double laborTime = laborTimeHour + (double)laborTimeMinute / MINUTES_PER_HOUR;
        return laborTime > maximalDailyLaborTime;
    }

    /**
     * @return Returns the sum of the costs of all given timereports.
     */
    public double calculateDailyCosts(List<Timereport> timereports) {
        Double dailycosts = 0.0;
        for (Timereport timereport : timereports) {
            dailycosts += timereport.getCosts();
        }
        return dailycosts;
    }

    /**
     * @return Returns a string with the calculated quitting time (hh:mm). If something fails (may happen for missing workingday, etc.), "n/a" will be returned.
     */
    public String calculateQuittingTime(Workingday workingday, HttpServletRequest request, String timeSwitch) {
        String N_A = "n/a";
        if (workingday == null) return N_A;
        try {
            int timeHoursInt = 0;
            int timeMinutesInt = 0;

            if (timeSwitch.equals("quittingtime")) {
                String labortimeString = (String) request.getSession().getAttribute("labortime");
                String[] laborTimeArray = labortimeString.split(":");
                String laborTimeHoursString = laborTimeArray[0];
                String laborTimeMinutesString = laborTimeArray[1];
                int laborTimeHoursInt = Integer.parseInt(laborTimeHoursString);
                int laborTimeMinutesInt = Integer.parseInt(laborTimeMinutesString);
                timeHoursInt = laborTimeHoursInt;
                timeMinutesInt = laborTimeMinutesInt;
            }
            if (timeSwitch.equals("workingDayEnds")) {
                Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
                Double dailyWorkingTime = employeecontract.getDailyWorkingTime();
                int dailyWorkingTimeHours = dailyWorkingTime.intValue();
                int dailyWorkingTimeMinutes = Integer.parseInt(dailyWorkingTime.toString().replace(".", ":").split(":")[1]) * 6;
                timeHoursInt = dailyWorkingTimeHours;
                timeMinutesInt = dailyWorkingTimeMinutes;
            }

            int quittingtimeHours = workingday.getStarttimehour() + workingday.getBreakhours() + timeHoursInt;
            int quittingtimeMinutes = workingday.getStarttimeminute() + workingday.getBreakminutes() + timeMinutesInt;
            quittingtimeHours += quittingtimeMinutes / MINUTES_PER_HOUR;
            quittingtimeMinutes = quittingtimeMinutes % MINUTES_PER_HOUR;

            // clean possible truncation errors
            if (quittingtimeMinutes % GlobalConstants.MINUTE_INCREMENT != 0) {
                if (quittingtimeMinutes % GlobalConstants.MINUTE_INCREMENT > 2.5) {
                    quittingtimeMinutes += 5 - quittingtimeMinutes % GlobalConstants.MINUTE_INCREMENT;
                } else if (quittingtimeMinutes % GlobalConstants.MINUTE_INCREMENT < 2.5) {
                    quittingtimeMinutes -= quittingtimeMinutes % GlobalConstants.MINUTE_INCREMENT;
                }
            }
            // format return string
            String quittingTime = "";
            if (quittingtimeHours < 10) {
                quittingTime = quittingTime + "0";
            }
            quittingTime = quittingTime + quittingtimeHours + ":";
            if (quittingtimeMinutes < 10) {
                quittingTime = quittingTime + "0";
            }

            return quittingTime + quittingtimeMinutes;
        } catch (Exception e) {
            return N_A;
        }
    }

    /**
     * @return Returns the minutes of overtime, might be negative
     */
    public int calculateOvertimeTotal(Employeecontract employeecontract) {

        LocalDate today = today();

        LocalDate contractBegin = employeecontract.getValidFrom();

        return calculateOvertime(contractBegin, today, employeecontract, true);

    }

    public int calculateOvertime(LocalDate start, LocalDate end, Employeecontract employeecontract, boolean useOverTimeAdjustment) {

        // do not consider invalid(outside of the validity of the contract) days
        if (employeecontract.getValidUntil() != null && end.isAfter(employeecontract.getValidUntil())) {
            end = employeecontract.getValidUntil();
        }

        if (employeecontract.getValidFrom() != null && start.isBefore(employeecontract.getValidFrom())) {
            start = employeecontract.getValidFrom();
        }

        // So = 1
        // Mo = 2
        // Di = 3
        // Mi = 4
        // Do = 5
        // Fr = 6
        // Sa = 7
        int startDayOfWeek = DateUtils.getDayOfWeek(start);

        int numberOfHolidays = 0;
        var holidays = publicholidayDAO.getPublicHolidaysBetween(start, end);
        for (var publicholiday : holidays) {
            var dayOfWeek = DateUtils.getDayOfWeek(publicholiday.getRefdate());
            if (dayOfWeek != SATURDAY && dayOfWeek != SUNDAY) {
                numberOfHolidays += 1;
            }
        }

        var diffDays = DateUtils.getWeekdaysDistance(start, end);
        // substract holidays
        diffDays -= numberOfHolidays;

        // calculate working time
        double dailyWorkingTime = employeecontract.getDailyWorkingTime() * MINUTES_PER_HOUR;
        if (dailyWorkingTime % 1 != 0) {
            throw new RuntimeException("daily working time must be multiple of 0.05: " + employeecontract.getDailyWorkingTime());
        }
        long expectedWorkingTimeInMinutes = (long) dailyWorkingTime * diffDays;
        long actualWorkingTimeInMinutes = 0;
        List<Timereport> reports = timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeecontract.getId(), start, end);
        if (reports != null) {
            for (Timereport timereport : reports) {
                actualWorkingTimeInMinutes += timereport.getDurationhours() * MINUTES_PER_HOUR + timereport.getDurationminutes();
            }
        }

        int overtimeMinutes;
        if (useOverTimeAdjustment && start.equals(employeecontract.getValidFrom())) {
            long overtimeAdjustmentMinutes = 0;
            List<Overtime> overtimes = overtimeDAO
                    .getOvertimesByEmployeeContractId(employeecontract.getId());
            for (Overtime ot : overtimes) {
                overtimeAdjustmentMinutes += ot.getTime() * MINUTES_PER_HOUR;
            }
            overtimeMinutes = (int) (actualWorkingTimeInMinutes - expectedWorkingTimeInMinutes + overtimeAdjustmentMinutes);
        } else {
            overtimeMinutes = (int) (actualWorkingTimeInMinutes - expectedWorkingTimeInMinutes);
        }

        if (end.isAfter(start) || end.isEqual(start)) {
            return overtimeMinutes;
        } else {
            //startdate > enddate, should only happen when reopened on day of contractbegin (because then, enddate is set to (contractbegin - 1))
            return 0;
        }
    }

}
