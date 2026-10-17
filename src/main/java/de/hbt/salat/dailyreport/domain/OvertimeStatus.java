package de.hbt.salat.dailyreport.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.LocalDate;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import de.hbt.salat.common.json.HoursMinutes;

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

    /**
     * The overtime as one value (#1398), the sign included; the fields that split it into days, hours, minutes
     * and a sign, and {@link #duration} in ISO-8601, stay for a while for older clients.
     */
    @HoursMinutes.Signed
    @Schema(description = "Überstunden im Format HH:mm, Unterstunden mit vorangestelltem Minus", type = "string",
        pattern = HoursMinutes.SIGNED_PATTERN, example = "-12:30")
    private Duration balance;

    @Deprecated
    @Schema(description = "Abgekündigt, ersetzt durch balance: Anzahl Überstunden in Tagen", example = "5", deprecated = true)
    private long days;

    @Deprecated
    @Schema(description = "Abgekündigt, ersetzt durch balance: Anzahl Überstunden (zusätzlich)", example = "40",
        deprecated = true)
    private long hours;

    @Deprecated
    @Schema(description = "Abgekündigt, ersetzt durch balance: Anzahl Überstundenminuten (zusätzlich)", example = "30",
        deprecated = true)
    private long minutes;

    @Deprecated
    @Schema(description = "Abgekündigt, ersetzt durch balance: Gesamtdauer der Überstunden im Format ISO-8601",
        example = "PT40H30M", deprecated = true)
    private Duration duration;

    @Deprecated
    @Schema(description = "Abgekündigt, ersetzt durch das Vorzeichen von balance: Gibt an, ob es sich um negative "
        + "Überstunden (=Unterstunden) handelt", example = "false", deprecated = true)
    private boolean negative;

    /** The share of the time surcharges in {@link #balance} (#1469), as HH:mm like every duration (#1398). */
    @HoursMinutes
    @Schema(description = "Darin enthaltene Zeitgutschrift aus Nacht-, Wochenend- und Feiertagseinsätzen im Format HH:mm",
        type = "string", pattern = HoursMinutes.PATTERN, example = "02:30")
    private Duration surchargeCredit;
  }

}
