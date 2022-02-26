package org.tb.dailyreport.viewhelper.matrix;

import static org.tb.common.util.TimeFormatUtils.timeFormatHours;

import java.util.List;

public class ReportWrapper {

    private final List<MergedReport> mergedReportList;
    private final List<DayAndWorkingHourCount> dayAndWorkingHourCountList;
    private final double dayHoursSum;
    private final double dayHoursTarget;
    private double dayHoursDiff;

    public ReportWrapper(List<MergedReport> mergedReportList, List<DayAndWorkingHourCount> dayAndWorkingHourCountList, double dayHoursSum, double dayHoursTarget, double dayHoursDiff) {
        this.mergedReportList = mergedReportList;
        this.dayAndWorkingHourCountList = dayAndWorkingHourCountList;
        this.dayHoursSum = dayHoursSum;
        this.dayHoursTarget = dayHoursTarget;
        this.dayHoursDiff = dayHoursDiff;
    }

    public double getDayHoursDiff() {
        return dayHoursDiff;
    }

    public void setDayHoursDiff(double dayHoursDiff) {
        this.dayHoursDiff = dayHoursDiff;
    }

    public double getDayHoursTarget() {
        return dayHoursTarget;
    }

    public double getDayHoursSum() {
        return dayHoursSum;
    }

    public String getDayHoursSumString() {
        return timeFormatHours(dayHoursSum);
    }

    public String getDayHoursTargetString() {
        return timeFormatHours(dayHoursTarget);
    }

    public String getDayHoursDiffString() {
        return timeFormatHours(dayHoursDiff);
    }

    public List<DayAndWorkingHourCount> getDayAndWorkingHourCountList() {
        return dayAndWorkingHourCountList;
    }

    public List<MergedReport> getMergedReportList() {
        return mergedReportList;
    }

}