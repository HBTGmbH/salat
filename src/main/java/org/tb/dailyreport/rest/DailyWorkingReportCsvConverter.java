package org.tb.dailyreport.rest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.collect.Streams;
import com.opencsv.bean.AbstractBeanField;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvCustomBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvException;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Data;
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
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

@Component
@AllArgsConstructor
public class DailyWorkingReportCsvConverter implements HttpMessageConverter<List<DailyWorkingReportData>> {

    private final EmployeeorderService employeeorderService;
    private final AuthorizedUser authorizedUser;

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

    public List<DailyWorkingReportData> read(InputStream inputStream) throws IOException {
        // copy input stream intro byte array
        var contentBytes = IOUtils.toByteArray(inputStream);
        char separator = evalSeparator(contentBytes);

        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(contentBytes), UTF_8)) {
            var rows = new CsvToBeanBuilder<CsvRow>(reader)
                .withType(CsvRow.class)
                .withSeparator(separator)
                .build()
                .parse();
            return fromRows(rows);
        }
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
            return read(inputMessage.getBody());
        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    private List<DailyWorkingReportData> fromRows(List<CsvRow> rows) {
        return rows.stream()
                .filter(not(DailyWorkingReportCsvConverter::isEmptyRow))
                .collect(groupingBy(CsvRow::getDate)).entrySet().stream()
                .map(entry -> Map.entry(getUniqueWorkingDayRow(entry.getKey(), entry.getValue()), entry.getValue()))
                .map(entry ->  DailyWorkingReportData.builder()
                    .date(entry.getKey().getDate())
                    .startTime(entry.getKey().getStartTime())
                    .type(entry.getKey().getType())
                    .breakDuration(entry.getKey().getBreakTime())
                    .dailyReports(timeReportsFromRows(entry.getKey().getDate(), entry.getValue()))
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

    private static boolean isEmptyRow(CsvRow row) {
        return row.getDate() == null;
    }

    private static boolean isEmptyWorkingDayRow(CsvRow row) {
        if(row.getType() == WorkingDayType.NOT_WORKED) return false; // only date required for NOT_WORKED

        // if start time or break time is missing, it is considered empty for other work types
        return row.getStartTime() == null || row.getBreakTime() == null;
    }

    private List<DailyReportData> timeReportsFromRows(LocalDate date, List<CsvRow> rows){
        return rows.stream()
                .filter(not(DailyWorkingReportCsvConverter::isEmptyTimeReport))
                .map(row -> {
                    long employeeorderId = -1;
                    String customerorderSign;
                    String customerorderLabel;
                    String suborderSign;
                    String suborderLabel;

                    Employeeorder employeeorder;

                    if(row.getEmployeeorderId() != null) {
                        employeeorder = employeeorderService.getEmployeeorderById(row.employeeorderId);
                    } else {
                        employeeorder = employeeorderService.getEmployeeorderByEmployeeAndSuborder(authorizedUser.getSign(), row.getSuborderSign(), date);
                    }

                    employeeorderId = employeeorder.getId();
                    customerorderSign = employeeorder.getSuborder().getCustomerorder().getSign();
                    customerorderLabel = employeeorder.getSuborder().getCustomerorder().getShortdescription();
                    suborderSign = employeeorder.getSuborder().getCompleteOrderSign();
                    suborderLabel = employeeorder.getSuborder().getShortdescription();

                    return DailyReportData
                        .builder()
                        .date(DateUtils.format(date))
                        .employeeorderId(employeeorderId)
                        .orderSign(customerorderSign)
                        .orderLabel(customerorderLabel)
                        .suborderSign(suborderSign)
                        .suborderLabel(suborderLabel)
                        .hours(row.getWorkingTime().getHour())
                        .minutes(row.getWorkingTime().getMinute())
                        .comment(row.getComment())
                        .build();
                })
                .toList();
    }

    private static boolean isEmptyTimeReport(CsvRow row){
        return row.getEmployeeorderId() == null && row.getSuborderSign() == null;
    }

    @Override
    public void write(@Nullable List<DailyWorkingReportData> dailyReportData, @Nullable MediaType contentType, @Nullable HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        if(dailyReportData == null || dailyReportData.isEmpty() || outputMessage == null) {
            return;
        }

        var strategy = new HeaderColumnNameMappingStrategy() {
            {
                headerIndex.initializeHeaderIndex(new String[] {
                    "date","type","startTime","breakTime","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","workingTime","comment"
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
            row.getComment()
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
            reportData.getComment()
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
    }

    public static class LocalDateConverter extends AbstractBeanField<LocalDate, String> {
        private static final DateTimeFormatter DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");


        @Override
        protected LocalDate convert(String value) throws CsvDataTypeMismatchException {
            if (value == null || value.isBlank()) return null;
            try {
                return LocalDate.parse(value, ISO);
            } catch (DateTimeParseException e) {
                try {
                    return LocalDate.parse(value, DE);
                } catch (DateTimeParseException e2) {
                    throw new CsvDataTypeMismatchException(value, LocalDate.class);
                }
            }
        }

        @Override
        protected String convertToWrite(Object value) {
            if (value == null) return "";
            return ((LocalDate)value).format(ISO);
        }
    }

    public static class LocalTimeConverter extends AbstractBeanField<LocalTime, String> {
        private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
        private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");


        @Override
        protected LocalTime convert(String value) throws CsvDataTypeMismatchException {
            if (value == null || value.isBlank()) return null;
            try {
                return LocalTime.parse(value, HH_MM);
            } catch (DateTimeParseException e) {
                try {
                    return LocalTime.parse(value, HH_MM_SS).truncatedTo(ChronoUnit.MINUTES);
                } catch (DateTimeParseException e2) {
                    throw new CsvDataTypeMismatchException(value, LocalTime.class);
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
