package org.tb.reporting.rest;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class ReportDataCsvConverterTest {

  @Mock
  HttpOutputMessage outputMessage;

  @InjectMocks
  ReportDataCsvConverter converter;

  @Test
  void shouldWriteHeaderAndRows() throws IOException {
    var reportData = new ReportData("Stunden", List.of("auftrag", "stunden"),
        List.of(List.of("111", 7), List.of("222", 3)));

    assertThat(write(reportData)).isEqualTo("""
        "auftrag","stunden"
        "111","7"
        "222","3"
        """);
  }

  @Test
  void anEmptyResultStillWritesItsHeader() throws IOException {
    var reportData = new ReportData("Stunden", List.of("auftrag"), List.of());

    assertThat(write(reportData)).isEqualTo("""
        "auftrag"
        """);
  }

  @Test
  void aMissingValueBecomesAnEmptyCell() throws IOException {
    var reportData = new ReportData("Stunden", List.of("auftrag", "stunden"),
        List.of(asList("111", null)));

    assertThat(write(reportData)).isEqualTo("""
        "auftrag","stunden"
        "111",""
        """);
  }

  @Test
  void aQuoteInAValueIsEscaped() throws IOException {
    var reportData = new ReportData("Stunden", List.of("text"), List.of(List.of("sagt \"hallo\"")));

    // "text"
    // "sagt ""hallo"""
    assertThat(write(reportData)).isEqualTo("\"text\"\n\"sagt \"\"hallo\"\"\"\n");
  }

  @Test
  void writesOnlyReportDataAndOnlyAsCsv() {
    assertThat(converter.canWrite(ReportData.class, MediaType.parseMediaType("text/csv"))).isTrue();
    // a text/csv body of another type belongs to another converter - that is what the
    // suffix media types in the dailyreport module are for
    assertThat(converter.canWrite(String.class, MediaType.parseMediaType("text/csv"))).isFalse();
    assertThat(converter.canWrite(byte[].class, MediaType.parseMediaType("text/csv"))).isFalse();
    assertThat(converter.canWrite(ReportData.class, MediaType.APPLICATION_JSON)).isFalse();
    assertThat(converter.canWrite(null, MediaType.parseMediaType("text/csv"))).isFalse();
  }

  @Test
  void aReportResultIsNeverRead() {
    assertThat(converter.canRead(ReportData.class, MediaType.parseMediaType("text/csv"))).isFalse();
  }

  @Test
  void theAnswerSaysItIsCsv() throws IOException {
    var headers = new HttpHeaders();
    when(outputMessage.getHeaders()).thenReturn(headers);
    when(outputMessage.getBody()).thenReturn(new ByteArrayOutputStream());

    converter.write(new ReportData("Stunden", List.of("a"), List.of()), null, outputMessage);

    assertThat(headers.getContentType()).isEqualTo(ReportDataCsvConverter.TEXT_CSV);
  }

  private String write(ReportData reportData) throws IOException {
    var body = new ByteArrayOutputStream();
    when(outputMessage.getHeaders()).thenReturn(new HttpHeaders());
    when(outputMessage.getBody()).thenReturn(body);

    converter.write(reportData, null, outputMessage);

    return body.toString("UTF-8");
  }

}
