package org.tb.dailyreport.viewhelper.matrix;

import java.time.Duration;
import java.time.LocalDate;

import lombok.Data;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;

import static java.time.Duration.ZERO;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.PARTIALLY;

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
        return workingDayType == NOT_WORKED;
    }

    public boolean isPartiallyNotWorked() {
        return workingDayType == PARTIALLY;
    }

    public Duration getEffectiveOvertime() {
        var effectiveOvertime = ZERO;
        if(workingDayType == PARTIALLY) {
            effectiveOvertime = contractWorkingTime.minus(workingTime);
        } else if(workingDayType == NOT_WORKED) {
            effectiveOvertime = contractWorkingTime;
        }
        if(effectiveOvertime.isNegative()) {
            effectiveOvertime = ZERO;
        }
        return effectiveOvertime;
    }
}
