package org.tb.dailyreport.rest;

import com.google.common.collect.Streams;
import com.opencsv.bean.CsvBindByPosition;
import com.opencsv.bean.CsvDate;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvException;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.platform.commons.function.Try;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.tb.common.csv.PositionAwareColumnMappingStrategy;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;

@Component
public class DailyWorkingReportCsvConverter implements HttpMessageConverter<List<DailyWorkingReportData>> {
    static final char COLUMN_SEPARATOR = ',';
    static final String TEXT_CSV_DAILY_WORKING_REPORT_VALUE = "text/csv+dailyworkingreport";
    static final MediaType TEXT_CSV_DAILY_WORKING_REPORT = new MediaType(
            TEXT_CSV_DAILY_WORKING_REPORT_VALUE.split("/")[0],
            TEXT_CSV_DAILY_WORKING_REPORT_VALUE.split("/")[1]
    );

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

    @Override
    @NonNull
    public List<DailyWorkingReportData> read(@Nullable Class<? extends List<DailyWorkingReportData>> clazz, @Nullable HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        if (inputMessage == null) {
            return List.of();
        }
        try (InputStreamReader reader = new InputStreamReader(inputMessage.getBody(), UTF_8)) {
            var rows = new CsvToBeanBuilder<CsvRow>(reader)
                    .withSeparator(COLUMN_SEPARATOR)
                    .withMappingStrategy(new PositionAwareColumnMappingStrategy<>(CsvRow.class, () -> CsvRow.builder().build()))
                    .withSkipLines(1)
                    .build()
                    .parse();
            return fromRows(rows);
        }
    }
    @SneakyThrows
    private static List<DailyWorkingReportData> fromRows(List<CsvRow> rows) {
        return rows.stream().collect(groupingBy(CsvRow::getDate)).entrySet().stream()
                .map(entry -> Map.entry(getUniqueWorkingDayRow(entry.getKey(), entry.getValue()), entry.getValue()))
                .map(entry ->  DailyWorkingReportData.builder()
                    .date(LocalDateTime.of(entry.getKey().getDate(), entry.getKey().getStartTime()))
                    .type(entry.getKey().getType())
                    .breakMinutes(entry.getKey().getBreakTime().getMinute() + entry.getKey().getBreakTime().getHour() * 60)
                    .dailyReports(timeReportsFromRows(entry.getValue()))
                    .build()
        ).toList();
    }

    @SneakyThrows
    private static CsvRow getUniqueWorkingDayRow(LocalDate day, List<CsvRow> rows) {
        var workingDayRows = rows.stream().filter(not(DailyWorkingReportCsvConverter::isEmptyWorkingDayRow)).toList();
        if (workingDayRows.isEmpty()) {
            throw new IOException("no start and breaktime found for " + DateUtils.format(day));
        }
        if (workingDayRows.size() > 1){
            throw new IOException("more than one start and breaktime found for " + DateUtils.format(day));
        }
        return workingDayRows.getFirst();
    }

    private static boolean isEmptyWorkingDayRow(CsvRow row){
        return row.getDate() == null || row.getStartTime() == null;
    }

    private static List<DailyReportData> timeReportsFromRows(List<CsvRow> rows){
        return rows.stream()
                .filter(not(DailyWorkingReportCsvConverter::isEmptyTimeReport))
                .map(row -> DailyReportData
                        .builder()
                        .employeeorderId(row.getEmployeeorderId())
                        .orderSign(row.getOrderSign())
                        .orderLabel(row.getOrderLabel())
                        .suborderSign(row.getSuborderSign())
                        .suborderLabel(row.getSuborderLabel())
                        .hours(row.getWorkingTime().getHour())
                        .minutes(row.getWorkingTime().getMinute())
                        .comment(row.getComment())
                        .build())
                .toList();
    }

    private static boolean isEmptyTimeReport(CsvRow row){
        return row.getEmployeeorderId() == null;
    }

    @Override
    public void write(@Nullable List<DailyWorkingReportData> dailyReportData, @Nullable MediaType contentType, @Nullable HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        if(dailyReportData == null || dailyReportData.isEmpty() || outputMessage == null){
            return;
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(outputMessage.getBody(), UTF_8)) {
            var rows = dailyReportData.stream().map(DailyWorkingReportCsvConverter::toRows).flatMap(List::stream).toList();
            new StatefulBeanToCsvBuilder<CsvRow>(writer)
                    .withSeparator(COLUMN_SEPARATOR)
                    .withMappingStrategy(new PositionAwareColumnMappingStrategy<>(CsvRow.class, () -> CsvRow.builder().build()))
                    .build()
                    .write(rows);
        }  catch (CsvException  e) {
            throw new HttpMessageNotWritableException("unable to write CSV", e);
        }
    }

    private static List<CsvRow> toRows(DailyWorkingReportData dailyWorkingReportData) {
        var rows = dailyWorkingReportData.getDailyReports().stream()
                .map(reportData -> toBooking(dailyWorkingReportData.getDate().toLocalDate(), reportData))
                .toList();

        if (rows.isEmpty()){
            return List.of(toWorkingDay(CsvRow.builder().build(), dailyWorkingReportData));
        } else {
            return Streams.concat(Stream.of(toWorkingDay(rows.getFirst(), dailyWorkingReportData)), rows.stream().skip(1)).toList();
        }
    }

    private static CsvRow toWorkingDay(CsvRow row, DailyWorkingReportData dailyWorkingReportData) {
        return row.toBuilder()
                .date(dailyWorkingReportData.getDate().toLocalDate())
                .startTime(dailyWorkingReportData.getDate().toLocalTime())
                .type(dailyWorkingReportData.getType())
                .breakTime(dailyWorkingReportData.getBreakMinutes() == null
                        ? null
                        : DateUtils.getTimeFromMinutes(dailyWorkingReportData.getBreakMinutes()))
                .build();
    }

    private static CsvRow toBooking(LocalDate day, DailyReportData reportData) {
        return CsvRow.builder()
                .date(day)
                .employeeorderId(reportData.getEmployeeorderId())
                .orderSign(reportData.getOrderSign())
                .orderLabel(reportData.getOrderLabel())
                .suborderSign(reportData.getSuborderSign())
                .suborderLabel(reportData.getSuborderLabel())
                .workingTime(LocalTime.of((int)reportData.getHours(), (int)reportData.getMinutes()))
                .comment(reportData.getComment())
                .build();
    }

    @Getter
    @Builder(toBuilder = true)
    @EqualsAndHashCode
    public static class CsvRow {

        @CsvDate("yyyy-MM-dd")
        @CsvBindByPosition(position = 0)
        private LocalDate date;
        @CsvDate("HH:mm")
        @CsvBindByPosition(position = 1)
        private LocalTime startTime;
        @CsvBindByPosition(position = 2)
        private WorkingDayType type;

        @CsvDate("HH:mm")
        @CsvBindByPosition(position = 3)
        private LocalTime breakTime;

        @CsvBindByPosition(position = 4)
        private Long employeeorderId;

        @CsvBindByPosition(position = 5)
        private String orderSign;
        @CsvBindByPosition(position = 6)
        private String orderLabel;

        @CsvBindByPosition(position = 7)
        private String suborderSign;
        @CsvBindByPosition(position = 8)
        private String suborderLabel;

        @CsvDate("HH:mm")
        @CsvBindByPosition(position = 9)
        private LocalTime workingTime;

        @CsvBindByPosition(position = 10)
        private String comment;
    }
}
