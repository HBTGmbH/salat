package org.tb.dailyreport.domain;

import java.time.LocalDate;
import java.util.List;

public record ListViewData(
    List<ListDay> days,
    String monthTotal,
    String monthTarget,
    String monthDiff,
    boolean monthDiffNegative,
    boolean hasTarget,
    boolean monthReleased
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
