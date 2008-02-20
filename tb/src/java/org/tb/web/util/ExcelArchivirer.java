package org.tb.web.util;

 
import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
 

/**
 * This Class saves the created excel workbook 
 * on the client. The client can choose the location for file saving 
 * 
 *
 * @author la
 */

public class ExcelArchivirer {
	/**
	 *  Static method gets the dates from the action form. 
	 *
	 * 
	 * @return void
	 */
	public static void exportInvoice(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response) {

		ExcelArchivirer.save(ExcelWorkbookFactory.createInvoiceExcel(mapping,
				form, request, response), response);

	}

	/**
	 * Static method saves the excel workbook.

	 * 
	 * @return void  
	 */
	private static void save(HSSFWorkbook excel, HttpServletResponse resp) {

		
		resp.setHeader("Content-disposition","attachment; filename=" + "\""+ "Dateiname.xls" +"\"");
		resp.setContentType("application/msexcel");
		try { 
		ServletOutputStream out = resp.getOutputStream(); 
		excel.write(out);
		out.close();
		} 
		catch (IOException e) { 
		e.printStackTrace(); 
		}
	
	}		
}
