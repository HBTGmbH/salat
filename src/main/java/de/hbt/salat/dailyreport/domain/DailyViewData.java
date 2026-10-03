package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record DailyViewData(
    List<TimereportDTO> timereports,
    Duration totalBooked,
    Workingday workingday,
    String quittingTime,
    // when the target of the day is fulfilled, null where the day has no target or was not worked (#1236)
    TargetEnd targetEnd,
    // what is left to the target of the day, or with a leading "+" what lies beyond it once it is
    // reached; null where targetEnd is null (#1236)
    String targetDifference,
    boolean targetReached,
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
    boolean canCreateTimereport,
    // open, committed or closed: the period the day lies in, shown in the heading of the day (#1164)
    String reportStatus
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
