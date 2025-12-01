package org.tb.dailyreport.rest;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;

@Getter
@Builder
@Jacksonized
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Schema(description = "Tägliche Zeiterfassungen mit Arbeitszeiten und zugehörigen Zeitbuchungen")
public class DailyWorkingReportData {

    @NonNull
    @JsonFormat(pattern="yyyy-MM-dd")
    @Schema(description = "Leistungsdatum", example = "2025-09-09", requiredMode = REQUIRED)
    private LocalDate date;

    @JsonFormat(pattern="HH:mm")
    @Schema(description = "Startzeit des Arbeitstages im Format HH:mm", example = "08:00", nullable = true)
    private LocalTime startTime;

    @JsonFormat(pattern="HH:mm")
    @Schema(description = "Dauer der Pause im Format HH:mm", example = "00:30", nullable = true)
    private LocalTime breakDuration;

    @Schema(description = "Typ des Arbeitstages", example = "WORKED", nullable = true,
        allowableValues = {"WORKED", "NOT_WORKED" })
    private WorkingDayType type;

    @Schema(description = "Liste der einzelnen Zeitbuchungen für diesen Tag")
    private List<DailyReportData> dailyReports;
}
