package de.hbt.salat.dailyreport.rest;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.csv.PositionAwareColumnMappingStrategy;
import de.hbt.salat.common.exception.InvalidDataException;

@Component
public class DailyReportCsvConverter implements HttpMessageConverter<List<DailyReportData>> {
    static final char COLUMN_SEPARATOR = ',';
    static final String TEXT_CSV_DAILY_REPORT_VALUE = "text/csv+dailyreport";
    static final MediaType TEXT_CSV_DAILY_REPORT = new MediaType(
            TEXT_CSV_DAILY_REPORT_VALUE.split("/")[0],
            TEXT_CSV_DAILY_REPORT_VALUE.split("/")[1]
    );

    /**
     * Since #1398 column 6 carries the duration as HH:mm, and the minutes column after it is gone. The columns are
     * bound by position and the header row is skipped; a file of the former format would be read shifted by one. It
     * is rejected by its header instead, with the column that replaces it.
     */
    private static final Map<String, String> FORMER_COLUMNS = Map.of(
        "hours", "duration",
        "minutes", "duration");

    @Override
    public boolean canRead(@Nullable Class<?> clazz, @Nullable MediaType mediaType) {
        return TEXT_CSV_DAILY_REPORT.isCompatibleWith(mediaType);
    }

    @Override
    public boolean canWrite(@Nullable Class<?> clazz, @Nullable MediaType mediaType) {
        return TEXT_CSV_DAILY_REPORT.isCompatibleWith(mediaType);
    }

    @Override
    @NonNull
    public List<MediaType> getSupportedMediaTypes() {
        return List.of(TEXT_CSV_DAILY_REPORT);
    }

    @Override
    @NonNull
    public List<DailyReportData> read(@Nullable Class<? extends List<DailyReportData>> clazz, @Nullable HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        if (inputMessage == null) {
            return List.of();
        }
        var content = inputMessage.getBody().readAllBytes();
        try {
            DailyWorkingReportCsvConverter.rejectFormerColumns(content, COLUMN_SEPARATOR, FORMER_COLUMNS);
        } catch (InvalidDataException e) {
            throw new HttpMessageNotReadableException(e.getMessage(), e, inputMessage);
        }
        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(content), UTF_8)) {
            var csvToBean = new CsvToBeanBuilder<DailyReportData>(reader)
                    .withSeparator(COLUMN_SEPARATOR)
                    .withMappingStrategy(new PositionAwareColumnMappingStrategy<>(DailyReportData.class, () -> DailyReportData.builder().build()))
                    .withSkipLines(1)
                    // collect instead of throw, as in DailyWorkingReportCsvConverter (#1112): thrown, the
                    // exception kills a worker thread of opencsv with a stack trace on the console
                    .withThrowExceptions(false)
                    .build();
            var bookings = csvToBean.parse();
            if (!csvToBean.getCapturedExceptions().isEmpty()) {
                // a value that cannot be read is the caller's error: 400, not 500 (#1140)
                var first = csvToBean.getCapturedExceptions().getFirst();
                throw new HttpMessageNotReadableException("unable to read CSV line " + first.getLineNumber(), first, inputMessage);
            }
            return bookings.stream().map(DailyReportCsvConverter::withReferencesOfTheCell).toList();
        }
    }

    @Override
    public void write(@Nullable List<DailyReportData> dailyReportData, @Nullable MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        try (OutputStreamWriter writer = new OutputStreamWriter(outputMessage.getBody(), "UTF-8")) {
            new StatefulBeanToCsvBuilder<DailyReportData>(writer)
                    .withSeparator(COLUMN_SEPARATOR)
                    .withMappingStrategy(new PositionAwareColumnMappingStrategy<>(DailyReportData.class, () -> DailyReportData.builder().build()))
                    .build()
                    .write(dailyReportData == null ? List.of() : dailyReportData.stream()
                        .map(DailyReportCsvConverter::withAllReferencesInTheCell).toList());
        }  catch (CsvException  e) {
            throw new HttpMessageNotWritableException("unable to write CSV", e);
        }
    }

    /**
     * The column {@code ticketReference} carries every reference of a booking, separated by
     * {@value DailyWorkingReportCsvConverter#TICKET_REFERENCE_SEPARATOR} as in the CSV of the working days
     * (#1472); in JSON the field of that name stays the first reference for clients that know only one.
     */
    private static DailyReportData withAllReferencesInTheCell(DailyReportData booking) {
        var references = booking.getTicketReferences();
        return booking.toBuilder()
            .ticketReference(references == null || references.isEmpty()
                ? null : String.join(DailyWorkingReportCsvConverter.TICKET_REFERENCE_SEPARATOR, references))
            .build();
    }

    /** The references of the cell (#1472): {@code null} where the file has no such column, none for an empty cell. */
    private static DailyReportData withReferencesOfTheCell(DailyReportData booking) {
        if (booking.getTicketReference() == null) {
            return booking;
        }
        return booking.withTicketReferences(DailyWorkingReportCsvConverter.ticketReferencesOf(booking.getTicketReference()));
    }
}
