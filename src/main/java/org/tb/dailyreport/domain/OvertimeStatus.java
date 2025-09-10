package org.tb.dailyreport.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.LocalDate;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@Schema(description = "Überstundenstatus eines Mitarbeiters")
public class OvertimeStatus {

  @Schema(description = "Gesamtüberstunden")
  private OvertimeStatusInfo total;

  @Schema(description = "Überstunden des aktuellen Monats")
  private OvertimeStatusInfo currentMonth;

  @Data
  @Schema(description = "Detaillierte Informationen zu Überstunden")
  public static class OvertimeStatusInfo {
    @Schema(description = "Startdatum des Betrachtungszeitraums", example = "2023-01-01")
    private LocalDate begin;

    @Schema(description = "Enddatum des Betrachtungszeitraums", example = "2023-01-31")
    private LocalDate end;

    @Schema(description = "Anzahl Überstunden in Tagen", example = "5")
    private long days;

    @Schema(description = "Anzahl Überstunden (zusätzlich)", example = "40")
    private long hours;

    @Schema(description = "Anzahl Überstundenminuten (zusätzlich)", example = "30")
    private long minutes;

    @Schema(description = "Gesamtdauer der Überstunden als Duration", example = "PT40H30M")
    private Duration duration;

    @Schema(description = "Gibt an, ob es sich um negative Überstunden (=Unterstunden) handelt", example = "false")
    private boolean negative;
  }

}
