package org.tb.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public record DailyViewData(
    List<TimereportDTO> timereports,
    Duration totalBooked,
    Workingday workingday,
    String quittingTime,
    String targetEndTime,
    boolean hasTarget,
    boolean overMaxHours,
    int progressPercent,
    List<WeekStripDay> weekStrip
) {
    public record WeekStripDay(
        LocalDate date,
        Duration booked,
        boolean isToday,
        boolean isSelected,
        boolean isHoliday,
        String holidayName,
        boolean notWorked
    ) {}
}
