package org.tb.dailyreport.rest;

import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;

@ExtendWith(MockitoExtension.class)
class DailyReportCsvConverterTest {
    @Mock
    HttpInputMessage inputMessage;

    @Mock
    HttpOutputMessage outputMessage;

    @InjectMocks
    DailyReportCsvConverter dailyReportCsvConverter;

    @Test
    void shouldReadFromCsv() throws IOException {
        // given
        var rawData = """
                    "date","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","hours","minutes","comment"
                    "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","1","0","test1"
                """;
        var dailyReport1 = DailyReportData.builder()
                .date("2024-10-29").employeeorderId(183209)
                .orderSign("111").orderLabel("Rumsitzen")
                .suborderSign("111.01").suborderLabel("Stuhlpolsterung")
                .hours(1).minutes(0).comment("test1")
                .build();

        var inputStream = toInputStream(rawData, "UTF-8");
        when(inputMessage.getBody()).thenReturn(inputStream);

        // when
        var result = dailyReportCsvConverter.read(null, inputMessage);

        // then
        assertThat(result).hasSize(1).contains(dailyReport1);
        assertThat(result.getFirst().getTicketReference()).isNull();
    }

    /* The reference is a new column at the end (#1140): positions 0-8 stay where existing clients
       expect them, and a file without it still reads as before. */
    @Test
    void shouldReadTicketReferenceFromTheLastColumn() throws IOException {
        // given
        var rawData = """
                    "date","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","hours","minutes","comment","ticketReference"
                    "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","1","0","test1","ERP-1"
                    "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","2","0","test2",""
                """;
        when(inputMessage.getBody()).thenReturn(toInputStream(rawData, "UTF-8"));

        // when
        var result = dailyReportCsvConverter.read(null, inputMessage);

        // then
        assertThat(result).extracting(DailyReportData::getComment, DailyReportData::getTicketReference)
                .containsExactly(tuple("test1", "ERP-1"), tuple("test2", ""));
    }

    @Test
    void shouldWriteToCsv() throws IOException {
        // given
        var rawData = """
                "date","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","hours","minutes","comment","ticketReference"
                "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","1","0","test1",""
                "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","1","0","test2","ERP-1"
                """;
        var dailyReport1 = DailyReportData.builder()
                .date("2024-10-29").employeeorderId(183209)
                .orderSign("111").orderLabel("Rumsitzen")
                .suborderSign("111.01").suborderLabel("Stuhlpolsterung")
                .hours(1).minutes(0).comment("test1")
                .build();

        var outputStream = new ByteArrayOutputStream();
        when(outputMessage.getBody()).thenReturn(outputStream);

        // when
        dailyReportCsvConverter.write(List.of(dailyReport1,
                dailyReport1.toBuilder().comment("test2").ticketReference("ERP-1").build()), null, outputMessage);

        // then
        assertThat(IOUtils.toString(outputStream.toByteArray(), "UTF-8")).isEqualTo(rawData);
    }
}