package org.tb.web.util;

 
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnull;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.hssf.usermodel.HSSFDataFormatter;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.tb.GlobalConstants;
import org.tb.web.form.ShowInvoiceForm;
import org.tb.web.viewhelper.InvoiceSuborderViewHelper;
import org.tb.web.viewhelper.InvoiceTimereportViewHelper;

/**
 * This Class saves the created excel workbook 
 * on the client. The client can choose the location for file saving 
 * 
 *
 * @author la
 */
public class ExcelArchivierer {
	
	private static final String CONTENT_DISPOSITION = "attachment; filename=\"" + GlobalConstants.INVOICE_EXCEL_EXPORT_FILENAME + "\"";
	private static final String CONTENT_DISPOSITION_NEW = "attachment; filename=\"" + GlobalConstants.INVOICE_EXCEL_NEW_EXPORT_FILENAME + "\"";
	private static final Map<String, Short> cellStyleIndexes = new HashMap<String, Short>();
	
	public interface InstanceFactory {
		public Workbook createWorkbook();
		public RichTextString createRichTextString(String str);
		public DataFormatter getDataFormatter();
		public String getContentHeader();
		public String getMimeType();
	}
	
	public static InstanceFactory getHSSFFactory() {
		return new InstanceFactory() {
			private DataFormatter dataFormatter = new HSSFDataFormatter();

			@Override
			public Workbook createWorkbook() {
				return new HSSFWorkbook();
			}

			@Override
			public RichTextString createRichTextString(String str) {
				return new HSSFRichTextString(str);
			}

			@Override
			public DataFormatter getDataFormatter() {
				return dataFormatter;
			}

			@Override
			public String getContentHeader() {
				return CONTENT_DISPOSITION;
			}

			@Override
			public String getMimeType() {
				return GlobalConstants.INVOICE_EXCEL_CONTENT_TYPE;
			}
		};
	}
	
	public static InstanceFactory getXSSFFactory() {
		return new InstanceFactory() {
			private DataFormatter dataFormatter = new HSSFDataFormatter();

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

			@Override
			public String getContentHeader() {
				return CONTENT_DISPOSITION_NEW;
			}

			@Override
			public String getMimeType() {
				return GlobalConstants.INVOICE_EXCEL_NEW_CONTENT_TYPE;
			}
		};
	}
	
	public static void exportInvoice(ShowInvoiceForm showInvoiceForm, HttpServletRequest request, HttpServletResponse response, InstanceFactory factory) {
		Workbook workbook = createInvoiceExcel(showInvoiceForm, request, factory);
		response.setHeader("Content-disposition", factory.getContentHeader());
		response.setContentType(factory.getMimeType());
		try {
			ServletOutputStream out = response.getOutputStream();
			workbook.write(out);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private static Workbook createInvoiceExcel(ShowInvoiceForm showInvoiceForm, HttpServletRequest request, InstanceFactory factory) {
		Workbook workbook = factory.createWorkbook();
		createCellStyles(workbook);
		workbook.createSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME);
		addTitleRow(workbook, showInvoiceForm, request, factory);
		int rowIndex = 1;
		List<InvoiceSuborderViewHelper> invoiceSuborderViewhelpers = (List<InvoiceSuborderViewHelper>) request.getSession().getAttribute("viewhelpers");
		int layerlimit;
		try {
			layerlimit = Integer.parseInt(showInvoiceForm.getLayerlimit());
		} catch (NumberFormatException e) {
			layerlimit = -1;
		}
		for (InvoiceSuborderViewHelper invoiceSuborderViewHelper : invoiceSuborderViewhelpers) {
			if (invoiceSuborderViewHelper.getLayer() <= layerlimit || layerlimit == -1) {
				if (invoiceSuborderViewHelper.isVisible()) {
					rowIndex = addSuborderDataRow(workbook, rowIndex, invoiceSuborderViewHelper, showInvoiceForm, request, factory);
					// ab hier ggf. Timereports ausgeben
					List<InvoiceTimereportViewHelper> invoiceTimereportViewHelpers = invoiceSuborderViewHelper.getInvoiceTimereportViewHelperList();
					if (request.getSession().getAttribute("timereportsbox") != null && ((Boolean) request.getSession().getAttribute("timereportsbox"))
							&& invoiceTimereportViewHelpers.size() > 0) {
						for (InvoiceTimereportViewHelper invoiceTimereportViewHelper : invoiceTimereportViewHelpers) {
							if (invoiceTimereportViewHelper.isVisible()) {
								rowIndex = addTimereportDataRow(workbook, rowIndex, invoiceTimereportViewHelper, showInvoiceForm, request, factory);
							}
						}
					}
				}
			}
		}
		addSumRow(workbook, rowIndex, showInvoiceForm, request, factory);
		setColumnWidths(workbook.getSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME), factory);
		return workbook;
	}

