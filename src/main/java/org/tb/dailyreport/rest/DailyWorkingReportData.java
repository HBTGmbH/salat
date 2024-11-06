package org.tb.dailyreport.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Jacksonized
@EqualsAndHashCode
public class DailyWorkingReportData {

    @NonNull
    @JsonFormat(pattern="yyyy-MM-dd HH:mm")
    private LocalDateTime date;
    private Integer breakMinutes;
    private WorkingDayType type;

    private List<DailyReportData> dailyReports;
}
