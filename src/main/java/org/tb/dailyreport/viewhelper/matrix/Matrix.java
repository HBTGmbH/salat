package org.tb.dailyreport.viewhelper.matrix;

import static org.tb.common.util.DurationUtils.format;

import java.time.Duration;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Matrix {

    private final List<MatrixLine> matrixLines;
    private final List<MatrixDayTotal> matrixDayTotals;
    private final Duration totalWorkingTime;
    private final Duration totalWorkingTimeTarget;
    private final Duration totalWorkingTimeDiff;
    private final Duration totalOvertimeCompensation;
    private final Duration totalWorkingTimeDiffWithCompensation;

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

    public String getTotalWorkingTimeDiffWithCompensationString() {
        return format(totalWorkingTimeDiffWithCompensation);
    }

}
