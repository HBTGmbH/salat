package org.tb.dailyreport.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.tb.common.exception.ServiceFeedbackMessage;

public record ReleaseReviewDay(
    LocalDate date,
    List<TimereportDTO> timereports,
    Duration totalDuration,
    List<ServiceFeedbackMessage> errors
) {
    public long totalHours() {
        return totalDuration.toHours();
    }

    public long totalMinutes() {
        return totalDuration.toMinutesPart();
    }
}
