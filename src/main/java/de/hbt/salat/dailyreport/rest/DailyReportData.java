package de.hbt.salat.dailyreport.rest;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static de.hbt.salat.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvCustomBindByPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.order.domain.Employeeorder;

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

    /**
     * Optional since #1142: without it, {@link #suborderSign} and {@link #employeeSign} name the order.
     * Where it is given it takes precedence, and the signs are not evaluated — a client that sends
     * both, as before, keeps working even when its signs are stale.
     */
    @CsvBindByPosition(position = 1)
    @Schema(description = "ID des Mitarbeiterauftrags. Optional: fehlt sie, bestimmen suborderSign und employeeSign "
        + "zusammen mit dem Datum den Mitarbeiterauftrag. Ist sie angegeben, hat sie Vorrang, und die Kürzel werden "
        + "beim Schreiben nicht ausgewertet.",
        example = "78",
        nullable = true)
    private Long employeeorderId;

    @CsvBindByPosition(position = 2)
    @Schema(description = "Kürzel des Kundenauftrags. Beim Schreiben ignoriert.", example = "4711")
    private String orderSign;
    @CsvBindByPosition(position = 3)
    @Schema(description = "Bezeichnung des Kundenauftrags. Beim Schreiben ignoriert.", example = "Softwareentwicklung ERP-System")
    private String orderLabel;

    @CsvBindByPosition(position = 4)
    @Schema(description = "Vollständiges Kürzel des Unterauftrags. Beim Schreiben ohne employeeorderId wird unter den "
        + "am Leistungsdatum gültigen Mitarbeiteraufträgen des am Leistungsdatum gültigen Vertrags von employeeSign der "
        + "gewählt, dessen Unterauftrag genau dieses Kürzel trägt; passt keiner oder passen mehrere, wird die Buchung "
        + "abgelehnt. Kürzel können sich ändern: Daten, die vor einer Umbenennung gelesen wurden, passen danach nicht "
        + "mehr oder auf einen anderen Auftrag, der das alte Kürzel inzwischen trägt. Die ID bleibt dagegen gleich.",
        example = "4711/01")
    private String suborderSign;
    @CsvBindByPosition(position = 5)
    @Schema(description = "Bezeichnung des Unterauftrags. Beim Schreiben ignoriert.", example = "API-Entwicklung")
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
     * they never saw. {@link de.hbt.salat.dailyreport.service.DailyWorkingReportService} resolves it against
     * the stored booking; an empty text is what says "no reference".
     */
    @CsvBindByPosition(position = 9)
    @Schema(description = "Erste Ticket-Referenz, für Clients, die nur eine kennen; höchstens "
        + TICKET_REFERENCE_MAX_LENGTH + " Zeichen. Beim Lesen die erste aus ticketReferences, null ohne Referenz. "
        + "Beim Schreiben nur ausgewertet, wenn ticketReferences fehlt: dann die einzige Referenz, ein leerer Text "
        + "heißt \"keine Referenz\". Fehlen beide Felder oder sind sie null, behält eine vorhandene Buchung, die "
        + "der übergebenen bis auf die Referenzen gleicht, ihre Referenzen.",
        example = "ERP-3032",
        maxLength = TICKET_REFERENCE_MAX_LENGTH,
        nullable = true)
    private String ticketReference;

    /**
     * All references of the booking (#1326). {@code null} says nothing about them, like a missing
     * {@link #ticketReference}; an empty list says "none".
     */
    @Schema(description = "Ticket-Referenzen der Buchung in ihrer Reihenfolge, je höchstens "
        + TICKET_REFERENCE_MAX_LENGTH + " Zeichen; ein Ticket-Schlüssel wird in Großbuchstaben gespeichert. "
        + "Wie viele erlaubt sind, legt der Unterauftrag fest. Beim Schreiben heißt eine leere Liste \"keine "
        + "Referenz\"; fehlt das Feld, gilt ticketReference.",
        example = "[\"ERP-3032\", \"ERP-3040\"]",
        nullable = true)
    private List<String> ticketReferences;

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

    /** A new column at the end (#1142): positions 0-10 stay where existing clients expect them. */
    @CsvBindByPosition(position = 11)
    @Schema(description = "Kürzel des Mitarbeiters, dem die Buchung gehört. Beim Schreiben ohne employeeorderId "
        + "bestimmt es zusammen mit suborderSign und dem Leistungsdatum den Mitarbeiterauftrag; fehlt es, gilt der "
        + "angemeldete Mitarbeiter. Gebucht werden darf nur, wofür die Berechtigung auch über die ID reicht.",
        example = "abc",
        nullable = true)
    private String employeeSign;

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
                .ticketReference(timeReport.getTicketReferences().isEmpty() ? null : timeReport.getTicketReferences().getFirst())
                .ticketReferences(List.copyOf(timeReport.getTicketReferences()))
                .employeeSign(timeReport.getEmployeeSign())
                .build();
    }

    /**
     * This booking as {@link #valueOf} renders a stored one on {@code employeeorder} and {@code day}: every
     * value that follows from the order is taken from it, whatever the booking said (#1142). Import and REST
     * API compare incoming with stored bookings by {@link #equals}; a booking named by its signs, or carrying
     * stale labels, would otherwise never equal the one it stands for and count as new.
     */
    public DailyReportData assignedTo(Employeeorder employeeorder, LocalDate day) {
        var suborder = employeeorder.getSuborder();
        return toBuilder()
                .id(null)
                .date(DateUtils.format(day))
                .employeeorderId(employeeorder.getId())
                .orderSign(suborder.getCustomerorder().getSign())
                .orderLabel(suborder.getCustomerorder().getShortdescription())
                .suborderSign(suborder.getCompleteOrderSign())
                .suborderLabel(suborder.getShortdescription())
                .employeeSign(employeeorder.getEmployeecontract().getEmployee().getSign())
                .build();
    }

    public DailyReportData withoutId(){
        return toBuilder().id(null).build();
    }

    /**
     * This booking with {@code references} in both fields, the first as {@link #ticketReference}; {@code null}
     * empties both, which is how a booking compares that says nothing about its references.
     */
    public DailyReportData withTicketReferences(List<String> references) {
        if (references == null) {
            return toBuilder().ticketReference(null).ticketReferences(null).build();
        }
        return toBuilder()
                .ticketReference(references.isEmpty() ? null : references.getFirst())
                .ticketReferences(List.copyOf(references))
                .build();
    }

    /**
     * What the booking says about its references (#1140, #1326): the list where it is given, else the single
     * reference of an older client, else {@code null} — nothing said.
     */
    public List<String> givenTicketReferences() {
        if (ticketReferences != null) {
            return ticketReferences;
        }
        return ticketReference != null ? List.of(ticketReference) : null;
    }

}
