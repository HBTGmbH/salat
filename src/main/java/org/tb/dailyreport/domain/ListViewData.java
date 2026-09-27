package org.tb.dailyreport.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record ListViewData(
    List<ListDay> days,
    String monthTotal,
    String monthTarget,
    String monthDiff,
    boolean monthDiffNegative,
    String prevDayDiffString,
    boolean prevDayDiffNegative,
    boolean hasTarget,
    boolean monthReleased,
    Set<Long> editableTimereportIds
) {
    public record ListDay(
        LocalDate date,
        List<TimereportDTO> timereports,
        String total,
        boolean isWeekend,
        boolean isHoliday,
        String holidayName,
        boolean notWorked,
        boolean isToday,
        /** a booking may be created on this day - decided per day, not per month (#1164) */
        boolean canCreate
    ) {}
}
