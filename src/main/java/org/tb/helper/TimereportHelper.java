package org.tb.helper;

import static org.tb.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.GlobalConstants.SORT_OF_REPORT_WORK;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Overtime;
import org.tb.bdom.Publicholiday;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.form.AddDailyReportForm;
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

        int minutesEnd = reportForm.getSelectedMinuteBegin() + new Double(dMinutes).intValue();

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
            Date theNewDate,
            Timereport timereport,
            Employeecontract loginEmployeeContract,
            boolean authorized) {

        // check date range (must be in current or previous year)
        if (DateUtils.getCurrentYear() - DateUtils.getYear(theNewDate).getValue() >= 2) {
            errors.add("referenceday", new ActionMessage("form.timereport.error.date.invalidyear"));
        }

        // check if report types for one day are unique and if there is no time overlap with other work reports
        List<Timereport> dailyReports = timereportDAO.getTimereportsByDateAndEmployeeContractId(timereport.getEmployeecontract().getId(), theNewDate);
        if (dailyReports != null && dailyReports.size() > 0) {
            for (Timereport tr : dailyReports) {
                if (tr.getId() != timereport.getId()) { // do not check report against itself in case of edit
                    // uniqueness of types
                    // actually not checked - e.g., combination of sickness and work on ONE day should be valid
                    // but: vacation or sickness MUST occur only once per day
                    if (!timereport.getSortofreport().equals(SORT_OF_REPORT_WORK) && !tr.getSortofreport().equals(SORT_OF_REPORT_WORK)) {
                        errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.special.alreadyexisting"));
                        break;
                    }
                }
            }
        }

        // if sort of report is not 'W' reports are only allowed for workdays
        // e.g., vacation cannot be set on a Sunday
        if (!timereport.getSortofreport().equals(SORT_OF_REPORT_WORK)) {
            boolean valid = DateUtils.isWeekday(theNewDate);

            // checks for public holidays
            if (valid) {
                Optional<Publicholiday> publicHoliday = publicholidayDAO.getPublicHoliday(theNewDate);
                if (publicHoliday.isPresent()) {
                    valid = false;
                }
            }

            if (!valid) {
                errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.invalidday"));
            } else {
                // for new report, check if other reports already exist for selected day
                if (timereport.getId() == -1) {
                    List<Timereport> allReports = timereportDAO.getTimereportsByDateAndEmployeeContractId(timereport.getEmployeecontract().getId(), theNewDate);
                    if (allReports.size() > 0) {
                        errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.othersexisting"));
                    }
                }
            }

        }

        // check date vs release status
        Employeecontract employeecontract = timereport.getEmployeecontract();
        Date releaseDate = employeecontract.getReportReleaseDate();
        if (releaseDate == null) {
            releaseDate = employeecontract.getValidFrom();
        }
        Date acceptanceDate = employeecontract.getReportAcceptanceDate();
        if (acceptanceDate == null) {
            acceptanceDate = employeecontract.getValidFrom();
        }

        // check, if refDate is first day
        boolean firstday = false;
        if (!releaseDate.after(employeecontract.getValidFrom()) &&
                !theNewDate.after(employeecontract.getValidFrom())) {
            firstday = true;
        }

        if (!loginEmployeeContract.getEmployee().getSign().equals("adm")) {
            if (authorized && loginEmployeeContract.getId() != timereport.getEmployeecontract().getId()) {
                if (releaseDate.before(theNewDate) || firstday) {
                    errors.add("release", new ActionMessage("form.timereport.error.not.released"));
                }
            } else {
                if (!releaseDate.before(theNewDate) && !firstday) {
                    errors.add("release", new ActionMessage("form.timereport.error.released"));
                }
            }
            if (!theNewDate.after(acceptanceDate) && !firstday) {
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
    private int[] getWorkingTimeForDateAndEmployeeContract(Date date, long employeeContractId) {
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
    public int[] determineBeginTimeToDisplay(long ecId, Date date, Workingday workingday) {
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

    public int[] determineTimesToDisplay(long ecId, Date date, Workingday workingday, Timereport tr) {
        List<Timereport> timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, date);
        if (workingday != null) {
            int hourBegin = workingday.getStarttimehour();
            int minuteBegin = workingday.getStarttimeminute();
            hourBegin += workingday.getBreakhours();
            minuteBegin += workingday.getBreakminutes();
            for (Timereport timereport : timereports) {
                if (timereport.getId() == tr.getId()) {
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

        Date today = new Date();

        Date contractBegin = employeecontract.getValidFrom();

        return calculateOvertime(contractBegin, today, employeecontract, true);

    }

    public int calculateOvertime(Date start, Date end, Employeecontract employeecontract, boolean useOverTimeAdjustment) {

        // do not consider invalid(outside of the validity of the contract) days
        if (employeecontract.getValidUntil() != null && end.after(employeecontract.getValidUntil()))
            end = employeecontract.getValidUntil();
        if (employeecontract.getValidFrom() != null && start.before(employeecontract.getValidFrom()))
            start = employeecontract.getValidFrom();

        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
        String year = yearFormat.format(start);

        SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
        String month = monthFormat.format(start);
        int monthIntValue = Integer.parseInt(month);

        SimpleDateFormat dayFormat = new SimpleDateFormat("dd");
        String day = dayFormat.format(start);

        GregorianCalendar calendar = new GregorianCalendar();

        calendar.clear();
        calendar.set(new Integer(year), monthIntValue - 1, new Integer(day));

        // So = 1
        // Mo = 2
        // Di = 3
        // Mi = 4
        // Do = 5
        // Fr = 6
        // Sa = 7
        int firstday = calendar.get(Calendar.DAY_OF_WEEK);

        int numberOfHolidays = 0;

        List<Publicholiday> holidays = publicholidayDAO.getPublicHolidaysBetween(start, end);
        for (Publicholiday publicholiday : holidays) {
            calendar.setTimeInMillis(publicholiday.getRefdate().getTime());
            if (calendar.get(Calendar.DAY_OF_WEEK) != 1 && calendar.get(Calendar.DAY_OF_WEEK) != 7) {
                numberOfHolidays += 1;
            }
        }

        long diffMillis;
        long diffDays;

        diffMillis = end.getTime() - start.getTime();
        diffDays = (diffMillis + 60 * 60 * 1000) / (24 * 60 * 60 * 1000);
        // 1 hour added because of possible differences caused by sommertime/wintertime

        // add 1 day (number of days are needed, not the difference)
        diffDays += 1;

        if (diffDays < 0) {
            // throw new RuntimeException("implementation error while calculating overtime");
            return 0;
        }
        long weeks = diffDays / 7; // how many complete weeks?
        long days = diffDays % 7; // days of incomplete week
        diffDays = diffDays - weeks * 2; // subtract weekends of complete weeks

        // check weekdays of incomplete week
        if (days > 0) {
            if (firstday == 1) {
                // firstday is a sunday
                diffDays -= 1;
            } else {
                if (firstday + days == 8) {
                    diffDays -= 1;
                } else if (firstday + days > 8) {
                    diffDays -= 2;
                }
            }
        }

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

        if (end.getTime() >= start.getTime()) {
            return overtimeMinutes;
        } else {
            //startdate > enddate, should only happen when reopened on day of contractbegin (because then, enddate is set to (contractbegin - 1))
            return 0;
        }
    }

    public List<Date> getDatesForTimePeriod(Date startDate, int numberOfLaborDays) {
        List<Date> dates = new ArrayList<>(numberOfLaborDays);
        GregorianCalendar calendar = new GregorianCalendar();
        int loopcounter = 0;
        for (int i = 0; i < numberOfLaborDays; i++) {
            calendar.clear();
            calendar.setTime(startDate);
            int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
            calendar.set(Calendar.DAY_OF_YEAR, dayOfYear + loopcounter);
            loopcounter++;
            int weekday = calendar.get(Calendar.DAY_OF_WEEK);
            if (weekday != 1 && weekday != 7) {
                // weekday is no sa, su
                Date laborDay = calendar.getTime();
                Optional<Publicholiday> publicholiday = publicholidayDAO.getPublicHoliday(laborDay);
                if (!publicholiday.isPresent()) {
                    // labor day is not a holiday
                    dates.add(laborDay);
                } else {
                    i--;
                }
            } else {
                i--;
            }
        }
        return dates;
    }
}
