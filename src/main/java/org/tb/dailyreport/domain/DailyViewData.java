package org.tb.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record DailyViewData(
    List<TimereportDTO> timereports,
    Duration totalBooked,
    Workingday workingday,
    String quittingTime,
    String targetEndTime,
    // the contract does target accounting at all, i.e. it has a daily working time
    boolean hasTarget,
    // this particular day has a target - false on weekends, public holidays and outside the
    // validity of the contract (#857)
    boolean hasDayTarget,
    boolean overMaxHours,
    int progressPercent,
    List<WeekStripDay> weekStrip,
    boolean notWorked,
    String startTime,
    String breakTime,
    String dailyWorkingTimeFormatted,
    Set<Long> editableTimereportIds,
    boolean workingdayEditable,
    boolean canCreateTimereport
) {
    public record WeekStripDay(
        LocalDate date,
        Duration booked,
        int bookingCount,
        boolean isToday,
        boolean isSelected,
        boolean isHoliday,
        String holidayName,
        boolean notWorked
    ) {}
}
