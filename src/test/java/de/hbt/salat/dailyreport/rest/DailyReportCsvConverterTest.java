package de.hbt.salat.dailyreport.rest;

import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;
import static de.hbt.salat.order.domain.SurchargeKind.NIGHT;

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
import org.springframework.http.converter.HttpMessageNotReadableException;

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
                .date("2024-10-29").employeeorderId(183209L)
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
        assertThat(result.getFirst().isTraining()).isFalse();
    }

    /* A value that is neither true nor false is the caller's error: 400, not 500 (#1140). */
    @Test
    void shouldRejectAnUnreadableTrainingFlag() throws IOException {
        // given
        var rawData = """
                    "date","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","hours","minutes","comment","ticketReference","training"
                    "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","1","0","test1","","ja"
                """;
        when(inputMessage.getBody()).thenReturn(toInputStream(rawData, "UTF-8"));

        // when / then
        assertThatThrownBy(() -> dailyReportCsvConverter.read(null, inputMessage))
                .isInstanceOf(HttpMessageNotReadableException.class);
    }

    /* Reference and training flag are new columns at the end (#1140): positions 0-8 stay where existing clients
       expect them, and a file without it still reads as before. */
    @Test
    void shouldReadTicketReferenceFromTheLastColumn() throws IOException {
        // given
        var rawData = """
                    "date","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","hours","minutes","comment","ticketReference","training"
                    "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","1","0","test1","ERP-1","true"
                    "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","2","0","test2","",""
                """;
        when(inputMessage.getBody()).thenReturn(toInputStream(rawData, "UTF-8"));

        // when
        var result = dailyReportCsvConverter.read(null, inputMessage);

        // then
        assertThat(result).extracting(DailyReportData::getComment, DailyReportData::givenTicketReferences, DailyReportData::isTraining)
                .containsExactly(tuple("test1", List.of("ERP-1"), true), tuple("test2", List.of(), false));
    }

    /* The employee sign is a new column at the end (#1142); with it and the suborder sign a line needs no
       order id, and position 1 may stay empty. */
    @Test
    void shouldReadABookingNamedBySignsWithoutAnId() throws IOException {
        // given
        var rawData = """
                    "date","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","hours","minutes","comment","ticketReference","training","employeeSign"
                    "2024-10-29","","","","111/01","","1","0","test1","","","abc"
                """;
        when(inputMessage.getBody()).thenReturn(toInputStream(rawData, "UTF-8"));

        // when
        var result = dailyReportCsvConverter.read(null, inputMessage);

        // then
        assertThat(result).extracting(DailyReportData::getEmployeeorderId, DailyReportData::getSuborderSign, DailyReportData::getEmployeeSign)
                .containsExactly(tuple(null, "111/01", "abc"));
    }

    @Test
    void shouldWriteToCsv() throws IOException {
        // given
        var rawData = """
                "date","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","hours","minutes","comment","ticketReference","training","employeeSign","surchargeKind"
                "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","1","0","test1","","false","",""
                "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","1","0","test2","ERP-1","true","abc","NIGHT"
                """;
        var dailyReport1 = DailyReportData.builder()
                .date("2024-10-29").employeeorderId(183209L)
                .orderSign("111").orderLabel("Rumsitzen")
                .suborderSign("111.01").suborderLabel("Stuhlpolsterung")
                .hours(1).minutes(0).comment("test1")
                .build();

        var outputStream = new ByteArrayOutputStream();
        when(outputMessage.getBody()).thenReturn(outputStream);

        // when
        dailyReportCsvConverter.write(List.of(dailyReport1,
                dailyReport1.toBuilder().comment("test2").training(true).employeeSign("abc").surchargeKind("NIGHT").build()
                        .withTicketReferences(List.of("ERP-1"))), null, outputMessage);

        // then
        assertThat(IOUtils.toString(outputStream.toByteArray(), "UTF-8")).isEqualTo(rawData);
    }

    /* All references of a booking, separated by ";" like the CSV of the working days (#1472): one in the
       cell is what a single reference has always been. */
    @Test
    void shouldReadSeveralTicketReferencesFromOneCell() throws IOException {
        // given
        var rawData = """
                    "date","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","hours","minutes","comment","ticketReference","training"
                    "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","1","0","test1","ERP-1;ERP-2","false"
                """;
        when(inputMessage.getBody()).thenReturn(toInputStream(rawData, "UTF-8"));

        // when
        var result = dailyReportCsvConverter.read(null, inputMessage);

        // then
        assertThat(result).singleElement().satisfies(booking ->
                assertThat(booking.givenTicketReferences()).containsExactly("ERP-1", "ERP-2"));
    }

    @Test
    void shouldWriteAllTicketReferencesOfABooking() throws IOException {
        // given
        var booking = DailyReportData.builder()
                .date("2024-10-29").employeeorderId(183209L)
                .orderSign("111").orderLabel("Rumsitzen")
                .suborderSign("111.01").suborderLabel("Stuhlpolsterung")
                .hours(1).minutes(0).comment("test1")
                .build()
                .withTicketReferences(List.of("ERP-1", "ERP-2"));
        var outputStream = new ByteArrayOutputStream();
        when(outputMessage.getBody()).thenReturn(outputStream);

        // when
        dailyReportCsvConverter.write(List.of(booking), null, outputMessage);

        // then
        assertThat(IOUtils.toString(outputStream.toByteArray(), "UTF-8"))
                .contains("\"test1\",\"ERP-1;ERP-2\",\"false\"");
    }

    /* The time surcharge flag is a new column at the end (#1469): a file without it says nothing about the flag,
       an empty cell says "none". */
    @Test
    void shouldReadTheSurchargeFlagFromTheLastColumn() throws IOException {
        // given
        var rawData = """
                    "date","employeeorderId","orderSign","orderLabel","suborderSign","suborderLabel","hours","minutes","comment","ticketReference","training","employeeSign","surchargeKind"
                    "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","4","0","test1","","false","","NIGHT"
                    "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","2","0","test2","","false","",""
                    "2024-10-29","183209","111","Rumsitzen","111.01","Stuhlpolsterung","1","0","test3","","false",""
                """;
        when(inputMessage.getBody()).thenReturn(toInputStream(rawData, "UTF-8"));

        // when
        var result = dailyReportCsvConverter.read(null, inputMessage);

        // then
        assertThat(result).extracting(DailyReportData::getComment, DailyReportData::givesSurchargeKind,
                DailyReportData::givenSurchargeKind)
                .containsExactly(tuple("test1", true, NIGHT), tuple("test2", true, null), tuple("test3", false, null));
    }
}