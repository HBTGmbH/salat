package org.tb.dailyreport.rest;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.tb.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvCustomBindByPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TimereportDTO;

@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
@ToString
@Schema(description = "Zeitbuchung")
@Jacksonized
@AllArgsConstructor
public class DailyReportData {

    @Schema(description = "Eindeutige ID der Zeitbuchung, wird vom System vergeben", example = "12345")
    private Long id;

    @CsvBindByPosition(position = 0)
    @Schema(description = "Leistungsdatum der Zeitbuchung im Format YYYY-MM-DD",
        example = "2025-09-09",
        requiredMode = REQUIRED)
    private String date;

    @CsvBindByPosition(position = 1)
    @Schema(description = "ID des Mitarbeiterauftrags",
        example = "78",
        requiredMode = REQUIRED)
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
    @Schema(description = "Anzahl der gebuchten Stunden ohne Minuten",
        example = "4",
        requiredMode = REQUIRED)
    private long hours;

    @CsvBindByPosition(position = 7)
    @Schema(description = "Anzahl der gebuchten Minuten (zusätzlich zu den Stunden)",
        example = "30",
        requiredMode = REQUIRED)
    private long minutes;

    @CsvBindByPosition(position = 8)
    @Schema(description = "Kommentar oder Beschreibung der durchgeführten Arbeit", example = "ERP-3032 Implementierung der REST-API für Zeiterfassung")
    private String comment;

    /**
     * {@code null} is not the same as "no reference" (#1140): it is what a file without the column
     * and a client that does not know the field send, and neither of them has changed a reference
     * they never saw. {@link org.tb.dailyreport.service.DailyWorkingReportService} resolves it against
     * the stored booking; an empty text is what says "no reference".
     */
    @CsvBindByPosition(position = 9)
    @Schema(description = "Referenz auf ein Ticket, etwa ein JIRA-Schlüssel, höchstens "
        + TICKET_REFERENCE_MAX_LENGTH + " Zeichen. Beim Lesen null, wenn die Buchung keine hat. "
        + "Beim Schreiben heißt ein leerer Text \"keine Referenz\"; fehlt das Feld oder ist es null, behält "
        + "eine vorhandene Buchung, die der übergebenen bis auf die Referenz gleicht, ihre Referenz.",
        example = "ERP-3032",
        maxLength = TICKET_REFERENCE_MAX_LENGTH,
        nullable = true)
    private String ticketReference;

    /**
     * Optional in JSON and CSV: a missing or empty value is {@code false} (#1140). Jackson 3 builds
     * this class through its constructor, not through the builder, so the fallback has to sit on the
     * property: without it a missing primitive is rejected.
     */
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @CsvCustomBindByPosition(position = 10, converter = DailyWorkingReportCsvConverter.TrainingFlagConverter.class)
    @Schema(description = "Gibt an, ob in dieser Zeit eine besondere Lernleistung ähnlich einer Schulung stattgefunden hat. "
        + "Fehlt das Feld oder ist es null, gilt false.",
        example = "false",
        nullable = true)
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
                .ticketReference(timeReport.getTicketReference())
                .build();
    }

    public DailyReportData withoutId(){
        return toBuilder().id(null).build();
    }

    public DailyReportData withTicketReference(String ticketReference) {
        return toBuilder().ticketReference(ticketReference).build();
    }

}
