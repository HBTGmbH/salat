package org.tb.reporting.rest;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.opencsv.CSVWriterBuilder;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Schreibt ein {@link ReportData} als CSV: Kopfzeile, Trennzeichen {@code ,}, UTF-8 ohne BOM — wie
 * der {@code DailyReportCsvConverter}.
 *
 * <p>Zwei Unterschiede zu jenem sind beabsichtigt:
 * <ul>
 *   <li>Die Spalten eines Reports stehen erst zur Laufzeit fest. Eine bean-basierte
 *       {@code MappingStrategy} gibt es hier deshalb nicht; geschrieben wird über die Spaltenliste
 *       des Ergebnisses.</li>
 *   <li>Der Medientyp ist {@code text/csv} und nicht {@code text/csv+report}. Die Suffixe im Modul
 *       {@code dailyreport} sind nötig, weil die Konverter dort in {@code canWrite} allein auf den
 *       Medientyp schauen und sich sonst gegenseitig bedienen würden. Dieser Konverter prüft die
 *       Klasse mit und kann deshalb den normalen Typ führen.</li>
 * </ul>
 */
@Component
public class ReportDataCsvConverter implements HttpMessageConverter<ReportData> {

  static final char COLUMN_SEPARATOR = ',';
  static final String TEXT_CSV_VALUE = "text/csv";
  static final MediaType TEXT_CSV = new MediaType("text", "csv");

  /** Ein Reportergebnis wird nie gelesen — es entsteht aus einer Datenbankabfrage. */
  @Override
  public boolean canRead(@Nullable Class<?> clazz, @Nullable MediaType mediaType) {
    return false;
  }

  @Override
  public boolean canWrite(@Nullable Class<?> clazz, @Nullable MediaType mediaType) {
    return clazz != null && ReportData.class.isAssignableFrom(clazz) && TEXT_CSV.isCompatibleWith(mediaType);
  }

  @Override
  @NonNull
  public List<MediaType> getSupportedMediaTypes() {
    return List.of(TEXT_CSV);
  }

  @Override
  @NonNull
  public ReportData read(@Nullable Class<? extends ReportData> clazz, @Nullable HttpInputMessage inputMessage)
      throws HttpMessageNotReadableException {
    throw new HttpMessageNotReadableException("a report result cannot be read from CSV", inputMessage);
  }

  @Override
  public void write(@Nullable ReportData reportData, @Nullable MediaType contentType, HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    if (reportData == null) {
      throw new HttpMessageNotWritableException("no report result to write");
    }
    // Vor getBody(): eine Servlet-Antwort schreibt ihre Kopfzeilen, sobald der Strom geöffnet ist.
    // Wer HttpMessageConverter direkt umsetzt, bekommt diesen Schritt nicht geschenkt — er steckt in
    // AbstractHttpMessageConverter. Ohne ihn trägt die Antwort keinen Content-Type.
    outputMessage.getHeaders().setContentType(contentType != null && !contentType.isWildcardSubtype()
        ? contentType
        : TEXT_CSV);
    try (var writer = new OutputStreamWriter(outputMessage.getBody(), UTF_8);
         var csvWriter = new CSVWriterBuilder(writer).withSeparator(COLUMN_SEPARATOR).build()) {
      csvWriter.writeNext(reportData.columns().toArray(String[]::new));
      for (var row : reportData.rows()) {
        csvWriter.writeNext(ReportData.textRow(row).toArray(String[]::new));
      }
    }
  }

}
