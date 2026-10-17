package de.hbt.salat.dailyreport.service;

import static org.apache.poi.ss.usermodel.CellType.NUMERIC;
import static org.apache.poi.ss.usermodel.CellType.STRING;
import static de.hbt.salat.common.GlobalConstants.DEFAULT_EXCEL_DATE_FORMAT;
import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_DAY;
import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_HOUR;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.dailyreport.domain.TimereportDTO;

/**
 * Writes the booking list to a spreadsheet (#1092).
 *
 * <p>Exactly the columns the table shows and every hit the filter has — also the ones beyond the display limit, which
 * only cuts what is on screen. No summary sheet: a spreadsheet sums for itself, and a second sheet would freeze one
 * grouping out of the many somebody might want.
 *
 * <p>The duration is written twice on purpose: {@code [hh]:mm} reads like the table, the decimal hours are what anybody
 * calculates with. Both are numbers (#1398): the duration a fraction of a day with the cell format {@code [hh]:mm}, as
 * in the invoice export — no locale turns it into a date, a duration of 24 hours or more stays one, and the sheet can
 * sum it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized(requireUnrestricted = true)
public class TimereportListExcelService {

  /** Hours beyond a day, not a time of day: {@code [hh]} keeps counting past 24. */
  static final String DURATION_FORMAT = "[hh]:mm";

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

    var durationStyle = workbook.createCellStyle();
    durationStyle.setDataFormat(workbook.createDataFormat().getFormat(DURATION_FORMAT));

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
        messages.getMessage("main.timereportlist.column.status"),
        messages.getMessage("main.surcharge.booking.label"));

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
      text(row, 7, String.join(", ", timereport.getTicketReferences()));
      duration(row, 8, timereport.getDuration(), durationStyle);
      var decimal = row.createCell(9, NUMERIC);
      decimal.setCellValue(timereport.getDuration().toMinutes() / (double) MINUTES_PER_HOUR);
      text(row, 10, messages.getMessage(timereport.isBillable()
          ? "main.timereportlist.billable.yes" : "main.timereportlist.billable.no"));
      text(row, 11, messages.getMessage("main.timereportlist.status." + timereport.getStatus(),
          timereport.getStatus()));
      // the time surcharge credit (#1469) in hours like the duration, empty without one: what counts is how much
      // it adds to the time account, not its kind
      if (!timereport.getSurchargeCredit().isZero()) {
        duration(row, 12, timereport.getSurchargeCredit(), durationStyle);
      } else {
        text(row, 12, "");
      }
    }

    for (int column = 0; column < headers.size(); column++) {
      sheet.autoSizeColumn(column);
    }
  }

  private static void text(Row row, int column, String value) {
    row.createCell(column, STRING).setCellValue(value == null ? "" : value);
  }

  /** A duration as Excel counts it, in days, shown {@link #DURATION_FORMAT}. */
  private static void duration(Row row, int column, Duration duration, CellStyle style) {
    var cell = row.createCell(column, NUMERIC);
    cell.setCellValue((double) duration.toMinutes() / MINUTES_PER_DAY);
    cell.setCellStyle(style);
  }
}
