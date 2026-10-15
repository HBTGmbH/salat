package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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

  private static final int SURCHARGE_COLUMN = 12;

  private final TimereportListExcelService service = new TimereportListExcelService(messages());

  @Test
  void the_last_column_carries_the_credit_in_hours() throws IOException {
    var flagged = booking(1L).surchargeKind(SurchargeKind.SUNDAY).surchargePercent(50)
        .surchargeCredit(Duration.ofMinutes(90)).build();

    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.export(List.of(flagged, booking(2L).build()))))) {
      var sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(0).getCell(SURCHARGE_COLUMN).getStringCellValue()).isEqualTo("Zeitzuschlag");
      assertThat(sheet.getRow(1).getCell(SURCHARGE_COLUMN).getStringCellValue()).isEqualTo("1:30");
      assertThat(sheet.getRow(2).getCell(SURCHARGE_COLUMN).getStringCellValue()).isEmpty();
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
