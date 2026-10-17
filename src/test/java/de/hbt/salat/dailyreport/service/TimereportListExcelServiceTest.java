package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.order.domain.SurchargeKind;

/**
 * The time surcharge in the spreadsheet of the booking list (#1469): the credit in hours like the duration, empty
 * without one - what counts is how much a booking adds to the time account, not its kind.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportListExcelServiceTest {

  private static final int DURATION_COLUMN = 8;
  private static final int SURCHARGE_COLUMN = 12;

  private final TimereportListExcelService service = new TimereportListExcelService(messages());

  @Test
  void the_last_column_carries_the_credit_in_hours() throws IOException {
    var flagged = booking(1L).surchargeKind(SurchargeKind.SUNDAY).surchargePercent(50)
        .surchargeCredit(Duration.ofMinutes(90)).build();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(List.of(flagged, booking(2L).build()))))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(0).getCell(SURCHARGE_COLUMN).getStringCellValue()).isEqualTo("Zeitzuschlag");
      assertThat(sheet.getRow(1).getCell(SURCHARGE_COLUMN).getNumericCellValue()).isEqualTo(90 / (24.0 * 60));
      assertThat(sheet.getRow(1).getCell(SURCHARGE_COLUMN).getCellStyle().getDataFormatString()).isEqualTo("[hh]:mm");
      assertThat(sheet.getRow(2).getCell(SURCHARGE_COLUMN).getStringCellValue()).isEmpty();
    }
  }

  /* A number of days shown [hh]:mm, not a text (#1398): Excel sums it, and beyond a day it keeps counting hours. */
  @Test
  void the_duration_is_a_time_value_that_counts_past_a_day() throws IOException {
    var longBooking = booking(1L).duration(Duration.ofHours(25).plusMinutes(30)).build();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(List.of(longBooking))))) {
      var cell = workbook.getSheetAt(0).getRow(1).getCell(DURATION_COLUMN);
      assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
      assertThat(cell.getNumericCellValue()).isEqualTo((25 * 60 + 30) / (24.0 * 60));
      assertThat(cell.getCellStyle().getDataFormatString()).isEqualTo("[hh]:mm");
      assertThat(new DataFormatter(Locale.GERMANY).formatCellValue(cell)).isEqualTo("25:30");
      assertThat(workbook.getSheetAt(0).getRow(1).getCell(DURATION_COLUMN + 1).getNumericCellValue()).isEqualTo(25.5);
    }
  }

  private static TimereportDTO.TimereportDTOBuilder booking(long id) {
    return TimereportDTO.builder().id(id).referenceday(LocalDate.of(2026, 10, 11)).employeeSign("pa")
        .employeeName("Person A").customerShortname("K").customerorderSign("ABC").customerorderDescription("Auftrag")
        .completeOrderSign("ABC/01").suborderDescription("Unterauftrag").taskdescription("Einspielen")
        .duration(Duration.ofHours(3)).status("open");
  }

  private static MessageSourceAccessor messages() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    return new MessageSourceAccessor(messageSource, Locale.GERMANY);
  }
}
