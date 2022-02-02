package org.tb.helper.matrix;

import static org.tb.util.TimeFormatUtils.timeFormatHoursAndMinutes;

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

    public String getDurationString() {
        return timeFormatHoursAndMinutes(durationHours, durationMinutes);
    }

}
