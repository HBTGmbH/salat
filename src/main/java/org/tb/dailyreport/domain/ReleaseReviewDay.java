package org.tb.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.tb.common.exception.ServiceFeedbackMessage;

public record ReleaseReviewDay(
    LocalDate date,
    List<ReleaseReviewEntry> entries,
    Duration totalDuration,
    List<ServiceFeedbackMessage> errors,
    LocalTime startTime,
    Duration breakLength,
    boolean notWorked,
    String publicHolidayName
) {
    public boolean publicHoliday() {
        return publicHolidayName != null;
    }

    public long totalHours() {
        return totalDuration.toHours();
    }

    public long totalMinutes() {
        return totalDuration.toMinutesPart();
    }

    public LocalTime endTime() {
        if (startTime == null || totalDuration.isZero()) return null;
        return startTime.plus(breakLength).plus(totalDuration);
    }
}