	private static void createCellStyles(Workbook workbook) {
		// title-Style
		CellStyle titleCellStyle = workbook.createCellStyle();
		Font boldItalicFont = workbook.createFont();
		boldItalicFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
		boldItalicFont.setItalic(true);
		titleCellStyle.setFont(boldItalicFont);
		titleCellStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);
		titleCellStyle.setAlignment(CellStyle.ALIGN_CENTER);
		cellStyleIndexes.put("title", titleCellStyle.getIndex());
		// italic-Style
		CellStyle italicCellStyle = workbook.createCellStyle();
		Font italicFont = workbook.createFont();
		italicFont.setBoldweight(Font.BOLDWEIGHT_NORMAL);
		italicFont.setItalic(true);
		italicCellStyle.setFont(italicFont);
		italicCellStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);
		italicCellStyle.setAlignment(CellStyle.ALIGN_RIGHT);
		cellStyleIndexes.put("italic", italicCellStyle.getIndex());
		// textwrap-Style
		CellStyle textwrapCellStyle = workbook.createCellStyle();
		Font normalFont = workbook.createFont();
		normalFont.setBoldweight(Font.BOLDWEIGHT_NORMAL);
		textwrapCellStyle.setFont(normalFont);
		textwrapCellStyle.setWrapText(true);
		textwrapCellStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);
		textwrapCellStyle.setAlignment(CellStyle.ALIGN_LEFT);
		cellStyleIndexes.put("textwrap", textwrapCellStyle.getIndex());
		// hourMinute-Style
		CellStyle hourMinuteCellStyle = workbook.createCellStyle();
		hourMinuteCellStyle.setFont(normalFont);
		hourMinuteCellStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);
		hourMinuteCellStyle.setAlignment(CellStyle.ALIGN_RIGHT);
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
		boldFont.setBoldweight(Font.BOLDWEIGHT_BOLD);
		hourMinuteBoldCellStyle.setFont(boldFont);
		cellStyleIndexes.put("hourMinuteBold", hourMinuteBoldCellStyle.getIndex());
		// date-Style
		CellStyle dateCellStyle = workbook.createCellStyle();
		dateCellStyle.setFont(normalFont);
		dateCellStyle.setVerticalAlignment(CellStyle.VERTICAL_TOP);
		dateCellStyle.setAlignment(CellStyle.ALIGN_RIGHT);
		DataFormat dateFormat = workbook.createDataFormat();
		dateCellStyle.setDataFormat(dateFormat.getFormat(GlobalConstants.INVOICE_DATE_FORMAT));
