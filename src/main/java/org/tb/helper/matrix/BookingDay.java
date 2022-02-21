package org.tb.helper.matrix;

import static org.tb.util.TimeFormatUtils.timeFormatHoursAndMinutes;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookingDay implements Comparable<BookingDay> {

    private final Date date;
    private final long durationHours;
    private final long durationMinutes;
    private final String taskdescription;
    private boolean satSun;
    private boolean publicHoliday;

    public BookingDay(Date date, long durationHours, long durationMinutes, String taskdescription) {
        this.date = new Date(date.getTime());
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
