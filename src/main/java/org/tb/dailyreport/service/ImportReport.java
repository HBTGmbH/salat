package org.tb.dailyreport.service;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ImportReport(List<DayResult> days, int linesRead) implements Serializable {

    public ImportReport(List<DayResult> days) { this(days, 0); }

    public record BookingDetail(
        String suborderSign,
        String suborderLabel,
        long hours,
        long minutes,
        String comment
    ) implements Serializable {}

    public record UpdatedBookingDetail(
        BookingDetail from,
        BookingDetail to
    ) implements Serializable {}

    public record DayResult(
        LocalDate date,
        boolean workingDayCreated,
        boolean workingDayDataChanged,
        LocalTime startTime,
        LocalTime breakDuration,
        List<BookingDetail> bookingsCreated,
        List<BookingDetail> bookingsDeleted,
        List<UpdatedBookingDetail> bookingsUpdated
    ) implements Serializable {
        public boolean workingDayChanged() { return workingDayCreated || workingDayDataChanged; }
        public boolean hasChanges() { return workingDayChanged() || !bookingsCreated.isEmpty() || !bookingsDeleted.isEmpty() || !bookingsUpdated.isEmpty(); }
        public int bookingsCreatedCount() { return bookingsCreated.size(); }
        public int bookingsDeletedCount() { return bookingsDeleted.size(); }
        public int bookingsUpdatedCount() { return bookingsUpdated.size(); }
    }

    public int totalBookingsCreated() {
        return days.stream().mapToInt(DayResult::bookingsCreatedCount).sum();
    }

    public int totalBookingsDeleted() {
        return days.stream().mapToInt(DayResult::bookingsDeletedCount).sum();
    }

    public int totalBookingsUpdated() {
        return days.stream().mapToInt(DayResult::bookingsUpdatedCount).sum();
    }

    public long totalWorkingDaysChanged() {
        return days.stream().filter(DayResult::workingDayChanged).count();
    }
}
