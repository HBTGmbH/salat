package de.hbt.salat.invoice.service;


import static org.apache.poi.ss.usermodel.CellType.NUMERIC;
import static org.apache.poi.ss.usermodel.CellType.STRING;
import static de.hbt.salat.common.GlobalConstants.DEFAULT_TIMEZONE_ID;
import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_DAY;
import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_HOUR;

import jakarta.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.hssf.usermodel.HSSFDataFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.invoice.domain.InvoiceData;
import de.hbt.salat.invoice.domain.InvoiceGroup;
import de.hbt.salat.invoice.domain.InvoiceLine;
import de.hbt.salat.invoice.domain.InvoiceSurcharge;
import de.hbt.salat.invoice.domain.InvoiceTicketDisplay;
import de.hbt.salat.invoice.service.InvoiceService.InvoiceOptions;

/**
 * This Class saves the created excel workbook
 * on the client. The client can choose the location for file saving
 *
 * @author la
 */
@Service("invoiceExcelExportService")
@Transactional
@RequiredArgsConstructor
@Authorized(requiresBackoffice = true)
public class ExcelExportService {

    private static final String INVOICE_EXCEL_SHEET_NAME = "Tätigkeitsnachweis";
    private static final Map<String, Short> cellStyleIndexes = new HashMap<String, Short>();

    private static InstanceFactory getXSSFFactory() {
        return new InstanceFactory() {
            private final DataFormatter dataFormatter = new HSSFDataFormatter();

            @Override
            public Workbook createWorkbook() {
                return new XSSFWorkbook();
            }

            @Override
            public RichTextString createRichTextString(String str) {
                return new XSSFRichTextString(str);
            }

            @Override
            public DataFormatter getDataFormatter() {
                return dataFormatter;
            }
        };
    }

    private final MessageSourceAccessor messages;

