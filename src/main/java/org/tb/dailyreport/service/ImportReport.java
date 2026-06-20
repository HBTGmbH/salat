package org.tb.dailyreport.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public record ImportReport(List<DayResult> days) implements Serializable {

    public record BookingDetail(
        String suborderSign,
        String suborderLabel,
        long hours,
        long minutes,
        String comment
    ) implements Serializable {}

    public record DayResult(
        LocalDate date,
        boolean workingDayCreated,
        List<BookingDetail> bookingsCreated,
        List<BookingDetail> bookingsDeleted
    ) implements Serializable {
        public int bookingsCreatedCount() { return bookingsCreated.size(); }
        public int bookingsDeletedCount() { return bookingsDeleted.size(); }
    }

    public int totalBookingsCreated() {
        return days.stream().mapToInt(DayResult::bookingsCreatedCount).sum();
    }

    public int totalBookingsDeleted() {
        return days.stream().mapToInt(DayResult::bookingsDeletedCount).sum();
    }
}