//		dateCellStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("d-mmm-yy"));
		cellStyleIndexes.put("date", dateCellStyle.getIndex());
	}
	
	private static RichTextString createRTS(String str, InstanceFactory factory) {
		if(str == null) return factory.createRichTextString("");
		if(str.length() <= 255) return factory.createRichTextString(str);
		return factory.createRichTextString(str.substring(0, 255));
	}
	
	private static int addSuborderDataRow(Workbook workbook, int rowIndex, InvoiceSuborderViewHelper invoiceSuborderViewHelper, ShowInvoiceForm showInvoiceForm, HttpServletRequest request, InstanceFactory factory) {
		Row row = workbook.getSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
		rowIndex++;
		int colIndex = 0;
		Cell cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
		cell.setCellValue(createRTS(invoiceSuborderViewHelper.getSign(), factory));
		colIndex++;
		if (request.getSession().getAttribute("customeridbox") != null && ((Boolean) request.getSession().getAttribute("customeridbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
			cell.setCellValue(createRTS(invoiceSuborderViewHelper.getSuborder_customer(), factory));
			colIndex++;
		}
		if (request.getSession().getAttribute("timereportsbox") != null && ((Boolean) request.getSession().getAttribute("timereportsbox"))) {
			colIndex++;
		}
		if (request.getSession().getAttribute("employeesignbox") != null && ((Boolean) request.getSession().getAttribute("employeesignbox"))) {
			colIndex++;
		}
		cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("textwrap")));
		if ("longdescription".equals(showInvoiceForm.getSuborderdescription())) {
			cell.setCellValue(createRTS(invoiceSuborderViewHelper.getDescription(), factory));	
		} else if ("shortdescription".equals(showInvoiceForm.getSuborderdescription())) {
			cell.setCellValue(createRTS(invoiceSuborderViewHelper.getShortdescription(), factory));
		} else {
			// should be unreachable, see selectbox in showInvoice.jsp:266ff
			assert false;
		}
		colIndex++;
		if (request.getSession().getAttribute("targethoursbox") != null && ((Boolean) request.getSession().getAttribute("targethoursbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_NUMERIC);
			if (invoiceSuborderViewHelper.getDebithours() != null) {
				cell.setCellValue(invoiceSuborderViewHelper.getDebithours() / 24);
			} else {
				cell.setCellValue(0l);
			}
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
			colIndex++;
		}
		if (request.getSession().getAttribute("actualhoursbox") != null && ((Boolean) request.getSession().getAttribute("actualhoursbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_NUMERIC);
			int layerlimit;
			try {
				layerlimit = Integer.parseInt(showInvoiceForm.getLayerlimit());
			} catch (NumberFormatException e) {
				layerlimit = -1;
			}
			if (invoiceSuborderViewHelper.getLayer() < layerlimit || layerlimit == -1) {
				cell.setCellValue(new Double(invoiceSuborderViewHelper.getTotalActualminutesPrint()) / 1440);
				cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
			} else if (invoiceSuborderViewHelper.getLayer() == layerlimit) {
				cell.setCellValue(new Double(invoiceSuborderViewHelper.getDurationInMinutes()) / 1440);
				if (!invoiceSuborderViewHelper.getDuration().equals("00:00") && !invoiceSuborderViewHelper.getDuration().equals(invoiceSuborderViewHelper.getActualhoursPrint())) {
					cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
				} else {
					cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinuteItalic")));
				}
			}
			colIndex++;
		}
		return rowIndex;
	}
	
	private static int addTimereportDataRow(Workbook workbook, int rowIndex, @Nonnull InvoiceTimereportViewHelper invoiceTimereportViewHelper, ShowInvoiceForm showInvoiceForm, HttpServletRequest request, InstanceFactory factory) {
		Row row = workbook.getSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
		rowIndex++;
		int colIndex = 1;
		if (request.getSession().getAttribute("customeridbox") != null && ((Boolean) request.getSession().getAttribute("customeridbox"))) {
			colIndex++;
		}
		Cell cell = row.createCell(colIndex, Cell.CELL_TYPE_NUMERIC);
		if (invoiceTimereportViewHelper.getReferenceday().getRefdate() != null) {
			cell.setCellValue(invoiceTimereportViewHelper.getReferenceday().getRefdate());
		} else {
			cell.setCellValue(new Date()); // should not happen
		}
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("date")));
		colIndex++;
		if (request.getSession().getAttribute("employeesignbox") != null && ((Boolean) request.getSession().getAttribute("employeesignbox"))
				&& request.getSession().getAttribute("timereportsbox") != null && ((Boolean) request.getSession().getAttribute("timereportsbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
			cell.setCellValue(createRTS(invoiceTimereportViewHelper.getEmployeecontract().getEmployee().getSign(), factory));
			colIndex++;
		}
		if (request.getSession().getAttribute("timereportdescriptionbox") != null && ((Boolean) request.getSession().getAttribute("timereportdescriptionbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
			cell.setCellValue(createRTS(invoiceTimereportViewHelper.getTaskdescription(), factory));
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("textwrap")));
			colIndex++;
		} else {
			colIndex++;
		}
		if (request.getSession().getAttribute("targethoursbox") != null && ((Boolean) request.getSession().getAttribute("targethoursbox"))) {
			colIndex++;
		}
		if (request.getSession().getAttribute("actualhoursbox") != null && ((Boolean) request.getSession().getAttribute("actualhoursbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_NUMERIC);
			if (invoiceTimereportViewHelper.getDurationhours() != null) {
				double duration = (invoiceTimereportViewHelper.getDurationhours() * 60) + invoiceTimereportViewHelper.getDurationminutes();
				cell.setCellValue(duration / 1440);
			} else {
				cell.setCellValue(0l);
			}
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
		}
		return rowIndex;
	}
	
	private static void addTitleRow(Workbook workbook, ShowInvoiceForm showInvoiceForm, HttpServletRequest request, InstanceFactory factory) {
		int colIndex = 0;
		Row row = workbook.getSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME).createRow(0);
		Cell cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
		cell.setCellValue(createRTS(showInvoiceForm.getTitlesubordertext(), factory));
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
		colIndex++;
		if (request.getSession().getAttribute("customeridbox") != null && ((Boolean) request.getSession().getAttribute("customeridbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
			cell.setCellValue(createRTS(showInvoiceForm.getTitlecustomersigntext(), factory));
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
			colIndex++;
		}
		if (request.getSession().getAttribute("timereportsbox") != null && ((Boolean) request.getSession().getAttribute("timereportsbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
			cell.setCellValue(createRTS(showInvoiceForm.getTitledatetext(), factory));
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
			colIndex++;
		}
		if (request.getSession().getAttribute("employeesignbox") != null && ((Boolean) request.getSession().getAttribute("employeesignbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
			cell.setCellValue(createRTS(showInvoiceForm.getTitleemployeesigntext(), factory));
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
			colIndex++;
		}
		cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
		cell.setCellValue(createRTS(showInvoiceForm.getTitledescriptiontext(), factory));
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
		colIndex++;
		if (request.getSession().getAttribute("targethoursbox") != null && ((Boolean) request.getSession().getAttribute("targethoursbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
			cell.setCellValue(createRTS(showInvoiceForm.getTitletargethourstext(), factory));
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
			colIndex++;
		}
		if (request.getSession().getAttribute("actualhoursbox") != null && ((Boolean) request.getSession().getAttribute("actualhoursbox"))) {
			cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
			cell.setCellValue(createRTS(showInvoiceForm.getTitleactualhourstext(), factory));
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
			colIndex++;
		}
	}
	
	private static void addSumRow(Workbook workbook, int rowIndex, ShowInvoiceForm showInvoiceForm, HttpServletRequest request, InstanceFactory factory) {
		Row row = workbook.getSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
		int colIndex = 2;
		if (request.getSession().getAttribute("customeridbox") != null && ((Boolean) request.getSession().getAttribute("customeridbox"))) {
			colIndex++;
		}
		if (request.getSession().getAttribute("timereportsbox") != null && ((Boolean) request.getSession().getAttribute("timereportsbox"))) {
			colIndex++;
		}
		if (request.getSession().getAttribute("employeesignbox") != null && ((Boolean) request.getSession().getAttribute("employeesignbox"))) {
			colIndex++;
		}
		Cell cell = row.createCell(colIndex, Cell.CELL_TYPE_STRING);
		RichTextString overall;
		if (request.getSession().getAttribute("overall") != null) {
			overall = createRTS((String) request.getSession().getAttribute("overall") + ":", factory);
		} else {
			overall = createRTS("Gesamt:", factory);
		}
		cell.setCellValue(overall);
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("italic")));
		colIndex++;
		cell = row.createCell(colIndex, Cell.CELL_TYPE_NUMERIC);
		cell.setCellValue((Double) request.getSession().getAttribute("actualminutessum") / 1440);
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinuteBold")));
	}
	
	private static void setColumnWidths(Sheet sheet, InstanceFactory factory) {
		// get the highest width per column and set the column width for sheet
		Map<Integer, Integer> widthMap = new HashMap<Integer, Integer>();
		for (Iterator<Row> rowIter = sheet.rowIterator(); rowIter.hasNext();) {
			Row row = rowIter.next();
			for (Iterator<Cell> cellIter = row.cellIterator(); cellIter.hasNext();) {
				Cell cell = cellIter.next();
				Integer widthFromMap = widthMap.get(cell.getColumnIndex());
				Integer width;
				switch (cell.getCellType()) {
				case Cell.CELL_TYPE_NUMERIC:
					width = (factory.getDataFormatter().formatCellValue(cell).length() + 3) * 256;
					break;
				case Cell.CELL_TYPE_STRING:
					width = (cell.getRichStringCellValue().length() + 3) * 256;
					break;
				case Cell.CELL_TYPE_FORMULA:
					// nothing -> fall through
				case Cell.CELL_TYPE_BLANK:
					// nothing -> fall through
				case Cell.CELL_TYPE_BOOLEAN:
					// nothing -> fall through
				case Cell.CELL_TYPE_ERROR:
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
			sheet.setColumnWidth(entry.getKey(), entry.getValue());
		}
	}
}
