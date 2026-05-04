package org.tb.dailyreport.viewhelper.matrix;

import static java.time.Duration.ZERO;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.OVERTIME_COMPENSATED;

import java.time.Duration;
import java.time.LocalDate;
import lombok.Data;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;

@Data
public class MatrixDayTotal {

    private int day;
    private Duration workingTime;
    private Duration netWorkingTime; // time without any leave like sick or holiday
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

    public MatrixDayTotal(LocalDate date, int day, Duration workingTime, Duration contractWorkingTime, Duration netWorkingTime) {
        this.date = date;
        this.day = day;
        this.workingTime = workingTime;
        this.contractWorkingTime = contractWorkingTime;
        this.netWorkingTime = netWorkingTime;
    }

    public String getWorkingTimeString() {
        return DurationUtils.format(workingTime, false);
    }

    public String getDayString() {
        return day < 10 ? "0" + day : Integer.toString(day);
    }

    public String getStartOfWorkString() {
        return startOfWorkMinute != null ? timeFormatMinutes(startOfWorkMinute) : null;
    }

    public String getEndOfWorkString() {
        Long endOfWorkMinute = startOfWorkMinute != null ? startOfWorkMinute + netWorkingTime.toMinutes() + orZero(breakMinutes) : null;
        return startOfWorkMinute != null ? timeFormatMinutes(endOfWorkMinute) : null;
    }

    private long orZero(Long breakMinutes) {
        return breakMinutes != null ? breakMinutes : 0;
    }

    public String getBreakDurationString() {
        return breakMinutes != null ? timeFormatMinutes(breakMinutes) : null;
    }

    public void addWorkingTime(Duration workingTime, Duration netWorkingTime) {
        this.workingTime = this.workingTime.plus(workingTime);
        this.netWorkingTime = this.netWorkingTime.plus(netWorkingTime);
    }

    public boolean isZeroWorkingTime() {
        return workingTime == null || workingTime.isZero();
    }

    public boolean isNotWorked() {
        return workingDayType == NOT_WORKED;
    }

    public boolean isOvertimeCompensated() {
        return workingDayType == OVERTIME_COMPENSATED;
    }

    public Duration getEffectiveOvertime() {
        var effectiveOvertime = ZERO;
        if(workingDayType == OVERTIME_COMPENSATED) {
            effectiveOvertime = contractWorkingTime.minus(workingTime);
        }
        if(effectiveOvertime.isNegative()) {
            effectiveOvertime = ZERO;
        }
        return effectiveOvertime;
    }
}
