package org.tb.dailyreport.viewhelper;

import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.action.AddDailyReportForm;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

/**
 * Helper class for timereport handling which does not directly deal with persistence
 *
 * @author oda
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TimereportHelper {

    private final TimereportService timereportService;
    private final EmployeeorderService employeeorderService;
    private final AuthorizedUser authorizedUser;
    private final EmployeecontractService employeecontractService;

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
            TimereportDTO timereport,
            Employeecontract loginEmployeeContract) {

        // check date range (must be in current or previous year)
        if (DateUtils.getCurrentYear() - DateUtils.getYear(theNewDate).getValue() >= 2) {
            errors.add("referenceday", new ActionMessage("form.timereport.error.date.invalidyear"));
        }

        // check date vs release status
        Employeecontract employeecontract = employeecontractService.getEmployeecontractById(timereport.getEmployeecontractId());
        LocalDate releaseDate = employeecontract.getReportReleaseDate();
        LocalDate acceptanceDate = employeecontract.getReportAcceptanceDate();

        if (!authorizedUser.isAdmin()) {
            if (authorizedUser.isManager() && !Objects.equals(loginEmployeeContract.getId(), timereport.getEmployeecontractId())) {
                if (releaseDate != null && releaseDate.isBefore(theNewDate)) {
                    errors.add("release", new ActionMessage("form.timereport.error.not.released"));
                }
            } else {
                if (releaseDate != null &&  !releaseDate.isBefore(theNewDate)) {
                    errors.add("release", new ActionMessage("form.timereport.error.released"));
                }
            }
            if (acceptanceDate != null && !theNewDate.isAfter(acceptanceDate)) {
                errors.add("release", new ActionMessage("form.timereport.error.accepted"));
            }
        }

        // check for adequate employee order
        List<Employeeorder> employeeorders = employeeorderService.getEmployeeOrderByEmployeeContractIdAndSuborderIdAndValidAt(timereport.getEmployeecontractId(), timereport.getSuborderId(), theNewDate);
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
    private long[] getWorkingTimeForDateAndEmployeeContract(LocalDate date, long employeeContractId) {
        long[] workingTime = new long[2];
        List<TimereportDTO> timereports = timereportService.getTimereportsByDateAndEmployeeContractId(employeeContractId, date);
        long hours = 0;
        long minutes = 0;
        for (TimereportDTO timereport : timereports) {
            hours += timereport.getDuration().toHours();
            minutes += timereport.getDuration().toMinutesPart();
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
    public long[] determineBeginTimeToDisplay(long ecId, LocalDate date, Workingday workingday) {
        long[] beginTime = getWorkingTimeForDateAndEmployeeContract(date, ecId);
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

    public long[] determineTimesToDisplay(long ecId, LocalDate date, Workingday workingday, TimereportDTO tr) {
        List<TimereportDTO> timereports = timereportService.getTimereportsByDateAndEmployeeContractId(ecId, date);
        if (workingday != null) {
            long hourBegin = workingday.getStarttimehour();
            long minuteBegin = workingday.getStarttimeminute();
            hourBegin += workingday.getBreakhours();
            minuteBegin += workingday.getBreakminutes();
            for (TimereportDTO timereport : timereports) {
                if (Objects.equals(timereport.getId(), tr.getId())) {
                    break;
                }
                hourBegin += timereport.getDuration().toHours();
                minuteBegin += timereport.getDuration().toMinutesPart();
            }
            long hourEnd = hourBegin + tr.getDuration().toHours();
            long minuteEnd = minuteBegin + tr.getDuration().toMinutesPart();
            hourBegin += minuteBegin / MINUTES_PER_HOUR;
            minuteBegin = minuteBegin % MINUTES_PER_HOUR;
            hourEnd += minuteEnd / MINUTES_PER_HOUR;
            minuteEnd = minuteEnd % MINUTES_PER_HOUR;

            return new long[]{hourBegin, minuteBegin, hourEnd, minuteEnd};
        } else {
            return new long[4];
        }
    }

    /**
     * Calculates the overall labortime for a list of {@link Timereport}s.
     *
     * @return Returns the calculated time as String (hh:mm)
     */
    public String calculateLaborTime(List<TimereportDTO> timereports) {
        long[] labortime = calculateLaborTimeAsArray(timereports);
        long laborTimeHour = labortime[0];
        long laborTimeMinute = labortime[1];

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
    public long[] calculateLaborTimeAsArray(List<TimereportDTO> timereports) {
        long[] labortime = new long[2];
        long laborTimeHour = 0;
        long laborTimeMinute = 0;

        for (TimereportDTO timereport : timereports) {

            long hours = timereport.getDuration().toHours();
            long minutes = timereport.getDuration().toMinutesPart();

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
    public boolean checkLaborTimeMaximum(List<TimereportDTO> timereports, int maxDailyLaborTimeHours) {

        Duration actual = timereports.stream()
            .map(TimereportDTO::getDuration)
            .reduce(Duration.ZERO, Duration::plus);

        // check actual is not greater than the max labor time
        return Duration.ofHours(maxDailyLaborTimeHours).minus(actual).isNegative();
    }

    public String calculateWorkingDayEnds(Workingday workingday, HttpServletRequest request) {
        if (workingday == null) return "n/a";
        Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        long timeHoursLong = employeecontract.getDailyWorkingTime().toHours();
        long timeMinutesInt = employeecontract.getDailyWorkingTime().toMinutesPart();
        return calculateEndTime(workingday, timeHoursLong, timeMinutesInt);
    }

    /**
     * @return Returns a string with the calculated quitting time (hh:mm). If something fails (may happen for missing workingday, etc.), "n/a" will be returned.
     */
    public String calculateQuittingTime(Workingday workingday, HttpServletRequest request) {
        if (workingday == null) return "n/a";
        String labortimeString = (String) request.getSession().getAttribute("labortime");
        String[] laborTimeArray = labortimeString.split(":");
        String laborTimeHoursString = laborTimeArray[0];
        String laborTimeMinutesString = laborTimeArray[1];
        long timeHoursLong = Long.parseLong(laborTimeHoursString);
        long timeMinutesInt = Integer.parseInt(laborTimeMinutesString);
        return calculateEndTime(workingday, timeHoursLong, timeMinutesInt);
    }

    private static String calculateEndTime(Workingday workingday, long timeHoursLong, long timeMinutesInt) {
        try {
            long endTimeHours = workingday.getStarttimehour() + workingday.getBreakhours() + timeHoursLong;
            long endtimeMinutes = workingday.getStarttimeminute() + workingday.getBreakminutes() + timeMinutesInt;
            endTimeHours += endtimeMinutes / MINUTES_PER_HOUR;
            endtimeMinutes = endtimeMinutes % MINUTES_PER_HOUR;

            // format return string
            StringBuilder endTimeString = new StringBuilder();
            if (endTimeHours < 10) {
                endTimeString.append("0");
            }
            if (endTimeHours >= 24) {
                endTimeHours = endTimeHours % 24;
            }
            endTimeString.append(endTimeHours);
            endTimeString.append(":");
            if (endtimeMinutes < 10) {
                endTimeString.append("0");
            }
            endTimeString.append(endtimeMinutes);
            return endTimeString.toString();
        } catch (Exception e) {
            log.error("Could not calculate quitting time.", e);
            return "n/a";
        }
    }

}
