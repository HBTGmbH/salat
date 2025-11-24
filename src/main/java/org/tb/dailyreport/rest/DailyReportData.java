package org.tb.dailyreport.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencsv.bean.CsvBindByPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TimereportDTO;

@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
@Schema(description = "Zeitbuchung")
public class DailyReportData {

    @Schema(description = "Eindeutige ID der Zeitbuchung, wird vom System vergeben", example = "12345")
    private Long id;

    @CsvBindByPosition(position = 0)
    @Schema(description = "Leistungsdatum der Zeitbuchung im Format YYYY-MM-DD", example = "2025-09-09")
    private String date;

    @CsvBindByPosition(position = 1)
    @Schema(description = "ID des Mitarbeiterauftrags", example = "78")
    private long employeeorderId;

    @CsvBindByPosition(position = 2)
    @Schema(description = "Kürzel des Kundenauftrags", example = "4711")
    private String orderSign;
    @CsvBindByPosition(position = 3)
    @Schema(description = "Bezeichnung des Kundenauftrags", example = "Softwareentwicklung ERP-System")
    private String orderLabel;

    @CsvBindByPosition(position = 4)
    @Schema(description = "Kürzel des Unterauftrags", example = "4711/01")
    private String suborderSign;
    @CsvBindByPosition(position = 5)
    @Schema(description = "Bezeichnung des Unterauftrags", example = "API-Entwicklung")
    private String suborderLabel;

    @CsvBindByPosition(position = 6)
    @Schema(description = "Anzahl der gebuchten Stunden ohne Minuten", example = "4")
    private long hours;

    @CsvBindByPosition(position = 7)
    @Schema(description = "Anzahl der gebuchten Minuten (zusätzlich zu den Stunden)", example = "30")
    private long minutes;

    @CsvBindByPosition(position = 8)
    @Schema(description = "Kommentar oder Beschreibung der durchgeführten Arbeit", example = "ERP-3032 Implementierung der REST-API für Zeiterfassung")
    private String comment;

    @Schema(description = "Gibt an, ob in dieser Zeit eine besondere Lernleistung ähnlich einer Schulung stattgefunden hat", example = "false")
    private boolean training;

    public static DailyReportData valueOf(TimereportDTO timeReport) {
        return DailyReportData.builder()
                .id(timeReport.getId())
                .employeeorderId(timeReport.getEmployeeorderId())
                .date(DateUtils.format(timeReport.getReferenceday()))
                .orderLabel(timeReport.getCustomerorderDescription())
                .suborderLabel(timeReport.getSuborderDescription())
                .comment(timeReport.getTaskdescription())
                .training(timeReport.isTraining())
                .hours(timeReport.getDuration().toHours())
                .minutes(timeReport.getDuration().toMinutesPart())
                .suborderSign(timeReport.getCompleteOrderSign())
                .orderSign(timeReport.getCustomerorderSign())
                .build();
    }

    public DailyReportData withoutId(){
        return toBuilder().id(null).build();
    }

  @JsonCreator
  public static DailyReportData jacksonCreator(
      @JsonProperty("id") Long id,
      @JsonProperty("date") String date,
      @JsonProperty("employeeorderId") long employeeorderId,
      @JsonProperty("orderSign") String orderSign,
      @JsonProperty("orderLabel") String orderLabel,
      @JsonProperty("suborderSign") String suborderSign,
      @JsonProperty("suborderLabel") String suborderLabel,
      @JsonProperty("hours") long hours,
      @JsonProperty("minutes") long minutes,
      @JsonProperty("comment") String comment,
      @JsonProperty("training") boolean training
  ) {
    return DailyReportData.builder()
        .id(id)
        .date(date)
        .employeeorderId(employeeorderId)
        .orderSign(orderSign)
        .orderLabel(orderLabel)
        .suborderSign(suborderSign)
        .suborderLabel(suborderLabel)
        .hours(hours)
        .minutes(minutes)
        .comment(comment)
        .training(training)
        .build();
  }

}
