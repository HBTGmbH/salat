package org.tb.dailyreport.viewhelper.matrix;

import java.time.Duration;
import java.time.LocalDate;

import lombok.Data;
import org.tb.common.util.DurationUtils;

import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

@Data
public class DayAndWorkingHourCount {
    private int day;
    private Duration workingHour;
    private LocalDate date;
    private boolean publicHoliday;
    private boolean satSun;
    private String weekDay;
    private String publicHolidayName;
    private Long startOfWorkMinute;
    private boolean invalidStartOfWork;
    private Long breakMinutes;
    private boolean invalidBreakTime;

    public DayAndWorkingHourCount(int day, Duration workingHour, LocalDate date) {
        this.day = day;
        this.date = date;
        this.workingHour = workingHour;
    }

    public String getWorkingHourString() {
        return DurationUtils.format(workingHour);
    }

    public String getDayString() {
        return day < 10 ? "0" + day : Integer.toString(day);
    }

    public String getStartOfWorkString() {
        return startOfWorkMinute != null ? timeFormatMinutes(startOfWorkMinute) : null;
    }

    public String getBreakDurationString() {
        return breakMinutes != null ? timeFormatMinutes(breakMinutes) : null;
    }

}
