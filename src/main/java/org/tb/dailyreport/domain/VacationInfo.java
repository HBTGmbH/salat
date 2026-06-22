package org.tb.dailyreport.domain;

import java.time.Duration;

public record VacationInfo(String suborderSign, Duration budget, long usedVacationMinutes) {
}
