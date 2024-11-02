package org.tb.dailyreport.rest;

import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.*;
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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;

@Component
public class DailyReportCsvConverter implements HttpMessageConverter<List<DailyReportData>> {
    static final char COLUMN_SEPARATOR = ',';
    static final String TEXT_CSV_DAILY_REPORT_VALUE = "text/csv+dailyreport";
    static final MediaType TEXT_CSV_DAILY_REPORT = new MediaType(
            TEXT_CSV_DAILY_REPORT_VALUE.split("/")[0],
            TEXT_CSV_DAILY_REPORT_VALUE.split("/")[1]
    );

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
        try (InputStreamReader reader = new InputStreamReader(inputMessage.getBody())) {
            return new CsvToBeanBuilder<DailyReportData>(reader)
                    .withSeparator(COLUMN_SEPARATOR)
                    .withMappingStrategy(new PositionAwareColumnMappingStrategy<>(DailyReportData.class, () -> DailyReportData.builder().build()))
                    .withSkipLines(1)
                    .build()
                    .parse();
        }
    }

    @Override
    public void write(@Nullable List<DailyReportData> dailyReportData, @Nullable MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        try (OutputStreamWriter writer = new OutputStreamWriter(outputMessage.getBody())) {
            new StatefulBeanToCsvBuilder<DailyReportData>(writer)
                    .withSeparator(COLUMN_SEPARATOR)
                    .withMappingStrategy(new PositionAwareColumnMappingStrategy<>(DailyReportData.class, () -> DailyReportData.builder().build()))
                    .build()
                    .write(dailyReportData);
        }  catch (CsvException  e) {
            throw new HttpMessageNotWritableException("unable to write CSV", e);
        }
    }
}
