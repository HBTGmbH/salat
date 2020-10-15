package org.tb.helper.matrix;

import lombok.Data;

import java.util.Date;

@Data
public class BookingDay implements Comparable<BookingDay> {
    private Date date;
    private long durationHours;
    private long durationMinutes;
    private String taskdescription;
    private boolean satSun;
    private boolean publicHoliday;

    public BookingDay(Date date, long durationHours, long durationMinutes, String taskdescription) {
        this.date = date;
        this.durationHours = durationHours;
        this.durationMinutes = durationMinutes;
        this.satSun = false;
        this.publicHoliday = false;
        this.taskdescription = taskdescription;
    }

    public int compareTo(BookingDay o) {
        return this.date.compareTo(o.date);
    }

    public double getRoundHours() {
        long duration = (durationHours * 60 + durationMinutes) * 100 / 60;
        return (double) duration / 100;
    }

}
