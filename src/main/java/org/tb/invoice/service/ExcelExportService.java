package org.tb.invoice.service;


import static org.apache.poi.ss.usermodel.CellType.NUMERIC;
import static org.apache.poi.ss.usermodel.CellType.STRING;
import static org.tb.common.GlobalConstants.DEFAULT_TIMEZONE_ID;
import static org.tb.common.GlobalConstants.MINUTES_PER_DAY;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import jakarta.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.GlobalConstants;
import org.tb.invoice.domain.InvoiceData;
import org.tb.invoice.domain.InvoiceSuborder;
import org.tb.invoice.domain.InvoiceTimereport;
import org.tb.invoice.service.InvoiceService.InvoiceOptions;

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

    public byte[] exportToExcel(InvoiceData invoiceData, InvoiceColumnHeaders headers) throws IOException {
        var factory = getXSSFFactory();
        try(Workbook workbook = createInvoiceExcel(invoiceData, headers, factory);
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @SuppressWarnings("unchecked")
    private static Workbook createInvoiceExcel(InvoiceData invoiceData, InvoiceColumnHeaders headers, InstanceFactory factory) {
        Workbook workbook = factory.createWorkbook();
        createCellStyles(workbook);
        workbook.createSheet(INVOICE_EXCEL_SHEET_NAME);
        addTitleRow(workbook, invoiceData.getInvoiceOptions(), headers, factory);
        int rowIndex = 1;
        var invoiceSuborders = invoiceData.getSuborders();
        for (var invoiceSuborder : invoiceSuborders) {
            if (invoiceSuborder.isVisible()) {
                rowIndex = addSuborderDataRow(workbook, rowIndex, invoiceSuborder, invoiceData.getInvoiceOptions(), factory);
                // ab hier ggf. Timereports ausgeben
                List<InvoiceTimereport> invoiceTimereports = invoiceSuborder.getTimereports();
                if (invoiceData.getInvoiceOptions().isShowTimereports()) {
                    for (var invoiceTimereport : invoiceTimereports) {
                        if(invoiceTimereport.isVisible()) {
                            rowIndex = addTimereportDataRow(workbook, rowIndex, invoiceTimereport, invoiceData.getInvoiceOptions(), factory);
                        }
                    }
                }
            }
        }
        addSumRow(workbook, rowIndex, invoiceData);
        setColumnWidths(workbook.getSheet(INVOICE_EXCEL_SHEET_NAME), factory);
        return workbook;
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

    private static int addSuborderDataRow(Workbook workbook, int rowIndex, InvoiceSuborder invoiceSuborder, InvoiceOptions options, InstanceFactory factory) {
        Row row = workbook.getSheet(INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
        rowIndex++;
        int colIndex = 0;
        Cell cell = row.createCell(colIndex, STRING);
        cell.setCellValue(createRTS(invoiceSuborder.getOrderDescription(), factory));
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
        }
        if (options.isShowBudget()) {
            cell = row.createCell(colIndex, NUMERIC);
            if (invoiceSuborder.getBudget() != null) {
                cell.setCellValue((double) invoiceSuborder.getBudget().toMinutes() / MINUTES_PER_DAY);
            } else {
                cell.setCellValue(0L);
            }
            cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
            colIndex++;
        }
        cell = row.createCell(colIndex, NUMERIC);
        cell.setCellValue((double) invoiceSuborder.getTotalDuration().toMinutes() / MINUTES_PER_DAY);
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
        colIndex++;
        cell = row.createCell(colIndex, NUMERIC);
        cell.setCellValue((double) invoiceSuborder.getTotalDuration().toMinutes() / MINUTES_PER_HOUR);
        return rowIndex;
    }

    private static int addTimereportDataRow(Workbook workbook, int rowIndex, @Nonnull InvoiceTimereport invoiceTimereport, InvoiceOptions invoiceOptions, InstanceFactory factory) {
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
            cell.setCellValue(createRTS(invoiceTimereport.getTaskDescription(), factory));
            cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("textwrap")));
            colIndex++;
        }
        if (invoiceOptions.isShowBudget()) {
            colIndex++;
        }
        cell = row.createCell(colIndex, NUMERIC);
        if (invoiceTimereport.getDuration() != null) {
            double duration = invoiceTimereport.getDuration().toMinutes();
            cell.setCellValue(duration / MINUTES_PER_DAY);
        } else {
            cell.setCellValue(0L);
        }
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
        colIndex++;
        cell = row.createCell(colIndex, NUMERIC);
        if (invoiceTimereport.getDuration() != null) {
            double duration = invoiceTimereport.getDuration().toMinutes();
            cell.setCellValue(duration / MINUTES_PER_HOUR);
        } else {
            cell.setCellValue(0L);
        }
        return rowIndex;
    }

    private static void addTitleRow(Workbook workbook, InvoiceOptions options, InvoiceColumnHeaders headers, InstanceFactory factory) {
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

    private static void addSumRow(Workbook workbook, int rowIndex, InvoiceData invoiceData) {
        var options = invoiceData.getInvoiceOptions();
        Row row = workbook.getSheet(INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
        int colIndex = 1;
        if (options.isShowTimereports()) {
            colIndex++;
            if (options.isShowEmployee()) {
                colIndex++;
            }
            if (options.isShowTaskdescriptions()) {
                colIndex++;
            }
        }

        Cell cell = row.createCell(colIndex, NUMERIC);
        cell.setCellValue(((double)invoiceData.getTotalDurationVisible().toMinutes()) / MINUTES_PER_DAY);
        cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinuteBold")));
        colIndex++;
        cell = row.createCell(colIndex, NUMERIC);
        cell.setCellValue(invoiceData.getTotalHoursVisible().doubleValue());
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
        String getBudgetHeader();
        String getDurationHeader();
        String getHoursHeader();
    }
}
