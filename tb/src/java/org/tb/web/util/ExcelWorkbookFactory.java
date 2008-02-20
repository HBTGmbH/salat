package org.tb.web.util;

import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.web.form.ShowInvoiceForm;
import org.tb.web.viewhelper.InvoiceSuborderViewHelper;
import org.tb.web.viewhelper.InvoiceTimereportViewHelper;


/**
 * Build the excel file from invoice overview.  
 *
 *
 * @author la
 */


public class ExcelWorkbookFactory {
	
	/**
	 * Util method for converting time to double
	 * 
	 * @param str is the String to be converted  
	 * @return Double 
	 */
	public static Double timeToExcelDouble(String str) {
		// splitet 999:00 zu [999] und [00]
		String[] mom = str.split(":");
		Double doub = Double.valueOf(mom[0]);
		doub = doub + Double.valueOf(mom[1]) / 60;
		return doub;
	}

	/**
	 * Build the excel workbook 
	 * 
	 * @see ActionForward executeAuthenticated
	 * @param mapping 
	 * @param form
	 * @param request
	 * @param response
	 * @return HSSFWorkbook 
	 */
	public static HSSFWorkbook createInvoiceExcel(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {

		// check if special tasks initiated from the daily display need to be
		// carried out...
		ShowInvoiceForm invoiceForm = (ShowInvoiceForm) form;
		System.out.println("ICH EXPORTIERE EXEL--------------!!!!!!!--------");

		String[] suborderIdArray = invoiceForm.getSuborderIdArray();
		String[] timereportIdArray = invoiceForm.getTimereportIdArray();
		List<InvoiceSuborderViewHelper> suborderViewhelperList = (List<InvoiceSuborderViewHelper>) request
				.getSession().getAttribute("viewhelpers");

		for (InvoiceSuborderViewHelper invoiceSuborderViewHelper : suborderViewhelperList) {
			for (int i = 0; i < suborderIdArray.length; i++) {
				if (suborderIdArray[i].equals(String
						.valueOf(invoiceSuborderViewHelper.getId()))) {
					invoiceSuborderViewHelper.setVisible(true);
					// drucke was aus

					break;
				} else {
					invoiceSuborderViewHelper.setVisible(false);
				}
			}
			for (InvoiceTimereportViewHelper invoiceTimereportViewHelper : invoiceSuborderViewHelper
					.getInvoiceTimereportViewHelperList()) {
				for (int i = 0; i < timereportIdArray.length; i++) {
					if (timereportIdArray[i].equals(String
							.valueOf(invoiceTimereportViewHelper.getId()))) {
						invoiceTimereportViewHelper.setVisible(true);
						break;
					} else {
						invoiceTimereportViewHelper.setVisible(false);
					}
				}
			}
		}
		String actualHoursSum;
		int actualhours = 0;
		int actualminutes = 0;

		for (InvoiceSuborderViewHelper invoiceSuborderViewHelperSum : suborderViewhelperList) {
			if (invoiceSuborderViewHelperSum.isVisible()) {
				StringTokenizer stringTokenizer = new StringTokenizer(
						invoiceSuborderViewHelperSum.getActualhours(), ":");
				String hoursToken = stringTokenizer.nextToken();
				String minutesToken = stringTokenizer.nextToken();
				actualminutes += Integer.parseInt(minutesToken);
				int tempHours = actualminutes / 60;
				actualminutes = actualminutes % 60;
				actualhours += Integer.parseInt(hoursToken) + tempHours;

			}
		}

		String actualMinutesString = "";
		if (actualminutes < 10) {
			actualMinutesString += "0";
		}
		actualMinutesString += actualminutes;
		actualHoursSum = actualhours + ":" + actualMinutesString;

		// Outcheking of CheckBoxes

		boolean customSignBoxOn = (Boolean) request.getSession().getAttribute(
				"customeridbox");
		boolean dateBoxOn = (Boolean) request.getSession().getAttribute(
				"timereportsbox");
		boolean employeeSignBoxOn = (Boolean) request.getSession()
				.getAttribute("employeesignbox");
		boolean targetHoursBoxOn = (Boolean) request.getSession().getAttribute(
				"targethoursbox");
		boolean actualHoursBoxOn = (Boolean) request.getSession().getAttribute(
				"actualhoursbox");
		boolean timeReportCommentBox = (Boolean) request.getSession()
				.getAttribute("timereportdescriptionbox");
		String descriptionKind = (String) request.getSession().getAttribute(
				"optionsuborderdescription");

		HSSFWorkbook wb = new HSSFWorkbook();

		// Sheet name
		HSSFSheet s = wb.createSheet("Rechnung");
		s.setColumnWidth((short) 0, (short) 3100);
		s.setColumnWidth((short) 1, (short) 3100);
		s.setColumnWidth((short) 2, (short) 3100);
		s.setColumnWidth((short) 3, (short) 3100);
		s.setColumnWidth((short) 4, (short) 3100);
		s.setColumnWidth((short) 5, (short) 3100);
		s.setColumnWidth((short) 6, (short) 3100);
		s.setColumnWidth((short) 7, (short) 3100);
		HSSFRow r;
		HSSFCell c;

		// normal style
		HSSFCellStyle ns = wb.createCellStyle();
		HSSFFont f1 = wb.createFont();
		f1.setBoldweight(HSSFFont.BOLDWEIGHT_NORMAL);
		ns.setFont(f1);
		ns.setVerticalAlignment(HSSFCellStyle.VERTICAL_TOP);
		ns.setAlignment(HSSFCellStyle.ALIGN_LEFT);

		// bold style
		HSSFCellStyle bs = wb.createCellStyle();
		HSSFFont f = wb.createFont();
		f.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
		bs.setFont(f);
		bs.setVerticalAlignment(HSSFCellStyle.VERTICAL_TOP);
		bs.setAlignment(HSSFCellStyle.ALIGN_LEFT);
		// cs.setWrapText(true);

		// Wrapped style
		HSSFCellStyle csWrap = wb.createCellStyle();
		csWrap.setWrapText(true);
		csWrap.setVerticalAlignment(HSSFCellStyle.VERTICAL_TOP);
		csWrap.setAlignment(HSSFCellStyle.ALIGN_LEFT);

		// Time style
		HSSFCellStyle csTime = wb.createCellStyle();

		csTime.setDataFormat(wb.createDataFormat().getFormat("0.00"));
		csTime.setVerticalAlignment(HSSFCellStyle.VERTICAL_TOP);
		csTime.setAlignment(HSSFCellStyle.ALIGN_LEFT);

		// Headline row

		// String values for each row
		String subOrder = invoiceForm.getTitlesubordertext();
		String customerSign = invoiceForm.getTitlecustomersigntext();
		String date = invoiceForm.getTitledatetext();
		String employeeSign = invoiceForm.getTitleemployeesigntext();
		String discription = invoiceForm.getTitledescriptiontext();
		String targetHours = invoiceForm.getTitletargethourstext();
		String actualHours = invoiceForm.getTitleactualhourstext();
		
		// create a numeric cell
		r = s.createRow(1);
		short cellIndex = 0;
		c = r.createCell(cellIndex);
		cellIndex += 1;
		c.setCellValue(new HSSFRichTextString(subOrder));
		c.setCellStyle(bs);

		if (customSignBoxOn) {
			c = r.createCell(cellIndex);
			cellIndex += 1;
			c.setCellValue(new HSSFRichTextString(customerSign));
			c.setCellStyle(bs);
		}
		if (dateBoxOn) {
			c = r.createCell(cellIndex);
			cellIndex += 1;
			c.setCellValue(new HSSFRichTextString(date));
			c.setCellStyle(bs);
		}
		if (employeeSignBoxOn) {
			c = r.createCell(cellIndex);
			cellIndex += 1;
			c.setCellValue(new HSSFRichTextString(employeeSign));
			c.setCellStyle(bs);
		}
		c = r.createCell(cellIndex);
		cellIndex += 1;
		// s.setColumnWidth(cellIndex,(short) 8000);
		c.setCellValue(new HSSFRichTextString(discription));
		c.setCellStyle(bs);

		if (targetHoursBoxOn) {
			c = r.createCell(cellIndex);
			cellIndex += 1;
			c.setCellValue(new HSSFRichTextString(targetHours));
			c.setCellStyle(bs);
		}
		if (actualHoursBoxOn) {
			c = r.createCell(cellIndex);
			cellIndex += 1;
			c.setCellValue(new HSSFRichTextString(actualHours));
			c.setCellStyle(bs);
		}
		// End Headline

		int RowIndex = 2;

		for (InvoiceSuborderViewHelper invoiceSuborderViewHelper : suborderViewhelperList) {
			cellIndex = 0;
			if (invoiceSuborderViewHelper.isVisible()) {
				r = s.createRow(RowIndex);
				c = r.createCell(cellIndex);
				cellIndex += 1;
				c.setCellValue(new HSSFRichTextString(invoiceSuborderViewHelper
						.getSign()));
				c.setCellStyle(ns);

				if (customSignBoxOn) {
					c = r.createCell(cellIndex);
					cellIndex += 1;
					c.setCellValue(new HSSFRichTextString(
							invoiceSuborderViewHelper.getSuborder_customer()));
					c.setCellStyle(ns);
				}
				if (dateBoxOn) {
					c = r.createCell(cellIndex);
					cellIndex += 1;
					c.setCellValue(new HSSFRichTextString(""));
					c.setCellStyle(ns);
				}
				if (employeeSignBoxOn) {
					c = r.createCell(cellIndex);
					c.setCellValue(new HSSFRichTextString(""));
					cellIndex += 1;
					c.setCellStyle(ns);
				}
				if (descriptionKind.equals("longdescription")) {
					c = r.createCell(cellIndex);
					s.setColumnWidth(cellIndex, (short) 9000);
					c.setCellValue(new HSSFRichTextString(
							invoiceSuborderViewHelper.getDescription()));
					c.setCellStyle(csWrap);
					cellIndex += 1;
				}
				if (descriptionKind.equals("shortdescription")) {
					c = r.createCell(cellIndex);
					s.setColumnWidth(cellIndex, (short) 9000);
					c.setCellValue(new HSSFRichTextString(
							invoiceSuborderViewHelper.getShortdescription()));
					c.setCellStyle(csWrap);
					cellIndex += 1;
				}

				if (targetHoursBoxOn) {
					c = r.createCell(cellIndex);
					cellIndex += 1;
					if (invoiceSuborderViewHelper.getDebithours() != "")
						c.setCellValue(timeToExcelDouble(invoiceSuborderViewHelper
										.getDebithours()));
					else
						c.setCellValue(new HSSFRichTextString(
								(invoiceSuborderViewHelper.getDebithours())));

					c.setCellStyle(csTime);
				}
				if (actualHoursBoxOn) {
					c = r.createCell(cellIndex);
					cellIndex += 1;
					if (invoiceSuborderViewHelper.getDebithours() != "")
						c
								.setCellValue(timeToExcelDouble(invoiceSuborderViewHelper
										.getActualhours()));
					else
						c.setCellValue(0.00);
// 0 Hours for Actualhours	
//	(invoiceSuborderViewHelper.getActualhours())));	
					c.setCellStyle(csTime);
				}

			}
			RowIndex += 1;

			if (invoiceSuborderViewHelper.getInvoiceTimereportViewHelperList()
					.size() > 0
					&& dateBoxOn) {

				for (InvoiceTimereportViewHelper invoiceTimereportViewHelper : invoiceSuborderViewHelper
						.getInvoiceTimereportViewHelperList()) {
					short cellIndexSub = 1;
					if (invoiceTimereportViewHelper.isVisible()) {
						r = s.createRow(RowIndex);

						if (customSignBoxOn) {
							c = r.createCell(cellIndexSub);
							cellIndexSub += 1;
							c.setCellValue(new HSSFRichTextString(""));
							c.setCellStyle(ns);
						}
						c = r.createCell(cellIndexSub);
						cellIndexSub += 1;
						// Date format has to be change
						c.setCellValue(new HSSFRichTextString(
								invoiceTimereportViewHelper.getReferenceday()
										.getRefdate().toString()));
						c.setCellStyle(ns);

						if (employeeSignBoxOn && dateBoxOn) {
							c = r.createCell(cellIndexSub);
							cellIndexSub += 1;
							c.setCellValue(new HSSFRichTextString(
									invoiceTimereportViewHelper
											.getEmployeecontract()
											.getEmployee().getSign()));
							c.setCellStyle(ns);
						}

						if (timeReportCommentBox) {
							c = r.createCell(cellIndexSub);
							cellIndexSub += 1;
							c.setCellValue(new HSSFRichTextString(
									invoiceTimereportViewHelper
											.getTaskdescription()));
							c.setCellStyle(ns);
						}

						if (targetHoursBoxOn) {
							c = r.createCell(cellIndexSub);
							cellIndexSub += 1;
							c.setCellValue(new HSSFRichTextString(""));
							c.setCellStyle(csTime);
							//  
						}
						if (actualHoursBoxOn) {
					
							c = r.createCell(cellIndexSub);
							cellIndexSub += 1;
							String time = invoiceTimereportViewHelper
									.getDurationhours().toString()
									+ ":";
							if (invoiceTimereportViewHelper
									.getDurationminutes() < 10) {
								time += "0";
							} else {
								time += invoiceTimereportViewHelper
										.getDurationminutes().toString();
							}
							c.setCellValue(timeToExcelDouble(time));
							c.setCellStyle(csTime);
						}

					}
					RowIndex += 1;
				}
			}
		}

// Footer is not used
//		short cIndex = 0;
//
//		if (actualHoursBoxOn) {
//// zusatz zeile	
//			r = s.createRow(RowIndex + 1);
//			if (customSignBoxOn)
//				cIndex += 1;
//			if (dateBoxOn)
//				cIndex += 1;
//			if (employeeSignBoxOn && dateBoxOn)
//				cIndex += 1;
//			if (targetHoursBoxOn) {
//				cIndex += 2;
//				r = s.createRow(RowIndex + 1);
//				c = r.createCell(cIndex);
//				c.setCellValue(new HSSFRichTextString("Gesamt : "));
//				c.setCellStyle(bs);
//			}
//			if (!targetHoursBoxOn) {
//				c = r.createCell(cIndex);
//				c.setCellValue(new HSSFRichTextString("Gesamt : "));
//				c.setCellStyle(bs);
//			}
//			cIndex += 1;
//			c = r.createCell(cIndex);
//			c.setCellValue(new HSSFRichTextString((String) request.getSession()
//					.getAttribute("printactualhourssum")));
//			c.setCellStyle(bs);
//
//		}
		
		return wb;
	}
}
