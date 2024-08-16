package org.tb.dailyreport.viewhelper.matrix;

import static org.tb.common.util.DurationUtils.format;

import java.time.Duration;
import java.util.List;
import lombok.Getter;

@Getter
public class Matrix {

    private final List<MatrixLine> matrixLines;
    private final List<MatrixDayTotal> matrixDayTotals;
    private final Duration totalWorkingTime;
    private final Duration totalWorkingTimeTarget;
    private final Duration totalOvertimeCompensation;
    private final Duration totalWorkingTimeDiff;

    public Matrix(List<MatrixLine> matrixLines, List<MatrixDayTotal> matrixDayTotals, Duration totalWorkingTime, Duration totalWorkingTimeTarget, Duration totalWorkingTimeDiff, Duration totalOvertimeCompensation) {
        this.matrixLines = matrixLines;
        this.matrixDayTotals = matrixDayTotals;
        this.totalWorkingTime = totalWorkingTime;
        this.totalWorkingTimeTarget = totalWorkingTimeTarget;
        this.totalWorkingTimeDiff = totalWorkingTimeDiff;
        this.totalOvertimeCompensation = totalOvertimeCompensation;
    }

    public String getTotalWorkingTimeString() {
        return format(totalWorkingTime);
    }

    public String getTotalWorkingTimeTargetString() {
        return format(totalWorkingTimeTarget);
    }

    public String getTotalOvertimeCompensationString() {
        return format(totalOvertimeCompensation);
    }

    public String getTotalWorkingTimeDiffString() {
        return format(totalWorkingTimeDiff);
    }

}
