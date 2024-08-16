package org.tb.dailyreport.viewhelper.matrix;

import java.time.Duration;
import java.time.LocalDate;

import lombok.Data;
import org.tb.common.util.DurationUtils;

import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

@Data
public class MatrixDayTotal {

    private int day;
    private Duration workingTime;
    private LocalDate date;
    private boolean publicHoliday;
    private boolean satSun;
    private String weekDay;
    private String publicHolidayName;
    private Long startOfWorkMinute;
    private boolean invalidStartOfWork;
    private Long breakMinutes;
    private boolean invalidBreakTime;

    public MatrixDayTotal(LocalDate date, int day, Duration workingTime) {
        this.date = date;
        this.day = day;
        this.workingTime = workingTime;
    }

    public String getWorkingTimeString() {
        return DurationUtils.format(workingTime);
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

    public void addWorkingTime(Duration workingTime) {
        this.workingTime = this.workingTime.plus(workingTime);
    }

    public boolean isZeroWorkingTime() {
        return workingTime == null || workingTime.isZero();
    }
}
