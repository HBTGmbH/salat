package de.hbt.salat.dailyreport.rest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static de.hbt.salat.common.GlobalConstants.TICKET_REFERENCE_MAX_LENGTH;
import static de.hbt.salat.common.exception.ErrorCode.TR_CSV_LINE_NOT_READABLE;
import static de.hbt.salat.common.exception.ErrorCode.TR_CSV_LINE_REJECTED;
import static de.hbt.salat.common.exception.ErrorCode.TR_CSV_VALUE_FORMAT_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.TR_CSV_VALUE_TOO_LONG;

import com.google.common.collect.Streams;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.opencsv.bean.CsvIgnore;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvException;
import com.opencsv.exceptions.CsvMalformedLineException;
import com.opencsv.exceptions.CsvValidationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType;
import de.hbt.salat.dailyreport.service.BookingOrderResolver;
import de.hbt.salat.employee.domain.Employee;

@Component
@AllArgsConstructor
@io.swagger.v3.oas.annotations.media.Schema(hidden = true)
public class DailyWorkingReportCsvConverter implements HttpMessageConverter<List<DailyWorkingReportData>> {

    static final char COLUMN_SEPARATOR = ',';
    /** Between several ticket references in one cell (#1326); a reference may contain blanks, so they cannot separate. */
    static final String TICKET_REFERENCE_SEPARATOR = ";";
    static final String TEXT_CSV_DAILY_WORKING_REPORT_VALUE = "text/csv+dailyworkingreport";
    static final MediaType TEXT_CSV_DAILY_WORKING_REPORT = new MediaType(
            TEXT_CSV_DAILY_WORKING_REPORT_VALUE.split("/")[0],
            TEXT_CSV_DAILY_WORKING_REPORT_VALUE.split("/")[1]
    );

    private final BookingOrderResolver bookingOrderResolver;
    private final AuthorizedUser authorizedUser;

    @Override
    public boolean canRead(@Nullable Class<?> clazz, @Nullable MediaType mediaType) {
        return TEXT_CSV_DAILY_WORKING_REPORT.isCompatibleWith(mediaType);
    }

    @Override
    public boolean canWrite(@Nullable Class<?> clazz, @Nullable MediaType mediaType) {
        return TEXT_CSV_DAILY_WORKING_REPORT.isCompatibleWith(mediaType);
    }

    @Override
    @NonNull
    public List<MediaType> getSupportedMediaTypes() {
        return List.of(TEXT_CSV_DAILY_WORKING_REPORT);
    }

    public record ReadResult(List<DailyWorkingReportData> reports, int linesRead) {}

    /**
     * Liest die Buchungen, wie die Datei sie nennt: mit der ID des Mitarbeiterauftrags oder mit Kürzeln,
     * ohne sie einem Auftrag zuzuordnen. So kommt eine Datei über die REST-API herein; zugeordnet wird
     * dort im {@link de.hbt.salat.dailyreport.service.DailyWorkingReportService}, nach den Regeln der API.
     */
    public ReadResult read(InputStream inputStream) throws IOException {
        var rows = readRows(inputStream);
        return new ReadResult(fromRows(rows, (row, booking) -> booking), rows.size());
    }

    /**
     * Liest die Datei des CSV-Imports der Oberfläche, die dem Mitarbeiter des ausgewählten Vertrags gehört,
     * und ordnet jede Buchung schon hier ihrem Mitarbeiterauftrag zu (#1142). Nur hier ist die Zeile
     * bekannt: eine Buchung, die sich nicht zuordnen lässt, wird mit ihrer Zeile gemeldet, bevor
     * irgendetwas gespeichert ist.
     *
     * <p>Der Service ordnet dieselben Buchungen beim Speichern noch einmal zu, dann über die ID, die hier
     * gesetzt wurde. Die Regel hängt damit nicht daran, dass jeder Aufrufer die Datei über diesen Weg liest.
     */
    public ReadResult read(InputStream inputStream, Employee employee) throws IOException {
        var rows = readRows(inputStream);
        return new ReadResult(fromRows(rows, (row, booking) -> assignedFor(employee, row, booking)), rows.size());
    }

