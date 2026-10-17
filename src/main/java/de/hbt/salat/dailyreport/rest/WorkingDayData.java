package de.hbt.salat.dailyreport.rest;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import de.hbt.salat.common.json.HoursMinutes;
import de.hbt.salat.common.json.TimeOfDay;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType;

/**
 * Start and break as one value each since #1398. The four numbers of older clients stay readable and
 * writable for a while: read, they are derived from {@link #startTime} and {@link #breakDuration}; written,
 * they count only where the new field is missing.
 */
@Getter
@Builder
@Jacksonized
@AllArgsConstructor
@Schema(description = "Arbeitstaginformationen zu einem Arbeitstag")
public class WorkingDayData {

    @Builder.Default
    @Schema(description = "ID der Arbeitstaginformationen, wird vom System vergeben", example = "42")
    private Long id = -1L;

    @TimeOfDay
    @Schema(description = "Beginn des Arbeitstages im Format HH:mm, 00:00 bis 23:59. Hat beim Schreiben Vorrang vor "
        + "starthour und startminute; fehlt er, gelten diese.",
        type = "string", example = "08:30", nullable = true)
    private LocalTime startTime;

    @HoursMinutes
    @Schema(description = "Dauer der Pause im Format HH:mm. Hat beim Schreiben Vorrang vor breakhours und breakminutes; "
        + "fehlt sie, gelten diese.",
        type = "string", pattern = HoursMinutes.PATTERN, example = "00:45", nullable = true)
    private Duration breakDuration;

    @Deprecated
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Schema(description = "Abgekündigt, ersetzt durch startTime: Startstunde des Arbeitstages",
        example = "8", minimum = "0", maximum = "23", deprecated = true)
    private int starthour;

    @Deprecated
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Schema(description = "Abgekündigt, ersetzt durch startTime: Startminute des Arbeitstages",
        example = "30", minimum = "0", maximum = "59", deprecated = true)
    private int startminute;

    @Deprecated
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Schema(description = "Abgekündigt, ersetzt durch breakDuration: Pausenstunden",
        example = "0", minimum = "0", deprecated = true)
    private int breakhours;

    @Deprecated
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Schema(description = "Abgekündigt, ersetzt durch breakDuration: Pausenminuten",
        example = "45", minimum = "0", maximum = "59", deprecated = true)
    private int breakminutes;

    @Schema(description = "Datum des Arbeitstages im Format yyyy-MM-dd", example = "2023-09-01")
    private String date;

    @Schema(description = "Typ des Arbeitstages", example = "WORKED", nullable = true,
        allowableValues = {"WORKED", "NOT_WORKED" })
    private WorkingDayType type;

    /** The hour of {@link #startTime} where it is given, for clients from before #1398. */
    @Deprecated
    public int getStarthour() {
        return startTime != null ? startTime.getHour() : starthour;
    }

    /** The minute of {@link #startTime} where it is given, for clients from before #1398. */
    @Deprecated
    public int getStartminute() {
        return startTime != null ? startTime.getMinute() : startminute;
    }

    /** The hours of {@link #breakDuration} where it is given, for clients from before #1398. */
    @Deprecated
    public int getBreakhours() {
        return breakDuration != null ? Math.toIntExact(breakDuration.toHours()) : breakhours;
    }

    /** The minutes part of {@link #breakDuration} where it is given, for clients from before #1398. */
    @Deprecated
    public int getBreakminutes() {
        return breakDuration != null ? breakDuration.toMinutesPart() : breakminutes;
    }

    static WorkingDayData valueOf(Workingday wd) {
        return WorkingDayData.builder()
                .id(wd.getId())
                .startTime(wd.getStartTime())
                .breakDuration(wd.getBreakLength())
                .date(DateUtils.format(wd.getRefday()))
                .type(wd.getType())
                .build();
    }
}
