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

    public BookingDay(LocalDate date, Duration duration, String taskdescription) {
        this.date = date;
        this.duration = duration;
        satSun = false;
        publicHoliday = false;
        this.taskdescription = taskdescription;
        bookingCount = 1;
    }

    public int compareTo(BookingDay o) {
        return this.date.compareTo(o.date);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BookingDay that = (BookingDay) obj;
        return date.equals(that.date);
    }

    @Override
    public int hashCode() {
        return date.hashCode();
    }

    public String getDurationString() {
        return DurationUtils.format(duration, bookingCount > 0);
    }

    public void addBooking(Duration duration, String taskdescription) {
        this.duration = this.duration.plus(duration);
        this.taskdescription += "\n" + taskdescription;
        bookingCount++;
    }

}
