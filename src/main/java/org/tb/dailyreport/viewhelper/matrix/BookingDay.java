package org.tb.dailyreport.viewhelper.matrix;

import java.time.Duration;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.util.DurationUtils;

@Getter
@Setter
public class BookingDay implements Comparable<BookingDay> {

    private final LocalDate date;
    private Duration duration;
    private String taskdescription;
    private boolean satSun;
    private boolean publicHoliday;
    private int bookingCount;

    public BookingDay(LocalDate date) {
        this.date = date;
        duration = Duration.ZERO;
        satSun = false;
        publicHoliday = false;
        this.taskdescription = "";
        bookingCount = 0;
    }

    public BookingDay(LocalDate date, long durationHours, long durationMinutes, String taskdescription) {
        this.date = date;
        duration = Duration.ofHours(durationHours);
        duration = duration.plusMinutes(durationMinutes);
        satSun = false;
        publicHoliday = false;
        this.taskdescription = taskdescription;
        bookingCount = 1;
    }

    public int compareTo(BookingDay o) {
        return this.date.compareTo(o.date);
    }

    public String getDurationString() {
        return DurationUtils.format(duration);
    }

    public void addBooking(long durationHours, long durationMinutes, String taskdescription) {
        duration = duration.plusHours(durationHours);
        duration = duration.plusMinutes(durationMinutes);
        this.taskdescription += "\n" + taskdescription;
        bookingCount++;
    }

}
