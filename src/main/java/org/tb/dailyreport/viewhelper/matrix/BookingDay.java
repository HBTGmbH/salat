package org.tb.dailyreport.viewhelper.matrix;

import java.time.Duration;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.util.DurationUtils;
import org.tb.order.domain.OrderType;

@Getter
@Setter
public class BookingDay implements Comparable<BookingDay> {

    private final LocalDate date;
    private Duration duration;
    private Duration workDuration;
    private String taskdescription;
    private boolean satSun;
    private boolean publicHoliday;
    private int bookingCount;

    public BookingDay(LocalDate date) {
        this.date = date;
        duration = Duration.ZERO;
        workDuration = Duration.ZERO;
        satSun = false;
        publicHoliday = false;
        this.taskdescription = "";
        bookingCount = 0;
    }

    public BookingDay(LocalDate date, Duration duration, String taskdescription, OrderType orderType) {
        this.date = date;
        satSun = false;
        publicHoliday = false;
        this.taskdescription = taskdescription;
        bookingCount = 1;
        this.duration = Duration.ZERO;
        workDuration = Duration.ZERO;
        addDuration(duration, orderType);
    }

    private void addDuration(Duration amount, OrderType orderType) {
        if(orderType == OrderType.STANDARD || orderType == null) {
            workDuration = workDuration.plus(amount);
        }
        duration = duration.plus(amount);
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

    public void addBooking(Duration duration, String taskdescription, OrderType orderType) {
        addDuration(duration, orderType);
        this.taskdescription += "\n" + taskdescription;
        bookingCount++;
    }

}
