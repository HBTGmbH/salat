package org.tb.dailyreport.viewhelper.matrix;

import static org.tb.common.util.DurationUtils.format;

import java.time.Duration;
import java.util.List;
import lombok.Getter;

@Getter
public class ReportWrapper {

    private final List<MatrixLine> matrixLines;
    private final List<DayAndWorkingHourCount> dayAndWorkingHourCountList;
    private final Duration dayHoursSum;
    private final Duration dayHoursTarget;
    private final Duration overtimeCompensation;
    private final Duration dayHoursDiff;

    public ReportWrapper(List<MatrixLine> matrixLines, List<DayAndWorkingHourCount> dayAndWorkingHourCountList, Duration dayHoursSum, Duration dayHoursTarget, Duration dayHoursDiff, Duration overtimeCompensation) {
        this.matrixLines = matrixLines;
        this.dayAndWorkingHourCountList = dayAndWorkingHourCountList;
        this.dayHoursSum = dayHoursSum;
        this.dayHoursTarget = dayHoursTarget;
        this.dayHoursDiff = dayHoursDiff;
        this.overtimeCompensation = overtimeCompensation;
    }

    public String getDayHoursSumString() {
        return format(dayHoursSum);
    }

    public String getDayHoursTargetString() {
        return format(dayHoursTarget);
    }

    public String getOvertimeCompensationString() {
        return format(overtimeCompensation);
    }

    public String getDayHoursDiffString() {
        return format(dayHoursDiff);
    }

}
