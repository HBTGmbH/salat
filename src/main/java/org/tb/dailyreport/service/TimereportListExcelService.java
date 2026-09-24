package org.tb.dailyreport.service;

import static org.apache.poi.ss.usermodel.CellType.NUMERIC;
import static org.apache.poi.ss.usermodel.CellType.STRING;
import static org.tb.common.GlobalConstants.DEFAULT_EXCEL_DATE_FORMAT;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.dailyreport.domain.TimereportDTO;

/**
 * Writes the booking list to a spreadsheet (#1092).
 *
 * <p>Exactly the columns the table shows and every hit the filter has — also the ones beyond the display limit, which
 * only cuts what is on screen. No summary sheet: a spreadsheet sums for itself, and a second sheet would freeze one
 * grouping out of the many somebody might want.
 *
 * <p>The duration is written twice on purpose: {@code h:mm} reads like the table, the decimal hours are what anybody
 * calculates with. Both as text and number respectively, so no locale turns 7:30 into a date.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized(requireUnrestricted = true)
public class TimereportListExcelService {

  private final MessageSourceAccessor messages;

  public byte[] export(List<TimereportDTO> timereports) throws IOException {
    try (var out = new ByteArrayOutputStream(); Workbook workbook = new XSSFWorkbook()) {
      write(workbook, timereports);
      workbook.write(out);
      return out.toByteArray();
    }
  }

  private void write(Workbook workbook, List<TimereportDTO> timereports) {
    var sheet = workbook.createSheet(messages.getMessage("main.timereportlist.title"));

    var headerStyle = workbook.createCellStyle();
    var headerFont = workbook.createFont();
    headerFont.setBold(true);
    headerStyle.setFont(headerFont);

    var dateStyle = workbook.createCellStyle();
    dateStyle.setDataFormat(workbook.createDataFormat().getFormat(DEFAULT_EXCEL_DATE_FORMAT));

    var headers = List.of(
        messages.getMessage("main.timereportlist.column.date"),
        messages.getMessage("main.timereportlist.column.sign"),
        messages.getMessage("main.timereportlist.column.employee"),
        messages.getMessage("main.timereportlist.column.customer"),
        messages.getMessage("main.timereportlist.column.order"),
        messages.getMessage("main.timereportlist.column.suborder"),
        messages.getMessage("main.timereportlist.column.task"),
        messages.getMessage("main.timereportlist.column.ticket"),
        messages.getMessage("main.timereportlist.column.duration"),
        messages.getMessage("main.timereportlist.column.duration.decimal"),
        messages.getMessage("main.timereportlist.column.billable"),
        messages.getMessage("main.timereportlist.column.status"));

    var headerRow = sheet.createRow(0);
    for (int column = 0; column < headers.size(); column++) {
      var cell = headerRow.createCell(column, STRING);
      cell.setCellValue(headers.get(column));
      cell.setCellStyle(headerStyle);
    }

    int rowIndex = 1;
    for (var timereport : timereports) {
      var row = sheet.createRow(rowIndex++);
      var dateCell = row.createCell(0);
      dateCell.setCellValue(java.sql.Date.valueOf(timereport.getReferenceday()));
      dateCell.setCellStyle(dateStyle);
      text(row, 1, timereport.getEmployeeSign());
      text(row, 2, timereport.getEmployeeName());
      text(row, 3, timereport.getCustomerShortname());
      text(row, 4, timereport.getCustomerorderSign() + " - " + timereport.getCustomerorderDescription());
      text(row, 5, timereport.getCompleteOrderSign() + " - " + timereport.getSuborderDescription());
      text(row, 6, timereport.getTaskdescription());
      text(row, 7, timereport.getTicketReference());
      text(row, 8, formatDuration(timereport));
      var decimal = row.createCell(9, NUMERIC);
      decimal.setCellValue(timereport.getDuration().toMinutes() / (double) MINUTES_PER_HOUR);
      text(row, 10, messages.getMessage(timereport.isBillable()
          ? "main.timereportlist.billable.yes" : "main.timereportlist.billable.no"));
      text(row, 11, messages.getMessage("main.timereportlist.status." + timereport.getStatus(),
          timereport.getStatus()));
    }

    for (int column = 0; column < headers.size(); column++) {
      sheet.autoSizeColumn(column);
    }
  }

  private static void text(Row row, int column, String value) {
    row.createCell(column, STRING).setCellValue(value == null ? "" : value);
  }

  private static String formatDuration(TimereportDTO timereport) {
    var minutes = timereport.getDuration().toMinutes();
    return "%d:%02d".formatted(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR);
  }
}
