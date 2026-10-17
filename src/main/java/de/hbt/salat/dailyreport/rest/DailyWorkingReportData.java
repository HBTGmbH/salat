package de.hbt.salat.dailyreport.rest;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Duration;
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
import de.hbt.salat.common.json.HoursMinutes;
import de.hbt.salat.common.json.TimeOfDay;
import de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType;

@Getter
@Builder(toBuilder = true)
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

    @TimeOfDay
    @Schema(description = "Startzeit des Arbeitstages im Format HH:mm, 00:00 bis 23:59", type = "string",
        example = "08:00", nullable = true)
    private LocalTime startTime;

    /** A duration, not a time of day (#1398): the format stays HH:mm, a break of 24 hours or more is one too. */
    @HoursMinutes
    @Schema(description = "Dauer der Pause im Format HH:mm", type = "string", pattern = HoursMinutes.PATTERN,
        example = "00:30", nullable = true)
    private Duration breakDuration;

    @Schema(description = "Typ des Arbeitstages", example = "WORKED", nullable = true,
        allowableValues = {"WORKED", "NOT_WORKED" })
    private WorkingDayType type;

    @Schema(description = "Liste der einzelnen Zeitbuchungen für diesen Tag")
    private List<DailyReportData> dailyReports;
}
