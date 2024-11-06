package org.tb.dailyreport.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;

@Getter
@Builder
@Jacksonized
@EqualsAndHashCode
public class DailyWorkingReportData {

    @NonNull
    @JsonFormat(pattern="yyyy-MM-dd")
    private LocalDate date;
    @JsonFormat(pattern="HH:mm")
    private LocalTime startTime;
    @JsonFormat(pattern="HH:mm")
    private LocalTime breakDuration;
    private WorkingDayType type;

    private List<DailyReportData> dailyReports;
}