    private DailyReportData assignedFor(Employee employee, CsvRow row, DailyReportData booking) {
        var day = row.getDate();
        try {
            return booking.assignedTo(bookingOrderResolver.resolveFor(employee, booking, day), day);
        } catch (InvalidDataException e) {
            throw new InvalidDataException(TR_CSV_LINE_REJECTED, row.getLine(), e.getMessages().getFirst());
        }
    }

    private List<CsvRow> readRows(InputStream inputStream) throws IOException {
        // copy input stream intro byte array
        var contentBytes = IOUtils.toByteArray(inputStream);
        char separator = evalSeparator(contentBytes);

        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(contentBytes), UTF_8)) {
            var csvToBean = new CsvToBeanBuilder<CsvRow>(reader)
                .withType(CsvRow.class)
                .withSeparator(separator)
                // Sammeln statt werfen (#1112): mit dem werfenden Handler steigt die Ausnahme im
                // Verarbeitungspool von opencsv auf, der Thread stirbt mit einem Stacktrace auf der
                // Konsole, und die Meldung, die der Aufrufer bekommt, hat die vollständige Rohzeile
                // angehängt. Gesammelt wird dieselbe Ausnahme hier ausgewertet.
                .withThrowExceptions(false)
                .build();
            List<CsvRow> rows;
            try {
                rows = csvToBean.parse();
            } catch (RuntimeException e) {
                // Einen Satz, den opencsv gar nicht zerlegen kann, meldet es weiter als
                // RuntimeException, deren Meldung die Rohzeile mitführt. Übernommen wird allein die
                // Zeilennummer, und die aus der Ausnahme, nicht aus ihrem Text (#1112).
                throw new InvalidDataException(TR_CSV_LINE_NOT_READABLE, lineNumberOf(e));
            }
            rejectUnreadableValues(csvToBean.getCapturedExceptions());
            var startLines = recordStartLines(contentBytes, separator);
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setLine(i < startLines.size() ? startLines.get(i) : 0);
            }
            return rows;
        }
    }

    /**
     * Die Zeile, in der jeder Datensatz der Datei beginnt, in der Reihenfolge, in der opencsv sie zu
     * Zeilen macht (#1142). Ein Kommentar in Anführungszeichen darf über mehrere Zeilen gehen; die Nummer
     * aus der Position abzuzählen, nennte danach jede Zeile falsch. Gelesen wird erst, wenn opencsv die
     * Datei ohne Fehler zerlegt hat, und mit demselben Trennzeichen: dann gibt es zu jeder Zeile einen
     * Datensatz.
     */
    private static List<Long> recordStartLines(byte[] content, char separator) throws IOException {
        var startLines = new ArrayList<Long>();
        try (var reader = new CSVReaderBuilder(new InputStreamReader(new ByteArrayInputStream(content), UTF_8))
                .withCSVParser(new CSVParserBuilder().withSeparator(separator).build())
                .build()) {
            reader.readNextSilently(); // header
            long start = reader.getLinesRead() + 1;
            while (reader.readNext() != null) {
                startLines.add(start);
                start = reader.getLinesRead() + 1;
            }
        } catch (CsvValidationException e) {
            throw new IOException(e);
        }
        return startLines;
    }

    /**
     * Die Zeilennummer, die in der Ursachenkette einer Ausnahme von opencsv steckt, oder {@code 0},
     * wo keine zu finden ist. Aus dem Meldungstext gelesen würde sie die Rohzeile mitbringen.
     */
    private static long lineNumberOf(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof CsvException csvException) {
                return csvException.getLineNumber();
            }
            if (cause instanceof CsvMalformedLineException malformedLine) {
                return malformedLine.getLineNumber();
            }
        }
        return 0;
    }

    /**
     * Ein Wert, den opencsv nicht lesen konnte, ist ein Eingabefehler der hochgeladenen Datei und
     * wird als {@link InvalidDataException} gemeldet, die der Aufrufer bereits behandelt (#1112).
     *
     * <p>Weitergereicht werden nur Zeilennummer, Spalte, der einzelne nicht lesbare Wert und die
     * erwarteten Formate. Die Meldung von opencsv bleibt außen vor: sie führt die vollständige
     * Rohzeile mit, und die gehört weder in eine Rückmeldung noch ins Log.
     *
     * <p>Geworfen wird, bevor irgendetwas gespeichert wurde — eine fehlerhafte Zeile lässt damit
     * die ganze Datei ungespeichert.
     */
    private void rejectUnreadableValues(List<CsvException> capturedExceptions) {
        if (capturedExceptions.isEmpty()) {
            return;
        }
        var first = capturedExceptions.getFirst();
        if (first instanceof CsvValueFormatException formatException) {
            throw new InvalidDataException(TR_CSV_VALUE_FORMAT_INVALID,
                first.getLineNumber(),
                formatException.getColumn(),
                formatException.getValue(),
                formatException.getExpectedFormats());
        }
        if (first instanceof CsvValueTooLongException tooLongException) {
            throw new InvalidDataException(TR_CSV_VALUE_TOO_LONG,
                first.getLineNumber(),
                tooLongException.getColumn(),
                tooLongException.getMaxLength());
        }
        throw new InvalidDataException(TR_CSV_LINE_NOT_READABLE, first.getLineNumber());
    }

    private char evalSeparator(byte[] content) throws IOException {
        String sample = new String(content, StandardCharsets.UTF_8);
        String[] lines = sample.split("\\r?\\n");
        // Kandidaten-Liste – hier erweitern, wenn nötig
        char[] candidates = { ',', ';', '\t', '|', ':' };

        char best = candidates[0];
        int maxCount = -1;

        int inspectLines = Math.min(lines.length, 1); // inspect only the header
        for (char sep : candidates) {
            int count = 0;
            String regex = Pattern.quote(String.valueOf(sep));
            for (int i = 0; i < inspectLines; i++) {
                // Anzahl der Trennzeichen in Zeile i
                count += lines[i].split(regex, -1).length - 1;
            }
            if (count > maxCount) {
                maxCount = count;
                best = sep;
            }
        }
        return best;
    }


    @Override
    @NonNull
    public List<DailyWorkingReportData> read(@Nullable Class<? extends List<DailyWorkingReportData>> clazz, @Nullable HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        if (inputMessage == null) {
            return List.of();
        }
        try {
            return read(inputMessage.getBody()).reports();
        } catch (InvalidDataException e) {
            // eine fehlerhafte Datei ist ein Fehler des Aufrufers: 400, nicht 500 (#1140)
            throw new HttpMessageNotReadableException(e.getMessage(), e, inputMessage);
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    /** What becomes of a booking once it is read: nothing over the REST API, its order in the UI import. */
    private interface BookingAssignment {
        DailyReportData assign(CsvRow row, DailyReportData booking);
    }

    @SneakyThrows
    private List<DailyWorkingReportData> fromRows(List<CsvRow> rows, BookingAssignment assignment) {
        return rows.stream()
                .filter(not(DailyWorkingReportCsvConverter::isEmptyRow))
                .collect(groupingBy(CsvRow::getDate)).entrySet().stream()
                // day by day, so that of several faulty lines the one reported is the same on every run
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.entry(getUniqueWorkingDayRow(entry.getKey(), entry.getValue()), entry.getValue()))
                .map(entry ->  DailyWorkingReportData.builder()
                    .date(entry.getKey().getDate())
                    .startTime(entry.getKey().getStartTime())
                    .type(entry.getKey().getType())
                    .breakDuration(entry.getKey().getBreakTime())
                    .dailyReports(timeReportsFromRows(entry.getKey().getDate(), entry.getValue(), assignment))
                    .build()
        ).toList();
    }

    @SneakyThrows
    private CsvRow getUniqueWorkingDayRow(LocalDate day, List<CsvRow> rows) {
        var workingDayRows = rows.stream().filter(not(this::isEmptyWorkingDayRow)).toList();
        if (workingDayRows.isEmpty()) {
            throw new IOException("no working day data found for " + DateUtils.format(day));
        }
        if (workingDayRows.size() > 1){
            throw new IOException("more than one working day data found for " + DateUtils.format(day));
        }
        return workingDayRows.getFirst();
    }

    private static boolean isEmptyRow(CsvRow row) {
        return row.getDate() == null;
    }

    private boolean isEmptyWorkingDayRow(CsvRow row) {
        if(row.getType() == WorkingDayType.NOT_WORKED) return false; // only date required for NOT_WORKED
        if(authorizedUser.isRestricted()) return row.getType() == null; // for external users, only the type is relevant

        // if start time or break time is missing, it is considered empty for other work types
        return row.getStartTime() == null || row.getBreakTime() == null;
    }

    private List<DailyReportData> timeReportsFromRows(LocalDate date, List<CsvRow> rows, BookingAssignment assignment){
        return rows.stream()
                .filter(not(DailyWorkingReportCsvConverter::isEmptyTimeReport))
                .map(row -> assignment.assign(row, DailyReportData
                        .builder()
                        .date(DateUtils.format(date))
                        .employeeorderId(row.getEmployeeorderId())
                        .orderSign(row.getOrderSign())
                        .orderLabel(row.getOrderLabel())
                        .suborderSign(row.getSuborderSign())
                        .suborderLabel(row.getSuborderLabel())
                        .employeeSign(row.getEmployeeSign())
                        .hours(row.getWorkingTime().getHour())
                        .minutes(row.getWorkingTime().getMinute())
                        .comment(row.getComment())
                        .training(Boolean.TRUE.equals(row.getTraining()))
                        .build()
                        .withTicketReferences(ticketReferencesOf(row.getTicketReference()))))
                .toList();
    }

    /**
     * The references of a cell (#1326): several separated by {@value #TICKET_REFERENCE_SEPARATOR}, as
     * {@link #toBooking} writes them. {@code null} where the file has no such column — that says nothing
     * about the references (#1140) —, an empty list for an empty cell.
     */
    static List<String> ticketReferencesOf(String cell) {
        if (cell == null) {
            return null;
        }
        return Arrays.stream(cell.split(TICKET_REFERENCE_SEPARATOR))
            .filter(reference -> !reference.isBlank())
            .toList();
    }

    /**
     * A row without a duration books nothing: it carries the working day only. A row with a duration is
     * a booking, even where it names no order (#1142) — dropping it would lose it without a word, so the
     * assignment rejects it instead.
     */
    private static boolean isEmptyTimeReport(CsvRow row){
        return row.getWorkingTime() == null;
    }

    @Override
    public void write(@Nullable List<DailyWorkingReportData> dailyReportData, @Nullable MediaType contentType, @Nullable HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        if(dailyReportData == null || dailyReportData.isEmpty() || outputMessage == null) {
            return;
        }

        var strategy = new HeaderColumnNameMappingStrategy() {
            {
                headerIndex.initializeHeaderIndex(new String[] {
                    "date","type","startTime","breakTime","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","workingTime","comment","ticketReference","training","employeeSign"
                });
            }
        };
        strategy.setType(CsvRow.class);

        try (OutputStreamWriter writer = new OutputStreamWriter(outputMessage.getBody(), UTF_8)) {
            var rows = dailyReportData.stream().map(DailyWorkingReportCsvConverter::toRows).flatMap(List::stream).toList();
            new StatefulBeanToCsvBuilder<CsvRow>(writer)
                .withSeparator(COLUMN_SEPARATOR)
                .withApplyQuotesToAll(false)
                .withMappingStrategy(strategy)
                .build()
                .write(rows);
        }  catch (CsvException  e) {
            throw new HttpMessageNotWritableException("unable to write CSV", e);
        }
    }

    private static List<CsvRow> toRows(DailyWorkingReportData dailyWorkingReportData) {
        var rows = dailyWorkingReportData.getDailyReports().stream()
                .map(reportData -> toBooking(dailyWorkingReportData.getDate(), reportData))
                .toList();

        if (rows.isEmpty()){
            return List.of(toWorkingDay(new CsvRow(), dailyWorkingReportData));
        } else {
            return Streams.concat(Stream.of(toWorkingDay(rows.getFirst(), dailyWorkingReportData)), rows.stream().skip(1)).toList();
        }
    }

    private static CsvRow toWorkingDay(CsvRow row, DailyWorkingReportData workingReportData) {
        return new CsvRow(
            workingReportData.getDate(),
            workingReportData.getType(),
            workingReportData.getStartTime(),
            workingReportData.getBreakDuration(),
            row.getEmployeeorderId(),
            row.getOrderSign(),
            row.getOrderLabel(),
            row.getSuborderSign(),
            row.getSuborderLabel(),
            row.getWorkingTime(),
            row.getComment(),
            row.getTicketReference(),
            row.getTraining(),
            row.getEmployeeSign(),
            row.getLine()
        );
    }

    private static CsvRow toBooking(LocalDate day, DailyReportData reportData) {
        return new CsvRow(
            day,
            null,
            null,
            null,
            reportData.getEmployeeorderId(),
            reportData.getOrderSign(),
            reportData.getOrderLabel(),
            reportData.getSuborderSign(),
            reportData.getSuborderLabel(),
            LocalTime.of((int)reportData.getHours(), (int)reportData.getMinutes()),
            reportData.getComment(),
            reportData.getTicketReferences() == null || reportData.getTicketReferences().isEmpty()
                ? null : String.join(TICKET_REFERENCE_SEPARATOR, reportData.getTicketReferences()),
            reportData.isTraining(),
            reportData.getEmployeeSign(),
            0
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CsvRow {
        @CsvCustomBindByName(column = "date", converter = LocalDateConverter.class)
        private LocalDate date;
        @CsvBindByName
        private WorkingDayType type;
        @CsvCustomBindByName(converter = LocalTimeConverter.class)
        private LocalTime startTime;
        @CsvCustomBindByName(converter = LocalTimeConverter.class)
        private LocalTime breakTime;
        @CsvBindByName
        private Long employeeorderId;
        @CsvBindByName
        private String orderSign;
        @CsvBindByName
        private String orderLabel;
        @CsvBindByName
        private String suborderSign;
        @CsvBindByName
        private String suborderLabel;
        @CsvCustomBindByName(converter = LocalTimeConverter.class)
        private LocalTime workingTime;
        @CsvBindByName
        private String comment;
        /** {@code null} where the file has no such column, empty where the column is empty (#1140). */
        @CsvCustomBindByName(converter = TicketReferenceConverter.class)
        private String ticketReference;
        /** {@code null} on a row without a booking and where the file has no such column (#1140). */
        @CsvCustomBindByName(converter = TrainingFlagConverter.class)
        private Boolean training;
        /** A new column at the end (#1142); {@code null} where the file has none. */
        @CsvBindByName
        private String employeeSign;
        /** The line of the file the row begins in, for messages; not a column. */
        @CsvIgnore
        private long line;
    }

    /**
     * Ein Wert der hochgeladenen Datei, den keines der erwarteten Formate liest (#1112).
     *
     * <p>Spalte und erwartete Formate hängen an der Ausnahme, statt nur in ihrem Meldungstext zu
     * stehen: die Rückmeldung an die hochladende Person setzt sich daraus zusammen, und sie aus
     * einem Text wieder herauszulesen hieße, die Meldung zweimal zu pflegen.
     */
    @Getter
    public static class CsvValueFormatException extends CsvDataTypeMismatchException {

        private final String column;
        private final String value;
        private final String expectedFormats;

        CsvValueFormatException(String column, String value, Class<?> destinationClass, String expectedFormats) {
            super(value, destinationClass,
                "column '" + column + "': value '" + value + "' matches none of the expected formats " + expectedFormats);
            this.column = column;
            this.value = value;
            this.expectedFormats = expectedFormats;
        }
    }

    /**
     * Ein Wert der hochgeladenen Datei, der länger ist, als die Buchung ihn speichern kann (#1140).
     * Gemeldet wird er wie ein nicht lesbarer Wert, mit Zeile und Spalte.
     */
    @Getter
    public static class CsvValueTooLongException extends CsvConstraintViolationException {

        private final String column;
        private final int maxLength;

        CsvValueTooLongException(String column, int maxLength) {
            super("column '" + column + "': value exceeds " + maxLength + " characters");
            this.column = column;
            this.maxLength = maxLength;
        }
    }

    /**
     * Der Name der Spalte, die gerade gelesen wird. opencsv setzt das Feld vor der Umwandlung; wo
     * es fehlt, ist die Spalte unbekannt — das ist kein Grund, die Meldung ausfallen zu lassen.
     */
    private static String columnOf(AbstractBeanField<?, String> beanField) {
        var field = beanField.getField();
        return field != null ? field.getName() : "?";
    }

    public static class LocalDateConverter extends AbstractBeanField<LocalDate, String> {
        private static final DateTimeFormatter DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        static final String EXPECTED_FORMATS = "yyyy-MM-dd, dd.MM.yyyy";


        @Override
        protected LocalDate convert(String value) throws CsvDataTypeMismatchException {
            if (value == null || value.isBlank()) return null;
            try {
                return LocalDate.parse(value, ISO);
            } catch (DateTimeParseException e) {
                try {
                    return LocalDate.parse(value, DE);
                } catch (DateTimeParseException e2) {
                    throw new CsvValueFormatException(columnOf(this), value, LocalDate.class, EXPECTED_FORMATS);
                }
            }
        }

        @Override
        protected String convertToWrite(Object value) {
            if (value == null) return "";
            return ((LocalDate)value).format(ISO);
        }
    }

    /**
     * Prüft die Länge jeder Referenz schon beim Lesen, weil nur hier Zeile und Spalte bekannt sind;
     * mehrere stehen durch Semikolon getrennt in der Zelle (#1326). Gespeichert werden sie wie in der
     * Buchungsmaske über {@code TicketReferences.normalize}, die dieselbe Grenze noch einmal zieht; ein
     * leerer Wert bleibt leer und heißt „keine Referenz“.
     */
    public static class TicketReferenceConverter extends AbstractBeanField<String, String> {

        @Override
        protected String convert(String value) throws CsvConstraintViolationException {
            if (value != null && Arrays.stream(value.split(TICKET_REFERENCE_SEPARATOR))
                    .anyMatch(reference -> reference.trim().length() > TICKET_REFERENCE_MAX_LENGTH)) {
                throw new CsvValueTooLongException(columnOf(this), TICKET_REFERENCE_MAX_LENGTH);
            }
            return value;
        }
    }

    /**
     * {@code true} oder {@code false}, gleich in welcher Schreibung; leer heißt {@code false} (#1140).
     * Jeder andere Wert wird gemeldet statt als {@code false} gelesen — ein Tippfehler darf eine
     * Schulungsbuchung nicht still zu einer gewöhnlichen machen.
     */
    public static class TrainingFlagConverter extends AbstractBeanField<Boolean, String> {
        static final String EXPECTED_FORMATS = "true, false";

        @Override
        protected Boolean convert(String value) throws CsvDataTypeMismatchException {
            if (value == null || value.isBlank()) return null;
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                default -> throw new CsvValueFormatException(columnOf(this), value, Boolean.class, EXPECTED_FORMATS);
            };
        }

        @Override
        protected String convertToWrite(Object value) {
            return value == null ? "" : value.toString();
        }
    }

    public static class LocalTimeConverter extends AbstractBeanField<LocalTime, String> {
        private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
        private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");
        static final String EXPECTED_FORMATS = "HH:mm, HH:mm:ss";


        @Override
        protected LocalTime convert(String value) throws CsvDataTypeMismatchException {
            if (value == null || value.isBlank()) return null;
            try {
                return LocalTime.parse(value, HH_MM);
            } catch (DateTimeParseException e) {
                try {
                    return LocalTime.parse(value, HH_MM_SS).truncatedTo(ChronoUnit.MINUTES);
                } catch (DateTimeParseException e2) {
                    throw new CsvValueFormatException(columnOf(this), value, LocalTime.class, EXPECTED_FORMATS);
                }
            }
        }

        @Override
        protected String convertToWrite(Object value) {
            if (value == null) return "";
            return ((LocalTime)value).format(HH_MM);
        }
    }

}
