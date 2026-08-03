package org.tb.dailyreport.domain;

import java.time.Duration;
import java.util.List;

public record ReleaseReviewEntry(
    String customerorderSign,
    String customerorderDescription,
    String customerShortname,
    String completeOrderSign,
    String suborderDescription,
    Duration totalDuration,
    List<String> comments
) {
    public long totalHours() {
        return totalDuration.toHours();
    }

    public long totalMinutes() {
        return totalDuration.toMinutesPart();
    }
}
