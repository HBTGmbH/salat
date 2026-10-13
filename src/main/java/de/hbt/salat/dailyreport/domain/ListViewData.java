package de.hbt.salat.dailyreport.domain;

import java.time.Duration;
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
    // how far acceptance and release reach into the month, shown in its heading (#1164)
    ReportPeriod.Month reportPeriod
) {
    /** The time surcharge credit of the month's bookings (#1469), shown next to the totals. */
    public Duration surchargeTotal() {
        return days.stream().flatMap(day -> day.timereports().stream())
            .map(TimereportDTO::getSurchargeCredit).reduce(Duration.ZERO, Duration::plus);
    }

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