    public byte[] exportToExcel(InvoiceData invoiceData, InvoiceOptions displayOptions, InvoiceColumnHeaders headers) throws IOException {
        var factory = getXSSFFactory();
        var layout = new Layout(displayOptions,
            displayOptions.getTicketDisplay() == InvoiceTicketDisplay.COLUMN && invoiceData.isTicketReferencesPresent(),
            displayOptions.isShowTimereports() && (!invoiceData.getGrouping().isByTicket() || displayOptions.isShowTicketLines()),
            messages.getMessage("main.invoice.ticket.none", "ohne Ticket"),
            invoiceData.getSurcharges().stream().collect(Collectors.toMap(InvoiceSurcharge::getKey, this::surchargeLabel)),
            messages.getMessage("main.invoice.surcharges.text", "Zeitzuschläge"),
            messages.getMessage("main.invoice.overall.withsurcharges.text", "Gesamtsumme inkl. Zuschläge"));
        try(Workbook workbook = createInvoiceExcel(invoiceData, layout, headers, factory);
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * What the sheet shows besides the columns the options switch.
     *
     * @param ticketColumn whether the column „Ticket“ is there (#1390)
     * @param lines        whether the bookings are listed under their groups, or only the subtotals
     */
    private record Layout(InvoiceOptions options, boolean ticketColumn, boolean lines, String noTicketLabel,
                          Map<String, String> surchargeLabels, String surchargesLabel, String totalWithSurchargesLabel) {}

    /** „Sonntag 100 % auf 4:00“ — the kind and rate as the booking form names them, and what they apply to. */
    private String surchargeLabel(InvoiceSurcharge surcharge) {
        var flag = messages.getMessage("main.surcharge.flag", new Object[] {
            messages.getMessage(surcharge.kind().getLabel()),
            messages.getMessage("main.surcharge.percent", new Object[] {surcharge.percent()})});
        return messages.getMessage("main.invoice.surcharge.line",
            new Object[] {flag, DurationUtils.format(surcharge.basis())});
    }

    private static Workbook createInvoiceExcel(InvoiceData invoiceData, Layout layout, InvoiceColumnHeaders headers, InstanceFactory factory) {
        Workbook workbook = factory.createWorkbook();
        createCellStyles(workbook);
        workbook.createSheet(INVOICE_EXCEL_SHEET_NAME);
        addTitleRow(workbook, layout, headers, factory);
        int rowIndex = 1;
        for (var group : invoiceData.getGroups()) {
            rowIndex = addGroup(workbook, rowIndex, group, layout, factory);
        }
        addSumRow(workbook, rowIndex, invoiceData, layout);
        addSurchargeRows(workbook, rowIndex + 1, invoiceData, layout, factory);
        setColumnWidths(workbook.getSheet(INVOICE_EXCEL_SHEET_NAME), factory);
        return workbook;
    }

    /**
     * The group's row with its subtotal, its own bookings, then the groups below it — those also when
     * the group itself is deselected (#217).
     */
    private static int addGroup(Workbook workbook, int rowIndex, InvoiceGroup group, Layout layout, InstanceFactory factory) {
        if (group.isVisible()) {
            rowIndex = addGroupDataRow(workbook, rowIndex, group, layout, factory);
            if (layout.lines()) {
                for (var line : group.getLines()) {
                    if (line.isVisible()) {
                        rowIndex = addLineDataRow(workbook, rowIndex, line, layout, factory);
                    }
                }
            }
        }
        for (var child : group.getChildren()) {
            rowIndex = addGroup(workbook, rowIndex, child, layout, factory);
        }
        return rowIndex;
    }

    private static void createCellStyles(Workbook workbook) {
        // title-Style
        CellStyle titleCellStyle = workbook.createCellStyle();
        Font boldItalicFont = workbook.createFont();
        boldItalicFont.setBold(true);
        boldItalicFont.setItalic(true);
        titleCellStyle.setFont(boldItalicFont);
        titleCellStyle.setVerticalAlignment(VerticalAlignment.TOP);
        titleCellStyle.setAlignment(HorizontalAlignment.CENTER);
        cellStyleIndexes.put("title", titleCellStyle.getIndex());
        // italic-Style
        CellStyle italicCellStyle = workbook.createCellStyle();
        Font italicFont = workbook.createFont();
        italicFont.setBold(false);
        italicFont.setItalic(true);
        italicCellStyle.setFont(italicFont);
        italicCellStyle.setVerticalAlignment(VerticalAlignment.TOP);
        italicCellStyle.setAlignment(HorizontalAlignment.RIGHT);
        cellStyleIndexes.put("italic", italicCellStyle.getIndex());
        // textwrap-Style
        CellStyle textwrapCellStyle = workbook.createCellStyle();
        Font normalFont = workbook.createFont();
        normalFont.setBold(false);
        textwrapCellStyle.setFont(normalFont);
        textwrapCellStyle.setWrapText(true);
        textwrapCellStyle.setVerticalAlignment(VerticalAlignment.TOP);
        textwrapCellStyle.setAlignment(HorizontalAlignment.LEFT);
        cellStyleIndexes.put("textwrap", textwrapCellStyle.getIndex());
        // hourMinute-Style
        CellStyle hourMinuteCellStyle = workbook.createCellStyle();
        hourMinuteCellStyle.setFont(normalFont);
        hourMinuteCellStyle.setVerticalAlignment(VerticalAlignment.TOP);
        hourMinuteCellStyle.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat hhmmFormat = workbook.createDataFormat();
        hourMinuteCellStyle.setDataFormat(hhmmFormat.getFormat("[hh]:mm"));
        cellStyleIndexes.put("hourMinute", hourMinuteCellStyle.getIndex());
        // hourMinuteItalic-Style
        CellStyle hourMinuteItalicCellStyle = workbook.createCellStyle();
        hourMinuteItalicCellStyle.cloneStyleFrom(hourMinuteCellStyle);
        hourMinuteItalicCellStyle.setFont(italicFont);
        cellStyleIndexes.put("hourMinuteItalic", hourMinuteItalicCellStyle.getIndex());
        // hourMinuteBold-Style
        CellStyle hourMinuteBoldCellStyle = workbook.createCellStyle();
        hourMinuteBoldCellStyle.cloneStyleFrom(hourMinuteCellStyle);
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        hourMinuteBoldCellStyle.setFont(boldFont);
        cellStyleIndexes.put("hourMinuteBold", hourMinuteBoldCellStyle.getIndex());
        // date-Style
        CellStyle dateCellStyle = workbook.createCellStyle();
        dateCellStyle.setFont(normalFont);
        dateCellStyle.setVerticalAlignment(VerticalAlignment.TOP);
        dateCellStyle.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat dateFormat = workbook.createDataFormat();
        dateCellStyle.setDataFormat(dateFormat.getFormat(GlobalConstants.DEFAULT_EXCEL_DATE_FORMAT));
        cellStyleIndexes.put("date", dateCellStyle.getIndex());
    }

    private static RichTextString createRTS(String str, InstanceFactory factory) {
        if (str == null) return factory.createRichTextString("");
        return factory.createRichTextString(str);
    }

    private static int addGroupDataRow(Workbook workbook, int rowIndex, InvoiceGroup group, Layout layout, InstanceFactory factory) {
        var options = layout.options();
        Row row = workbook.getSheet(INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
        rowIndex++;
        int colIndex = 0;
        Cell cell = row.createCell(colIndex, STRING);
        var heading = group.isNoTicketGroup() ? layout.noTicketLabel() : group.getHeading();
        cell.setCellValue(createRTS("  ".repeat(group.getDepth()) + heading, factory));
        colIndex++;
        if (options.isShowTimereports()) {
            colIndex++;
            if (options.isShowEmployee()) {
                colIndex++;
            }
            if (options.isShowTaskdescriptions()) {
                cell = row.createCell(colIndex, STRING);
                cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("textwrap")));
                colIndex++;
            }
            if (layout.ticketColumn()) {
                colIndex++;
            }
        }
        if (options.isShowBudget()) {
            if (group.isSuborderGroup()) {
                cell = row.createCell(colIndex, NUMERIC);
                if (group.getBudget() != null) {
                    cell.setCellValue((double) group.getBudget().toMinutes() / MINUTES_PER_DAY);
                } else {
                    cell.setCellValue(0L);
                }
                cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
            }
            colIndex++;
        }
        cell = row.createCell(colIndex, NUMERIC);
        cell.setCellValue((double) group.getTotalDurationVisible().toMinutes() / MINUTES_PER_DAY);
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
        colIndex++;
        cell = row.createCell(colIndex, NUMERIC);
        cell.setCellValue((double) group.getTotalDurationVisible().toMinutes() / MINUTES_PER_HOUR);
        return rowIndex;
    }

    private static int addLineDataRow(Workbook workbook, int rowIndex, @Nonnull InvoiceLine line, Layout layout, InstanceFactory factory) {
        var invoiceOptions = layout.options();
        var invoiceTimereport = line.getTimereport();
        Row row = workbook.getSheet(INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
        rowIndex++;
        int colIndex = 1;
        Cell cell = row.createCell(colIndex, NUMERIC);
        if (invoiceTimereport.getReferenceDay() != null) {
            Date date = Date.from(
                invoiceTimereport
                    .getReferenceDay()
                    .atStartOfDay(ZoneId.of(DEFAULT_TIMEZONE_ID))
                    .toInstant()
            );
            cell.setCellValue(date);
        } else {
            cell.setCellValue((Date )null); // should not happen
        }
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("date")));
        colIndex++;
        if (invoiceOptions.isShowEmployee()) {
            cell = row.createCell(colIndex, STRING);
            cell.setCellValue(createRTS(invoiceTimereport.getEmployeeName(), factory));
            colIndex++;
        }
        if (invoiceOptions.isShowTaskdescriptions()) {
            cell = row.createCell(colIndex, STRING);
            var description = invoiceOptions.getTicketDisplay() == InvoiceTicketDisplay.COMMENT
                ? line.getTaskDescriptionWithTickets()
                : invoiceTimereport.getTaskDescription();
            cell.setCellValue(createRTS(description, factory));
            cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("textwrap")));
            colIndex++;
        }
        if (layout.ticketColumn()) {
            cell = row.createCell(colIndex, STRING);
            cell.setCellValue(createRTS(line.getTicketText(), factory));
            colIndex++;
        }
        if (invoiceOptions.isShowBudget()) {
            colIndex++;
        }
        cell = row.createCell(colIndex, NUMERIC);
        double duration = line.getShare().toMinutes();
        cell.setCellValue(duration / MINUTES_PER_DAY);
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
        colIndex++;
        cell = row.createCell(colIndex, NUMERIC);
        cell.setCellValue(duration / MINUTES_PER_HOUR);
        return rowIndex;
    }

    private static void addTitleRow(Workbook workbook, Layout layout, InvoiceColumnHeaders headers, InstanceFactory factory) {
        var options = layout.options();
        int colIndex = 0;
        Row row = workbook.getSheet(INVOICE_EXCEL_SHEET_NAME).createRow(0);
        Cell cell = row.createCell(colIndex, STRING);
        cell.setCellValue(createRTS(headers.getOrderHeader(), factory));
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
        colIndex++;
        if (options.isShowTimereports()) {
            cell = row.createCell(colIndex, STRING);
            cell.setCellValue(createRTS(headers.getDateHeader(), factory));
            cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
            colIndex++;
            if (options.isShowEmployee()) {
                cell = row.createCell(colIndex, STRING);
                cell.setCellValue(createRTS(headers.getEmployeeHeader(), factory));
                cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
                colIndex++;
            }
            if (options.isShowTaskdescriptions()) {
                cell = row.createCell(colIndex, STRING);
                cell.setCellValue(createRTS(headers.getTaskDescriptionHeader(), factory));
                cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
                colIndex++;
            }
            if (layout.ticketColumn()) {
                cell = row.createCell(colIndex, STRING);
                cell.setCellValue(createRTS(headers.getTicketHeader(), factory));
                cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
                colIndex++;
            }
        }

        if (options.isShowBudget()) {
            cell = row.createCell(colIndex, STRING);
            cell.setCellValue(createRTS(headers.getBudgetHeader(), factory));
            cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
            colIndex++;
        }

        cell = row.createCell(colIndex, STRING);
        cell.setCellValue(createRTS(headers.getDurationHeader(), factory));
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
        colIndex++;
        cell = row.createCell(colIndex, STRING);
        cell.setCellValue(createRTS(headers.getHoursHeader(), factory));
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
    }

    private static void addSumRow(Workbook workbook, int rowIndex, InvoiceData invoiceData, Layout layout) {
        Row row = workbook.getSheet(INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
        int colIndex = durationColumn(layout);
        Cell cell = row.createCell(colIndex, NUMERIC);
        cell.setCellValue(((double)invoiceData.getTotalDurationVisible().toMinutes()) / MINUTES_PER_DAY);
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinuteBold")));
        colIndex++;
        cell = row.createCell(colIndex, NUMERIC);
        cell.setCellValue(invoiceData.getTotalHoursVisible().doubleValue());
    }

    /** The column of the duration; the hours stand in the one after it. */
    private static int durationColumn(Layout layout) {
        var displayOptions = layout.options();
        int colIndex = 1;
        if (displayOptions.isShowTimereports()) {
            colIndex++;
            if (displayOptions.isShowEmployee()) {
                colIndex++;
            }
            if (displayOptions.isShowTaskdescriptions()) {
                colIndex++;
            }
            if (layout.ticketColumn()) {
                colIndex++;
            }
        }
        if (displayOptions.isShowBudget()) {
            colIndex++;
        }
        return colIndex;
    }

    /**
     * The time surcharges as positions of their own below the total (#1470), and the total with them —
     * nothing when there are none or when they are part of the hours.
     */
    private static void addSurchargeRows(Workbook workbook, int rowIndex, InvoiceData invoiceData, Layout layout,
                                         InstanceFactory factory) {
        var surcharges = invoiceData.getSurcharges();
        if (surcharges.isEmpty()) {
            return;
        }
        var sheet = workbook.getSheet(INVOICE_EXCEL_SHEET_NAME);
        sheet.createRow(rowIndex++).createCell(0, STRING).setCellValue(createRTS(layout.surchargesLabel(), factory));
        for (var surcharge : surcharges) {
            addLabelledDurationRow(workbook, rowIndex++, layout.surchargeLabels().get(surcharge.getKey()),
                surcharge.credit(), surcharge.getHours().doubleValue(), "hourMinute", layout, factory);
        }
        // rounded like the total above it
        addLabelledDurationRow(workbook, rowIndex, layout.totalWithSurchargesLabel(),
            invoiceData.getTotalDurationVisibleWithSurcharges(), invoiceData.getTotalHoursVisibleWithSurcharges().doubleValue(),
            "hourMinuteBold", layout, factory);
    }

    /** A label in the first column, the duration and the hours where the sum row has them. */
    private static void addLabelledDurationRow(Workbook workbook, int rowIndex, String label, Duration duration,
                                               double hours, String durationStyle, Layout layout, InstanceFactory factory) {
        Row row = workbook.getSheet(INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
        row.createCell(0, STRING).setCellValue(createRTS(label, factory));
        int colIndex = durationColumn(layout);
        Cell cell = row.createCell(colIndex, NUMERIC);
        cell.setCellValue((double) duration.toMinutes() / MINUTES_PER_DAY);
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get(durationStyle)));
        cell = row.createCell(colIndex + 1, NUMERIC);
        cell.setCellValue(hours);
    }

    private static void setColumnWidths(Sheet sheet, InstanceFactory factory) {
        // get the highest width per column and set the column width for sheet
        Map<Integer, Integer> widthMap = new HashMap<>();
        for (Iterator<Row> rowIter = sheet.rowIterator(); rowIter.hasNext(); ) {
            Row row = rowIter.next();
            for (Iterator<Cell> cellIter = row.cellIterator(); cellIter.hasNext(); ) {
                Cell cell = cellIter.next();
                Integer widthFromMap = widthMap.get(cell.getColumnIndex());
                int width;
                switch (cell.getCellType()) {
                    case NUMERIC:
                        width = (factory.getDataFormatter().formatCellValue(cell).length() + 3) * 256;
                        break;
                    case STRING:
                        width = (cell.getRichStringCellValue().length() + 3) * 256;
                        break;
                    case FORMULA:
                        // nothing -> fall through
                    case BLANK:
                        // nothing -> fall through
                    case BOOLEAN:
                        // nothing -> fall through
                    case ERROR:
                        // nothing -> fall through
                    default:
                        width = sheet.getColumnWidth(cell.getColumnIndex());
                }
                if (widthFromMap == null || widthFromMap < width) {
                    widthMap.put(cell.getColumnIndex(), width);
                }
            }
        }
        for (Entry<Integer, Integer> entry : widthMap.entrySet()) {
            sheet.setColumnWidth(entry.getKey(), Math.min(entry.getValue(), 255 * 255));
        }
    }

    public interface InstanceFactory {
        Workbook createWorkbook();

        RichTextString createRichTextString(String str);

        DataFormatter getDataFormatter();
    }

    public interface InvoiceColumnHeaders {
        String getOrderHeader();
        String getDateHeader();
        String getEmployeeHeader();
        String getTaskDescriptionHeader();
        String getTicketHeader();
        String getBudgetHeader();
        String getDurationHeader();
        String getHoursHeader();
    }
}
