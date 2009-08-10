package org.tb.web.util;

 
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFDataFormat;
import org.apache.poi.hssf.usermodel.HSSFDataFormatter;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
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
	private static final Map<String, Short> cellStyleIndexes = new HashMap<String, Short>();
	private static final HSSFDataFormatter dataFormatter = new HSSFDataFormatter();
	
	public static void exportInvoice(ShowInvoiceForm showInvoiceForm, HttpServletRequest request, HttpServletResponse response) {
		HSSFWorkbook workbook = createInvoiceExcel(showInvoiceForm, request);
		response.setHeader("Content-disposition", CONTENT_DISPOSITION);
		response.setContentType(GlobalConstants.INVOICE_EXCEL_CONTENT_TYPE);
		try {
			ServletOutputStream out = response.getOutputStream();
			workbook.write(out);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private static HSSFWorkbook createInvoiceExcel(ShowInvoiceForm showInvoiceForm, HttpServletRequest request) {
		HSSFWorkbook workbook = new HSSFWorkbook();
		createCellStyles(workbook);
		workbook.createSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME);
		addTitleRow(workbook, showInvoiceForm, request);
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
					rowIndex = addSuborderDataRow(workbook, rowIndex, invoiceSuborderViewHelper, showInvoiceForm, request);
					// ab hier ggf. Timereports ausgeben
					List<InvoiceTimereportViewHelper> invoiceTimereportViewHelpers = invoiceSuborderViewHelper.getInvoiceTimereportViewHelperList();
					if (request.getSession().getAttribute("timereportsbox") != null && ((Boolean) request.getSession().getAttribute("timereportsbox"))
							&& invoiceTimereportViewHelpers.size() > 0) {
						for (InvoiceTimereportViewHelper invoiceTimereportViewHelper : invoiceTimereportViewHelpers) {
							if (invoiceTimereportViewHelper.isVisible()) {
								rowIndex = addTimereportDataRow(workbook, rowIndex, invoiceTimereportViewHelper, showInvoiceForm, request);
							}
						}
					}
				}
			}
		}
		addSumRow(workbook, rowIndex, showInvoiceForm, request);
		setColumnWidths(workbook.getSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME));
		return workbook;
	}

	private static void createCellStyles(HSSFWorkbook workbook) {
		// title-Style
		HSSFCellStyle titleCellStyle = workbook.createCellStyle();
		HSSFFont boldItalicFont = workbook.createFont();
		boldItalicFont.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
		boldItalicFont.setItalic(true);
		titleCellStyle.setFont(boldItalicFont);
		titleCellStyle.setVerticalAlignment(HSSFCellStyle.VERTICAL_TOP);
		titleCellStyle.setAlignment(HSSFCellStyle.ALIGN_CENTER);
		cellStyleIndexes.put("title", titleCellStyle.getIndex());
		// italic-Style
		HSSFCellStyle italicCellStyle = workbook.createCellStyle();
		HSSFFont italicFont = workbook.createFont();
		italicFont.setBoldweight(HSSFFont.BOLDWEIGHT_NORMAL);
		italicFont.setItalic(true);
		italicCellStyle.setFont(italicFont);
		italicCellStyle.setVerticalAlignment(HSSFCellStyle.VERTICAL_TOP);
		italicCellStyle.setAlignment(HSSFCellStyle.ALIGN_RIGHT);
		cellStyleIndexes.put("italic", italicCellStyle.getIndex());
		// textwrap-Style
		HSSFCellStyle textwrapCellStyle = workbook.createCellStyle();
		HSSFFont normalFont = workbook.createFont();
		normalFont.setBoldweight(HSSFFont.BOLDWEIGHT_NORMAL);
		textwrapCellStyle.setFont(normalFont);
		textwrapCellStyle.setWrapText(true);
		textwrapCellStyle.setVerticalAlignment(HSSFCellStyle.VERTICAL_TOP);
		textwrapCellStyle.setAlignment(HSSFCellStyle.ALIGN_LEFT);
		cellStyleIndexes.put("textwrap", textwrapCellStyle.getIndex());
		// hourMinute-Style
		HSSFCellStyle hourMinuteCellStyle = workbook.createCellStyle();
		hourMinuteCellStyle.setFont(normalFont);
		hourMinuteCellStyle.setVerticalAlignment(HSSFCellStyle.VERTICAL_TOP);
		hourMinuteCellStyle.setAlignment(HSSFCellStyle.ALIGN_RIGHT);
		HSSFDataFormat hhmmFormat = workbook.createDataFormat();
		hourMinuteCellStyle.setDataFormat(hhmmFormat.getFormat("[hh]:mm"));
		cellStyleIndexes.put("hourMinute", hourMinuteCellStyle.getIndex());
		// hourMinuteItalic-Style
		HSSFCellStyle hourMinuteItalicCellStyle = workbook.createCellStyle();
		hourMinuteItalicCellStyle.cloneStyleFrom(hourMinuteCellStyle);
		hourMinuteItalicCellStyle.setFont(italicFont);
		cellStyleIndexes.put("hourMinuteItalic", hourMinuteItalicCellStyle.getIndex());
		// hourMinuteBold-Style
		HSSFCellStyle hourMinuteBoldCellStyle = workbook.createCellStyle();
		hourMinuteBoldCellStyle.cloneStyleFrom(hourMinuteCellStyle);
		HSSFFont boldFont = workbook.createFont();
		boldFont.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
		hourMinuteBoldCellStyle.setFont(boldFont);
		cellStyleIndexes.put("hourMinuteBold", hourMinuteBoldCellStyle.getIndex());
		// date-Style
		HSSFCellStyle dateCellStyle = workbook.createCellStyle();
		dateCellStyle.setFont(normalFont);
		dateCellStyle.setVerticalAlignment(HSSFCellStyle.VERTICAL_TOP);
		dateCellStyle.setAlignment(HSSFCellStyle.ALIGN_RIGHT);
		HSSFDataFormat dateFormat = workbook.createDataFormat();
		dateCellStyle.setDataFormat(dateFormat.getFormat(GlobalConstants.INVOICE_DATE_FORMAT));
//		dateCellStyle.setDataFormat(HSSFDataFormat.getBuiltinFormat("d-mmm-yy"));
		cellStyleIndexes.put("date", dateCellStyle.getIndex());
	}
	
	private static int addSuborderDataRow(HSSFWorkbook workbook, int rowIndex, InvoiceSuborderViewHelper invoiceSuborderViewHelper, ShowInvoiceForm showInvoiceForm, HttpServletRequest request) {
		HSSFRow row = workbook.getSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
		rowIndex++;
		int colIndex = 0;
		HSSFCell cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
		if (invoiceSuborderViewHelper.getSign() != null) {
			cell.setCellValue(new HSSFRichTextString(invoiceSuborderViewHelper.getSign()));
		} else {
			cell.setCellValue(new HSSFRichTextString(""));
		}
		colIndex++;
		if (request.getSession().getAttribute("customeridbox") != null && ((Boolean) request.getSession().getAttribute("customeridbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
			if (invoiceSuborderViewHelper.getSuborder_customer() != null) {
				cell.setCellValue(new HSSFRichTextString(invoiceSuborderViewHelper.getSuborder_customer()));
			} else {
				cell.setCellValue(new HSSFRichTextString(""));
			}
			colIndex++;
		}
		if (request.getSession().getAttribute("timereportsbox") != null && ((Boolean) request.getSession().getAttribute("timereportsbox"))) {
			colIndex++;
		}
		if (request.getSession().getAttribute("employeesignbox") != null && ((Boolean) request.getSession().getAttribute("employeesignbox"))) {
			colIndex++;
		}
		cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("textwrap")));
		if ("longdescription".equals(showInvoiceForm.getSuborderdescription())) {
			if (invoiceSuborderViewHelper.getDescription() != null) {
				cell.setCellValue(new HSSFRichTextString(invoiceSuborderViewHelper.getDescription()));	
			} else {
				cell.setCellValue(new HSSFRichTextString(""));
			}
		} else if ("shortdescription".equals(showInvoiceForm.getSuborderdescription())) {
			cell.setCellValue(new HSSFRichTextString(invoiceSuborderViewHelper.getShortdescription()));
		} else {
			// should be unreachable, see selectbox in showInvoice.jsp:266ff
			assert false;
		}
		colIndex++;
		if (request.getSession().getAttribute("targethoursbox") != null && ((Boolean) request.getSession().getAttribute("targethoursbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_NUMERIC);
			if (invoiceSuborderViewHelper.getDebithours() != null) {
				cell.setCellValue(invoiceSuborderViewHelper.getDebithours() / 24);
			} else {
				cell.setCellValue(0l);
			}
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
			colIndex++;
		}
		if (request.getSession().getAttribute("actualhoursbox") != null && ((Boolean) request.getSession().getAttribute("actualhoursbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_NUMERIC);
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
	
	private static int addTimereportDataRow(HSSFWorkbook workbook, int rowIndex, InvoiceTimereportViewHelper invoiceTimereportViewHelper, ShowInvoiceForm showInvoiceForm, HttpServletRequest request) {
		HSSFRow row = workbook.getSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
		rowIndex++;
		int colIndex = 1;
		if (request.getSession().getAttribute("customeridbox") != null && ((Boolean) request.getSession().getAttribute("customeridbox"))) {
			colIndex++;
		}
		HSSFCell cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_NUMERIC);
		if (invoiceTimereportViewHelper.getReferenceday().getRefdate() != null) {
			cell.setCellValue(invoiceTimereportViewHelper.getReferenceday().getRefdate());
		} else {
			cell.setCellValue(new Date()); // should not happen
		}
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("date")));
		colIndex++;
		if (request.getSession().getAttribute("employeesignbox") != null && ((Boolean) request.getSession().getAttribute("employeesignbox"))
				&& request.getSession().getAttribute("timereportsbox") != null && ((Boolean) request.getSession().getAttribute("timereportsbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
			if (invoiceTimereportViewHelper.getEmployeecontract().getEmployee().getSign() != null) {
				cell.setCellValue(new HSSFRichTextString(invoiceTimereportViewHelper.getEmployeecontract().getEmployee().getSign()));
			} else {
				cell.setCellValue(new HSSFRichTextString(""));
			}
			colIndex++;
		}
		if (request.getSession().getAttribute("timereportdescriptionbox") != null && ((Boolean) request.getSession().getAttribute("timereportdescriptionbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
			if (invoiceTimereportViewHelper.getTaskdescription() != null) {
				cell.setCellValue(new HSSFRichTextString(invoiceTimereportViewHelper.getTaskdescription()));
			} else {
				cell.setCellValue(new HSSFRichTextString(""));
			}
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("textwrap")));
			colIndex++;
		} else {
			colIndex++;
		}
		if (request.getSession().getAttribute("targethoursbox") != null && ((Boolean) request.getSession().getAttribute("targethoursbox"))) {
			colIndex++;
		}
		if (request.getSession().getAttribute("actualhoursbox") != null && ((Boolean) request.getSession().getAttribute("actualhoursbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_NUMERIC);
			if (invoiceTimereportViewHelper.getDurationhours() != null && invoiceTimereportViewHelper != null) {
				double duration = (invoiceTimereportViewHelper.getDurationhours() * 60) + invoiceTimereportViewHelper.getDurationminutes();
				cell.setCellValue(duration / 1440);
			} else {
				cell.setCellValue(0l);
			}
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinute")));
		}
		return rowIndex;
	}
	
	private static void addTitleRow(HSSFWorkbook workbook, ShowInvoiceForm showInvoiceForm, HttpServletRequest request) {
		int colIndex = 0;
		HSSFRow row = workbook.getSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME).createRow(0);
		HSSFCell cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
		cell.setCellValue(new HSSFRichTextString(showInvoiceForm.getTitlesubordertext()));
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
		colIndex++;
		if (request.getSession().getAttribute("customeridbox") != null && ((Boolean) request.getSession().getAttribute("customeridbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
			if (showInvoiceForm.getTitlecustomersigntext() != null) {
				cell.setCellValue(new HSSFRichTextString(showInvoiceForm.getTitlecustomersigntext()));
			} else {
				cell.setCellValue(new HSSFRichTextString(""));
			}
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
			colIndex++;
		}
		if (request.getSession().getAttribute("timereportsbox") != null && ((Boolean) request.getSession().getAttribute("timereportsbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
			if (showInvoiceForm.getTitledatetext() != null) {
				cell.setCellValue(new HSSFRichTextString(showInvoiceForm.getTitledatetext()));
			} else {
				cell.setCellValue(new HSSFRichTextString(""));
			}
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
			colIndex++;
		}
		if (request.getSession().getAttribute("employeesignbox") != null && ((Boolean) request.getSession().getAttribute("employeesignbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
			if (showInvoiceForm.getTitleemployeesigntext() != null) {
				cell.setCellValue(new HSSFRichTextString(showInvoiceForm.getTitleemployeesigntext()));
			} else {
				cell.setCellValue(new HSSFRichTextString(""));
			}
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
			colIndex++;
		}
		cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
		if (showInvoiceForm.getTitledescriptiontext() != null) {
			cell.setCellValue(new HSSFRichTextString(showInvoiceForm.getTitledescriptiontext()));
		} else {
			cell.setCellValue(new HSSFRichTextString(""));
		}
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
		colIndex++;
		if (request.getSession().getAttribute("targethoursbox") != null && ((Boolean) request.getSession().getAttribute("targethoursbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
			if (showInvoiceForm.getTitletargethourstext() != null) {
				cell.setCellValue(new HSSFRichTextString(showInvoiceForm.getTitletargethourstext()));
			} else {
				cell.setCellValue(new HSSFRichTextString(""));
			}
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
			colIndex++;
		}
		if (request.getSession().getAttribute("actualhoursbox") != null && ((Boolean) request.getSession().getAttribute("actualhoursbox"))) {
			cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
			if (showInvoiceForm.getTitleactualhourstext() != null) {
				cell.setCellValue(new HSSFRichTextString(showInvoiceForm.getTitleactualhourstext()));
			} else {
				cell.setCellValue(new HSSFRichTextString(""));
			}
			cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("title")));
			colIndex++;
		}
	}
	
	private static void addSumRow(HSSFWorkbook workbook, int rowIndex, ShowInvoiceForm showInvoiceForm, HttpServletRequest request) {
		HSSFRow row = workbook.getSheet(GlobalConstants.INVOICE_EXCEL_SHEET_NAME).createRow(rowIndex);
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
		HSSFCell cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_STRING);
		HSSFRichTextString overall;
		if (request.getSession().getAttribute("overall") != null) {
			overall = new HSSFRichTextString((String) request.getSession().getAttribute("overall") + ":");
		} else {
			overall = new HSSFRichTextString("Gesamt:");
		}
		cell.setCellValue(overall);
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("italic")));
		colIndex++;
		cell = row.createCell(colIndex, HSSFCell.CELL_TYPE_NUMERIC);
		cell.setCellValue((Double) request.getSession().getAttribute("actualminutessum") / 1440);
		cell.setCellStyle(workbook.getCellStyleAt(cellStyleIndexes.get("hourMinuteBold")));
	}
	
	@SuppressWarnings("unchecked")
	private static void setColumnWidths(HSSFSheet sheet) {
		// get the highest width per column and set the column width for sheet
		Map<Integer, Integer> widthMap = new HashMap<Integer, Integer>();
		for (Iterator<HSSFRow> rowIter = sheet.rowIterator(); rowIter.hasNext();) {
			HSSFRow row = rowIter.next();
			for (Iterator<HSSFCell> cellIter = row.cellIterator(); cellIter.hasNext();) {
				HSSFCell cell = cellIter.next();
				Integer widthFromMap = widthMap.get(cell.getColumnIndex());
				Integer width;
				switch (cell.getCellType()) {
				case HSSFCell.CELL_TYPE_NUMERIC:
					width = (dataFormatter.formatCellValue(cell).length() + 3) * 256;
					break;
				case HSSFCell.CELL_TYPE_STRING:
					width = (cell.getRichStringCellValue().length() + 3) * 256;
					break;
				case HSSFCell.CELL_TYPE_FORMULA:
					// nothing -> fall through
				case HSSFCell.CELL_TYPE_BLANK:
					// nothing -> fall through
				case HSSFCell.CELL_TYPE_BOOLEAN:
					// nothing -> fall through
				case HSSFCell.CELL_TYPE_ERROR:
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
