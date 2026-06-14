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
    Set<Long> editableTimereportIds,
    boolean canCreateTimereport
) {
    public record ListDay(
        LocalDate date,
        List<TimereportDTO> timereports,
        String total,
        boolean isWeekend,
        boolean isHoliday,
        String holidayName,
        boolean notWorked,
        boolean isToday
    ) {}
}
