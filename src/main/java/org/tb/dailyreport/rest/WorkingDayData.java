package org.tb.dailyreport.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;

@Getter
@Builder
@Jacksonized
@AllArgsConstructor
@Schema(description = "Arbeitstaginformationen zu einem Arbeitstag")
public class WorkingDayData {

    @Builder.Default
    @Schema(description = "ID der Arbeitstaginformationen, wird vom System vergeben", example = "42")
    private Long id = -1L;

    @Schema(description = "Startstunde des Arbeitstages", example = "8", minimum = "0", maximum = "23")
    private int starthour;

    @Schema(description = "Startminute des Arbeitstages", example = "30", minimum = "0", maximum = "59")
    private int startminute;

    @Schema(description = "Pausenstunden", example = "1", minimum = "0")
    private int breakhours;

    @Schema(description = "Pausenminuten", example = "30", minimum = "0", maximum = "59")
    private int breakminutes;

    @Schema(description = "Datum des Arbeitstages im Format yyyy-MM-dd", example = "2023-09-01")
    private String date;

    @Schema(description = "Typ des Arbeitstages", example = "WORKED", nullable = true,
        allowableValues = {"WORKED", "NOT_WORKED" })
    private WorkingDayType type;

    static WorkingDayData valueOf(Workingday wd) {
        return WorkingDayData.builder()
                .id(wd.getId())
                .starthour(wd.getStarttimehour())
                .startminute(wd.getStarttimeminute())
                .breakhours(wd.getBreakhours())
                .breakminutes(wd.getBreakminutes())
                .date(DateUtils.format(wd.getRefday()))
                .type(wd.getType())
                .build();
    }
}
