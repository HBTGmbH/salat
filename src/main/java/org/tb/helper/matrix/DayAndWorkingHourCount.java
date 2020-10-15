package org.tb.helper.matrix;

import lombok.Data;

import java.util.Date;

@Data
public class DayAndWorkingHourCount {
    private int day;
    private double workingHour;
    private Date date;
    private boolean publicHoliday;
    private boolean satSun;
    private String weekDay;
    private String publicHolidayName;

    public DayAndWorkingHourCount(int day, double workingHour, Date date) {
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

    public String getDayString() {
        return day < 10 ? "0" + day : Integer.toString(day);
    }

}
