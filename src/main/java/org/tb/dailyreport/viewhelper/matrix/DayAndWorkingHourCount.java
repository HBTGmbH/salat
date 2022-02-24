package org.tb.dailyreport.viewhelper.matrix;

import static org.tb.common.util.TimeFormatUtils.timeFormatHours;

import lombok.Data;

@Data
public class DayAndWorkingHourCount {
    private int day;
    private double workingHour;
    private String date;
    private boolean publicHoliday;
    private boolean satSun;
    private String weekDay;
    private String publicHolidayName;

    public DayAndWorkingHourCount(int day, double workingHour, String date) {
        this.day = day;
        this.date = date;
        this.workingHour = workingHour;
        this.publicHoliday = false;
        this.satSun = false;
        this.weekDay = null;
        this.publicHolidayName = null;
    }

    public double getRoundWorkingHour() {
        long duration = (long) (workingHour * 100);
        return (double) duration / 100.0;
    }

    public String getWorkingHourString() {
        return timeFormatHours(workingHour);
    }

    public String getDayString() {
        return day < 10 ? "0" + day : Integer.toString(day);
    }

}
