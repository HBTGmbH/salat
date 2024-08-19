package org.tb.dailyreport.viewhelper.matrix;

import java.time.Duration;
import java.time.LocalDate;

import lombok.Data;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;

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
    private WorkingDayType workingDayType;
    private Duration contractWorkingTime;

    public MatrixDayTotal(LocalDate date, int day, Duration workingTime, Duration contractWorkingTime) {
        this.date = date;
        this.day = day;
        this.workingTime = workingTime;
        this.contractWorkingTime = contractWorkingTime;
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

    public boolean isNotWorked() {
        return workingDayType == WorkingDayType.NOT_WORKED;
    }

    public boolean isPartiallyNotWorked() {
        return workingDayType == WorkingDayType.PARTIALLY;
    }

    public Duration getEffectiveTargetTime() {
        if(workingDayType == WorkingDayType.PARTIALLY) {
            return workingTime;
        }
        if(workingDayType == WorkingDayType.NOT_WORKED) {
            return Duration.ZERO;
        }
        return contractWorkingTime;
    }

    public Duration getEffectiveOvertime() {
        if(workingDayType == WorkingDayType.PARTIALLY) {
            return contractWorkingTime.minus(workingTime);
        }
        if(workingDayType == WorkingDayType.NOT_WORKED) {
            return contractWorkingTime;
        }
        return Duration.ZERO;
    }
}
