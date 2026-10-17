package de.hbt.salat.dailyreport.rest;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static de.hbt.salat.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;
import static de.hbt.salat.common.exception.ErrorCode.TR_DURATION_HOURS_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.TR_DURATION_MINUTES_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.TR_SURCHARGE_KIND_INVALID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvCustomBindByPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.json.HoursMinutes;
import de.hbt.salat.common.util.DataValidationUtils;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.SurchargeKind;

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

    /**
     * The booked duration (#1398). Where it is missing, {@link #hours} and {@link #minutes} of an older client
     * stand for it; where it is given, it wins over them. In CSV it is the only form: column 6, the minutes
     * column that followed it is gone.
     */
    @HoursMinutes
    @CsvCustomBindByPosition(position = 6, converter = DailyWorkingReportCsvConverter.DurationConverter.class)
    @Schema(description = "Gebuchte Dauer im Format HH:mm, auch ab 24 Stunden. Hat beim Schreiben Vorrang vor hours und "
        + "minutes; fehlt sie, gelten diese.",
        type = "string", pattern = HoursMinutes.PATTERN, example = "04:30", nullable = true)
    private Duration duration;

    /** Kept for clients from before #1398, read from {@link #getDuration()}; not in CSV. */
    @Deprecated
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Schema(description = "Abgekündigt, ersetzt durch duration: Stunden der gebuchten Dauer ohne Minuten. Beim Schreiben "
        + "nur ausgewertet, wenn duration fehlt.",
        example = "4", deprecated = true)
    private long hours;

    /** Kept for clients from before #1398, read from {@link #getDuration()}; not in CSV. */
    @Deprecated
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Schema(description = "Abgekündigt, ersetzt durch duration: Minuten der gebuchten Dauer zusätzlich zu den Stunden. "
        + "Beim Schreiben nur ausgewertet, wenn duration fehlt.",
        example = "30", deprecated = true)
    private long minutes;

    @CsvBindByPosition(position = 7)
    @Schema(description = "Kommentar oder Beschreibung der durchgeführten Arbeit", example = "ERP-3032 Implementierung der REST-API für Zeiterfassung")
    private String comment;

    /**
     * {@code null} is not the same as "no reference" (#1140): it is what a file without the column
     * and a client that does not know the field send, and neither of them has changed a reference
     * they never saw. {@link de.hbt.salat.dailyreport.service.DailyWorkingReportService} resolves it against
     * the stored booking; an empty text is what says "no reference".
     */
    @CsvBindByPosition(position = 8)
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
    @CsvCustomBindByPosition(position = 9, converter = DailyWorkingReportCsvConverter.TrainingFlagConverter.class)
    @Schema(description = "Gibt an, ob in dieser Zeit eine besondere Lernleistung ähnlich einer Schulung stattgefunden hat. "
        + "Fehlt das Feld oder ist es null, gilt false.",
        example = "false",
        nullable = true)
    private boolean training;

    /** A new column at the end (#1142). */
    @CsvBindByPosition(position = 10)
    @Schema(description = "Kürzel des Mitarbeiters, dem die Buchung gehört. Beim Schreiben ohne employeeorderId "
        + "bestimmt es zusammen mit suborderSign und dem Leistungsdatum den Mitarbeiterauftrag; fehlt es, gilt der "
        + "angemeldete Mitarbeiter. Gebucht werden darf nur, wofür die Berechtigung auch über die ID reicht.",
        example = "abc",
        nullable = true)
    private String employeeSign;

    /**
     * The time surcharge flag of the booking (#1469), a kind's name. Like the ticket reference, {@code null} says
     * nothing about it — a file without the column, a client that does not know the field — and an empty text
     * says "none"; {@link de.hbt.salat.dailyreport.service.DailyWorkingReportService} resolves the former against
     * the stored booking. A new column at the end.
     */
    @CsvBindByPosition(position = 11)
    @Schema(description = "Zeitzuschlag der Buchung: NIGHT, SATURDAY, SUNDAY oder HOLIDAY, null ohne Zuschlag. Beim Schreiben "
        + "setzt ein Wert die Art, ein leerer Text heißt \"kein Zuschlag\". Fehlt das Feld oder ist es null, behält eine "
        + "vorhandene Buchung, die der übergebenen bis auf Zuschlag und Referenzen gleicht, ihren Zuschlag. Angeboten wird "
        + "eine Art nur, wo der Unterauftrag einen Satz für sie hat; SATURDAY, SUNDAY und HOLIDAY nur an ihrem Tag. Den "
        + "Satz bestimmt der Unterauftrag; ersetzt die Buchung eine gespeicherte derselben Art, behält sie deren Satz.",
        example = "NIGHT",
        nullable = true,
        allowableValues = {"NIGHT", "SATURDAY", "SUNDAY", "HOLIDAY", ""})
    private String surchargeKind;

    /**
     * The rate the flag was stored with (#1469), read only: the rate follows from the suborder, nobody sends it. It
     * takes no part in comparing an imported booking with a stored one.
     */
    @CsvBindByPosition(position = 12)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @EqualsAndHashCode.Exclude
    @Schema(description = "Satz des Zeitzuschlags in Prozent, wie er beim Kennzeichnen an der Buchung festgehalten wurde; "
        + "null ohne Zuschlag. Nur lesend, beim Schreiben ignoriert.",
        example = "25", nullable = true, accessMode = Schema.AccessMode.READ_ONLY)
    private Integer surchargePercent;

    /**
     * The credit on the working time account, duration × rate (#1469), read only like the rate. A duration like
     * every other one at the interface (#1398): HH:mm, not minutes as a number.
     */
    @HoursMinutes
    @CsvCustomBindByPosition(position = 13, converter = DailyWorkingReportCsvConverter.DurationConverter.class)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @EqualsAndHashCode.Exclude
    @Schema(description = "Gutschrift des Zeitzuschlags aufs Arbeitszeitkonto im Format HH:mm (Dauer × Satz, ganze Minuten); "
        + "00:00 ohne Zuschlag. Nur lesend, beim Schreiben ignoriert.",
        type = "string", pattern = HoursMinutes.PATTERN, example = "01:00", nullable = true,
        accessMode = Schema.AccessMode.READ_ONLY)
    private Duration surchargeCredit;

    public static DailyReportData valueOf(TimereportDTO timeReport) {
        return DailyReportData.builder()
                .id(timeReport.getId())
                .employeeorderId(timeReport.getEmployeeorderId())
                .date(DateUtils.format(timeReport.getReferenceday()))
                .orderLabel(timeReport.getCustomerorderDescription())
                .suborderLabel(timeReport.getSuborderDescription())
                .comment(timeReport.getTaskdescription())
                .training(timeReport.isTraining())
                .duration(timeReport.getDuration())
                .suborderSign(timeReport.getCompleteOrderSign())
                .orderSign(timeReport.getCustomerorderSign())
                .ticketReference(timeReport.getTicketReferences().isEmpty() ? null : timeReport.getTicketReferences().getFirst())
                .ticketReferences(List.copyOf(timeReport.getTicketReferences()))
                .employeeSign(timeReport.getEmployeeSign())
                .surchargeKind(timeReport.getSurchargeKind() != null ? timeReport.getSurchargeKind().name() : null)
                .surchargePercent(timeReport.getSurchargePercent())
                .surchargeCredit(timeReport.getSurchargeCredit())
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

    /** This booking flagged with {@code kind}; {@code null} for none, which is also how one compares that says nothing. */
    public DailyReportData withSurchargeKind(SurchargeKind kind) {
        return toBuilder().surchargeKind(kind != null ? kind.name() : null).build();
    }

    /** Whether the booking says anything about its time surcharge flag (#1469), an empty text for "none" included. */
    public boolean givesSurchargeKind() {
        return surchargeKind != null;
    }

    /**
     * The kind the booking names (#1469), {@code null} for none or nothing said.
     *
     * @throws InvalidDataException if the text names no kind
     */
    public SurchargeKind givenSurchargeKind() {
        if (surchargeKind == null || surchargeKind.isBlank()) {
            return null;
        }
        try {
            return SurchargeKind.valueOf(surchargeKind.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidDataException(TR_SURCHARGE_KIND_INVALID, surchargeKind);
        }
    }

    /** The duration the booking states: {@link #duration}, or else what {@link #hours} and {@link #minutes} add up to. */
    public Duration getDuration() {
        return duration != null ? duration : Duration.ofHours(hours).plusMinutes(minutes);
    }

    /** The hours of {@link #getDuration()}, for clients from before #1398. */
    @Deprecated
    public long getHours() {
        return getDuration().toHours();
    }

    /** The minutes part of {@link #getDuration()}, for clients from before #1398. */
    @Deprecated
    public long getMinutes() {
        return getDuration().toMinutesPart();
    }

    /**
     * The booked duration, checked (#1400, #1398). {@link #duration} comes checked from JSON and CSV; hours and
     * minutes apart exist only for older clients: neither may be negative, and a minutes part of 60 or more
     * simply adds up.
     */
    public Duration toDuration() {
        if (duration != null) {
            DataValidationUtils.isTrue(!duration.isNegative(), TR_DURATION_HOURS_INVALID);
            return duration;
        }
        DataValidationUtils.isTrue(hours >= 0, TR_DURATION_HOURS_INVALID);
        DataValidationUtils.isTrue(minutes >= 0, TR_DURATION_MINUTES_INVALID);
        return getDuration();
    }

}
