package org.tb.dailyreport.viewhelper.matrix;

import static org.tb.common.util.TimeFormatUtils.timeFormatHoursAndMinutes;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.util.DateUtils;

@Getter
@Setter
public class BookingDay implements Comparable<BookingDay> {

    private final LocalDate date;
    private final long durationHours;
    private final long durationMinutes;
    private final String taskdescription;
    private boolean satSun;
    private boolean publicHoliday;

    public BookingDay(LocalDate date, long durationHours, long durationMinutes, String taskdescription) {
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
