package org.tb.web.action;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.bdom.comparators.SubOrderComparator;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.util.OptionItem;
import org.tb.web.form.ShowInvoiceForm;
import org.tb.web.util.ExcelArchivirer;
import org.tb.web.viewhelper.InvoiceSuborderViewHelper;
import org.tb.web.viewhelper.InvoiceTimereportViewHelper;

public class ShowInvoiceAction extends DailyReportAction {

	private OvertimeDAO overtimeDAO;

	private CustomerorderDAO customerorderDAO;

	private TimereportDAO timereportDAO;

	private EmployeecontractDAO employeecontractDAO;

	private SuborderDAO suborderDAO;

	private EmployeeorderDAO employeeorderDAO;

	private VacationDAO vacationDAO;

	private PublicholidayDAO publicholidayDAO;

	private WorkingdayDAO workingdayDAO;

	private EmployeeDAO employeeDAO;

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

	public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
		this.workingdayDAO = workingdayDAO;
	}

	public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
		this.publicholidayDAO = publicholidayDAO;
	}

	public void setVacationDAO(VacationDAO vacationDAO) {
		this.vacationDAO = vacationDAO;
	}

	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}

	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}

	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
		this.overtimeDAO = overtimeDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
		// check if special tasks initiated from the daily display need to be
		// carried out...
		ShowInvoiceForm invoiceForm = (ShowInvoiceForm) form;
		TimereportHelper th = new TimereportHelper();

		Map<String, String> monthMap = new HashMap<String, String>();
		monthMap.put("0", "main.timereport.select.month.jan.text");
		monthMap.put("1", "main.timereport.select.month.feb.text");
		monthMap.put("2", "main.timereport.select.month.mar.text");
		monthMap.put("3", "main.timereport.select.month.apr.text");
		monthMap.put("4", "main.timereport.select.month.mai.text");
		monthMap.put("5", "main.timereport.select.month.jun.text");
		monthMap.put("6", "main.timereport.select.month.jul.text");
		monthMap.put("7", "main.timereport.select.month.aug.text");
		monthMap.put("8", "main.timereport.select.month.sep.text");
		monthMap.put("9", "main.timereport.select.month.oct.text");
		monthMap.put("10", "main.timereport.select.month.nov.text");
		monthMap.put("11", "main.timereport.select.month.dec.text");

		// call on InvoiceView with parameter refreshInvoceForm to update
		// request
		if ((request.getParameter("task") != null) && (request.getParameter("task").equals("generateMaximumView"))) {
			String selectedView = invoiceForm.getInvoiceview();
			List<InvoiceSuborderViewHelper> invoiceSuborderViewHelperList = new LinkedList<InvoiceSuborderViewHelper>();
			List<Suborder> suborderList;
			Customerorder customerOrder;
			Date dateFirst;
			Date dateLast;
			if (!invoiceForm.getOrder().equals("CHOOSE ORDER")) {
				if (selectedView.equals(GlobalConstants.VIEW_MONTHLY)) {
					// generate dates for monthly view mode
					try {
						// request.getSession().setAttribute("invoiceview",
						// GlobalConstants.VIEW_MONTHLY);
						dateFirst = th.getDateFormStrings("1", invoiceForm.getFromMonth(), invoiceForm.getFromYear(), false);
						GregorianCalendar gc = new GregorianCalendar();
						gc.setTime(dateFirst);
						int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
						String maxDayString = "";
						if (maxday < 10) {
							maxDayString += "0";
						}
						maxDayString += maxday;
						dateLast = th.getDateFormStrings(maxDayString, invoiceForm.getFromMonth(), invoiceForm.getFromYear(), false);
					} catch (Exception e) {
						throw new RuntimeException("date cannot be parsed for form");
					}

					customerOrder = customerorderDAO.getCustomerorderBySign(invoiceForm.getOrder());
					if (invoiceForm.getSuborder().equals("ALL SUBORDERS")) {
						suborderList = suborderDAO.getSubordersByCustomerorderId(customerOrder.getId());
					} else {
						suborderList = suborderDAO.getSuborderById(Long.parseLong(invoiceForm.getSuborder())).getAllChildren();
					}
					Collections.sort(suborderList, new SubOrderComparator());
					java.sql.Date sqlDateFirst = new java.sql.Date(dateFirst.getTime());
					java.sql.Date sqlDateLast = new java.sql.Date(dateLast.getTime());
					List<Suborder> suborderListTemp = new LinkedList<Suborder>();
					if (invoiceForm.isInvoicebox()) {
						request.getSession().setAttribute("targethourssum",	fillViewHelper(suborderList, invoiceSuborderViewHelperList,	sqlDateFirst, sqlDateLast, invoiceForm));
					} else {
						for (Suborder suborder : suborderList) {
							if (suborder.getInvoice() == 'Y') {
								suborderListTemp.add(suborder);
							}
						}
						request.getSession().setAttribute("targethourssum", fillViewHelper(suborderListTemp, invoiceSuborderViewHelperList, sqlDateFirst, sqlDateLast, invoiceForm));
					}
					request.getSession().setAttribute("viewhelpers", invoiceSuborderViewHelperList);
				} else if (selectedView.equals(GlobalConstants.VIEW_CUSTOM)) {
					// generate dates for a period of time in custom view mode
					try {
						// request.getSession().setAttribute("invoiceview",
						// GlobalConstants.VIEW_CUSTOM);
						dateFirst = th.getDateFormStrings(invoiceForm.getFromDay(), invoiceForm.getFromMonth(), invoiceForm.getFromYear(), false);
						if (invoiceForm.getUntilDay() == null || invoiceForm.getUntilMonth() == null || invoiceForm.getUntilYear() == null) {
							GregorianCalendar gc = new GregorianCalendar();
							gc.setTime(dateFirst);
							int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
							String maxDayString = "";
							if (maxday < 10) {
								maxDayString += "0";
							}
							maxDayString += maxday;
							invoiceForm.setUntilDay(maxDayString);
							invoiceForm.setUntilMonth(invoiceForm.getFromMonth());
							invoiceForm.setUntilYear(invoiceForm.getFromYear());
						}
						dateLast = th.getDateFormStrings(invoiceForm.getUntilDay(), invoiceForm.getUntilMonth(), invoiceForm.getUntilYear(), false);
					} catch (Exception e) {
						throw new RuntimeException("date cannot be parsed for form");
					}
					customerOrder = customerorderDAO.getCustomerorderBySign(invoiceForm.getOrder());
					if (invoiceForm.getSuborder().equals("ALL SUBORDERS")) {
						suborderList = suborderDAO.getSubordersByCustomerorderId(customerOrder.getId());
					} else {
						suborderList = suborderDAO.getSuborderById(Long.parseLong(invoiceForm.getSuborder())).getAllChildren();
					}
					Collections.sort(suborderList, new SubOrderComparator());
					java.sql.Date sqlDateFirst = new java.sql.Date(dateFirst.getTime());
					java.sql.Date sqlDateLast = new java.sql.Date(dateLast.getTime());
					List<Suborder> suborderListTemp = new LinkedList<Suborder>();
					if (invoiceForm.isInvoicebox()) {
						request.getSession().setAttribute("targethourssum", fillViewHelper(suborderList, invoiceSuborderViewHelperList, sqlDateFirst, sqlDateLast, invoiceForm));
					} else {
						for (Suborder suborder : suborderList) {
							if (suborder.getInvoice() == 'Y') {
								suborderListTemp.add(suborder);
							}
						}
						request.getSession().setAttribute("targethourssum", fillViewHelper(suborderListTemp, invoiceSuborderViewHelperList, sqlDateFirst, sqlDateLast, invoiceForm));
					}
					request.getSession().setAttribute("viewhelpers", invoiceSuborderViewHelperList);
				} else {
					throw new RuntimeException("no view type selected");
				}
				request.getSession().setAttribute("customername", customerOrder.getCustomer().getName());
				request.getSession().setAttribute("customeraddress", customerOrder.getCustomer().getAddress());
				GregorianCalendar gc = new GregorianCalendar();
				gc.setTime(dateFirst);
				request.getSession().setAttribute("dateMonth", monthMap.get(String.valueOf(gc.get(Calendar.MONTH))));
				request.getSession().setAttribute("dateYear", gc.get(Calendar.YEAR));
				request.getSession().setAttribute("dateFirst",gc.get(Calendar.DATE) + "." + (gc.get(Calendar.MONTH) + 1) + "." + gc.get(Calendar.YEAR));
				gc.setTime(dateLast);
				request.getSession().setAttribute("dateLast", gc.get(Calendar.DATE) + "." + (gc.get(Calendar.MONTH) + 1) + "." + gc.get(Calendar.YEAR));
				request.getSession().setAttribute("currentOrderObject", customerOrder);
			} else {
				request.setAttribute("errorMessage", "No customer order selected. Please choose.");
			}
			return mapping.findForward("success");
		} else if ((request.getParameter("task") != null) && (request.getParameter("task").equals("refreshInvoiceForm"))) {
			// call on InvoiceView with parameter refreshInvoceForm to update
			// request
			if ((invoiceForm.getOrder().equals(null)) || (invoiceForm.getOrder().equals("CHOOSE ORDER"))) {
				request.getSession().setAttribute("currentOrder", "main.invoice.choose.text");
			} else {
				request.getSession().setAttribute("currentOrder", invoiceForm.getOrder());
				request.getSession().setAttribute("currentSuborder", invoiceForm.getSuborder());
				List<Suborder> suborders = suborderDAO.getSubordersByCustomerorderId(customerorderDAO.getCustomerorderBySign(invoiceForm.getOrder()).getId());
				Collections.sort(suborders, new SubOrderComparator());
				request.getSession().setAttribute("suborders", suborders);
			}

			/*
			 * Delete resultset if the customerorder of the invoice form has
			 * changed if(request.getSession().getAttribute("viewhelpers") !=
			 * null){ List<InvoiceSuborderViewHelper>
			 * invoiceSuborderViewHelperList = (List<InvoiceSuborderViewHelper>)
			 * request.getSession().getAttribute("viewhelpers");
			 * invoiceSuborderViewHelperList.get(0).getParentorder().equals(customerorderDAO.getCustomerorderBySign(invoiceForm.getOrder())); }
			 */

			// activate subcheckboxes for timereport-attributes
			if (invoiceForm.isTimereportsbox()) {
				request.getSession().setAttribute("timereportsubboxes", true);
			} else {
				request.getSession().setAttribute("timereportsubboxes", false);
				invoiceForm.setTimereportdescriptionbox(false);
				invoiceForm.setEmployeesignbox(false);
			}

			// selected view
			String selectedView = invoiceForm.getInvoiceview();
			if (selectedView.equals(GlobalConstants.VIEW_MONTHLY)) {
				request.getSession().setAttribute("invoiceview", GlobalConstants.VIEW_MONTHLY);
			} else if (selectedView.equals(GlobalConstants.VIEW_CUSTOM)) {
				request.getSession().setAttribute("invoiceview", GlobalConstants.VIEW_CUSTOM);
				//TODO: Baustelle Mantis 2437
//				String nextMonth = calculateNextMonth(invoiceForm.getFromMonth());
//				if (!GlobalConstants.MONTH_SHORTFORM_JANUARY.equals(nextMonth)) {
//					// -> kein Jahreswechsel nötig für untilYear
//					invoiceForm.setUntilMonth(nextMonth);
//					invoiceForm.setUntilYear(invoiceForm.getFromYear());
//				} else {
//					// -> Jahreswechsel nötig
//					String nextYear = calculateNextYear(invoiceForm.getFromYear());
//					if (nextYear != null) {
//						// nächstes Jahr ist auswählbar
//						invoiceForm.setUntilMonth(nextMonth);
//						invoiceForm.setUntilYear(nextYear);
//					} else {
//						// nächstes Jahr ist nicht auswählbar
//						invoiceForm.setUntilDay("31");
//						invoiceForm.setUntilMonth(invoiceForm.getFromMonth());
//						invoiceForm.setUntilYear(invoiceForm.getFromYear());
//					}
//				}
			} else {
				throw new RuntimeException("no view type selected");
			}
			request.getSession().setAttribute("customeridbox", invoiceForm.isCustomeridbox());
			request.getSession().setAttribute("targethoursbox", invoiceForm.isTargethoursbox());
			request.getSession().setAttribute("actualhoursbox", invoiceForm.isActualhoursbox());
			request.getSession().setAttribute("employeesignbox", invoiceForm.isEmployeesignbox());
			request.getSession().setAttribute("timereportdescriptionbox", invoiceForm.isTimereportdescriptionbox());
			request.getSession().setAttribute("timereportsbox",	invoiceForm.isTimereportsbox());
			request.getSession().setAttribute("currentDay",	invoiceForm.getFromDay());
			request.getSession().setAttribute("currentMonth", invoiceForm.getFromMonth());
			request.getSession().setAttribute("currentYear", invoiceForm.getFromYear());
			request.getSession().setAttribute("lastDay", invoiceForm.getUntilDay());
			request.getSession().setAttribute("lastMonth", invoiceForm.getUntilMonth());
			request.getSession().setAttribute("lastYear", invoiceForm.getUntilYear());
			request.getSession().setAttribute("optionmwst",	invoiceForm.getMwst());
			request.getSession().setAttribute("optionsuborderdescription", invoiceForm.getSuborderdescription());
			request.getSession().setAttribute("layerlimit",	invoiceForm.getLayerlimit());
			// if (invoiceForm.getCustomeraddress() != null
			// && invoiceForm.getCustomername() != null) {
			request.getSession().setAttribute("customername", invoiceForm.getCustomername());
			String customeraddress = invoiceForm.getCustomeraddress();
			request.getSession().setAttribute("customeraddress", customeraddress);
			// }

			return mapping.findForward("success");
		} else  if ((request.getParameter("task") != null) && (request.getParameter("task").equals("export"))) {
			ExcelArchivirer.exportInvoice(mapping, form, request, response);
			return mapping.getInputForward();
		} else if ((request.getParameter("task") != null) && (request.getParameter("task").equals("print"))) {
			// call on InvoiceView with parameter print
			String[] suborderIdArray = invoiceForm.getSuborderIdArray();
			String[] timereportIdArray = invoiceForm.getTimereportIdArray();
			List<InvoiceSuborderViewHelper> suborderViewhelperList = (List<InvoiceSuborderViewHelper>) request.getSession().getAttribute("viewhelpers");
			for (InvoiceSuborderViewHelper invoiceSuborderViewHelper : suborderViewhelperList) {
				for (int i = 0; i < suborderIdArray.length; i++) {
					if (suborderIdArray[i].equals(String.valueOf(invoiceSuborderViewHelper.getId()))) {
						invoiceSuborderViewHelper.setVisible(true);
						break;
					} else {
						invoiceSuborderViewHelper.setVisible(false);
					}
				}
				for (InvoiceTimereportViewHelper invoiceTimereportViewHelper : invoiceSuborderViewHelper.getInvoiceTimereportViewHelperList()) {
					for (int i = 0; i < timereportIdArray.length; i++) {
						if (timereportIdArray[i].equals(String.valueOf(invoiceTimereportViewHelper.getId()))) {
							invoiceTimereportViewHelper.setVisible(true);
							break;
						} else {
							invoiceTimereportViewHelper.setVisible(false);
						}
					}
				}
			}
			int actualhours = 0;
			int actualminutes = 0;
			for (InvoiceSuborderViewHelper invoiceSuborderViewHelperSum : suborderViewhelperList) {
				if (invoiceSuborderViewHelperSum.isVisible()) {
					StringTokenizer stringTokenizer = new StringTokenizer(invoiceSuborderViewHelperSum.getActualhours(), ":");
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
			String actualHoursSum = actualhours + ":" + actualMinutesString;
			request.getSession().setAttribute("titleactualhourstext", invoiceForm.getTitleactualhourstext());
			request.getSession().setAttribute("titlecustomersigntext", invoiceForm.getTitlecustomersigntext());
			request.getSession().setAttribute("titleinvoiceattachment", invoiceForm.getTitleinvoiceattachment());
			request.getSession().setAttribute("titledatetext", invoiceForm.getTitledatetext());
			request.getSession().setAttribute("titledescriptiontext", invoiceForm.getTitledescriptiontext());
			request.getSession().setAttribute("titleemployeesigntext", invoiceForm.getTitleemployeesigntext());
			request.getSession().setAttribute("titlesubordertext", invoiceForm.getTitlesubordertext());
			request.getSession().setAttribute("titletargethourstext", invoiceForm.getTitletargethourstext());
			request.getSession().setAttribute("printactualhourssum", actualHoursSum);
			request.getSession().setAttribute("suborderdescription", invoiceForm.getSuborderdescription());
			request.getSession().setAttribute("customername", invoiceForm.getCustomername());
			String customeraddress = invoiceForm.getCustomeraddress();
			customeraddress = customeraddress.replace("\r\n", "<br/>");
			customeraddress = customeraddress.replace("\n", "<br/>");
			customeraddress = customeraddress.replace("\r", "<br/>");
			request.getSession().setAttribute("customeraddress", customeraddress);
			return mapping.findForward("print");
		} else if (request.getParameter("task") != null) {
			// END
			// call on InvoiceView with any parameter to forward or go back
			// just go back to main menu
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				return mapping.findForward("backtomenu");
			} else {
				return mapping.findForward("success");
			}
		} else if (request.getParameter("task") == null) {
			// call on invoiceView without a parameter
			// set monthly view as standard
			invoiceForm.setInvoiceview(GlobalConstants.VIEW_MONTHLY);
			request.getSession().setAttribute("invoiceview", GlobalConstants.VIEW_MONTHLY);
			// no special task - prepare everything to show invoice
			Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
			EmployeeHelper eh = new EmployeeHelper();
			Employeecontract ec = eh.setCurrentEmployee(loginEmployee, request, employeeDAO, employeecontractDAO);
			if (ec == null) {
				request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
				return mapping.findForward("error");
			}
			request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());
			request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());
			request.getSession().setAttribute("orders", customerorderDAO.getCustomerorders());
			request.getSession().setAttribute("suborders", new LinkedList<Suborder>());
			request.getSession().setAttribute("optionmwst", "19");
			request.getSession().setAttribute("layerlimit", "-1");

			// selected view and selected dates
			request.getSession().setAttribute("invoiceview", invoiceForm.getInvoiceview());
			if (invoiceForm.getFromDay() == null || invoiceForm.getFromMonth() == null || invoiceForm.getFromYear() == null) {
				Date today = new Date();
				invoiceForm.setFromDay("01");
				invoiceForm.setFromMonth(DateUtils.getMonthShortString(today));
				invoiceForm.setFromYear(DateUtils.getYearString(today));
				invoiceForm.setUntilDay(new Integer(DateUtils.getLastDayOfMonth(DateUtils.getYearString(today), DateUtils.getMonthString(today))).toString());
				invoiceForm.setUntilMonth(DateUtils.getMonthShortString(today));
				invoiceForm.setUntilYear(DateUtils.getYearString(today));
				MessageResources messageResources = getResources(request);
				invoiceForm.setTitleactualhourstext(messageResources.getMessage("main.invoice.title.actualhours.text"));
				invoiceForm.setTitlecustomersigntext(messageResources.getMessage("main.invoice.title.customersign.text"));
				invoiceForm.setTitledatetext(messageResources.getMessage("main.invoice.title.date.text"));
				invoiceForm.setTitledescriptiontext(messageResources.getMessage("main.invoice.title.description.text"));
				invoiceForm.setTitleemployeesigntext(messageResources.getMessage("main.invoice.title.employeesign.text"));
				invoiceForm.setTitlesubordertext(messageResources.getMessage("main.invoice.title.suborder.text"));
				invoiceForm.setTitletargethourstext(messageResources.getMessage("main.invoice.title.targethours.text"));
				invoiceForm.setTitleinvoiceattachment(messageResources.getMessage("main.invoice.addresshead.text"));
			}
			request.getSession().setAttribute("currentDay", invoiceForm.getFromDay());
			request.getSession().setAttribute("currentMonth", invoiceForm.getFromMonth());
			request.getSession().setAttribute("currentYear", invoiceForm.getFromYear());
			request.getSession().setAttribute("lastDay", invoiceForm.getUntilDay());
			request.getSession().setAttribute("lastMonth", invoiceForm.getUntilMonth());
			request.getSession().setAttribute("lastYear", invoiceForm.getUntilYear());
		}
		return mapping.findForward("success");
	}

	private String fillViewHelper(List<Suborder> suborderList, List<InvoiceSuborderViewHelper> invoiceSuborderViewHelperList, java.sql.Date dateFirst, java.sql.Date dateLast, ShowInvoiceForm invoiceForm) {
		InvoiceSuborderViewHelper invoiceSuborderViewHelper;
		List<Timereport> timereportList;
		InvoiceTimereportViewHelper invoiceTimereportViewHelper;
		List<String> suborderIdList = new ArrayList<String>(suborderList.size());
		List<String> timereportIdList = new ArrayList<String>();
		for (Suborder suborder : suborderList) {
			List<InvoiceTimereportViewHelper> invoiceTimereportViewHelperList = new LinkedList<InvoiceTimereportViewHelper>();
			timereportList = timereportDAO.getTimereportsByDatesAndSuborderIdOrderedByDateAndEmployeeSign(dateFirst, dateLast, suborder.getId());
			for (Timereport timereport : timereportList) {
				invoiceTimereportViewHelper = new InvoiceTimereportViewHelper(timereport);
				invoiceTimereportViewHelperList.add(invoiceTimereportViewHelper);
				timereportIdList.add(String.valueOf(invoiceTimereportViewHelper.getId()));
			}
			invoiceSuborderViewHelper = new InvoiceSuborderViewHelper(suborder, timereportDAO, dateFirst, dateLast, invoiceForm.isInvoicebox());
			invoiceSuborderViewHelper.setInvoiceTimereportViewHelperList(invoiceTimereportViewHelperList);
			Pattern p = Pattern.compile("\\.");
			Matcher m = p.matcher(suborder.getSign());
			int counter = 0;
			while(m.find()){
				counter++;
			}
			invoiceSuborderViewHelper.setLayer(counter);
			invoiceSuborderViewHelperList.add(invoiceSuborderViewHelper);
			suborderIdList.add(String.valueOf(invoiceSuborderViewHelper.getId()));
		}
		invoiceForm.setSuborderIdArray(suborderIdList.toArray(new String[suborderIdList.size()]));
		invoiceForm.setTimereportIdArray(timereportIdList.toArray(new String[timereportIdList.size()]));
		int actualhours = 0;
		int actualminutes = 0;
		for (InvoiceSuborderViewHelper invoiceSuborderViewHelperSum : invoiceSuborderViewHelperList) {
			StringTokenizer stringTokenizer = new StringTokenizer(invoiceSuborderViewHelperSum.getActualhours(), ":");
			String hoursToken = stringTokenizer.nextToken();
			String minutesToken = stringTokenizer.nextToken();
			actualminutes += Integer.parseInt(minutesToken);
			int tempHours = actualminutes / 60;
			actualminutes = actualminutes % 60;
			actualhours += Integer.parseInt(hoursToken) + tempHours;
		}
		String actualMinutesString = "";
		if (actualminutes < 10) {
			actualMinutesString += "0";
		}
		actualMinutesString += actualminutes;
		String actualHoursSum = actualhours + ":" + actualMinutesString;
		return actualHoursSum;
	}
	
	private String calculateNextMonth(String month) {
		String nextMonth = null;
		for (int i = 0; i < GlobalConstants.MONTH_SHORTFORMS.length; i++) {
			String monthShortform = GlobalConstants.MONTH_SHORTFORMS[i];
			if (monthShortform.equals(month)) {
				try {
					nextMonth = GlobalConstants.MONTH_SHORTFORMS[i + 1];
				} catch (ArrayIndexOutOfBoundsException e) {
					nextMonth = GlobalConstants.MONTH_SHORTFORMS[0];
				}
				break;
			}
		}
		assert nextMonth != null;
		return nextMonth;
	}
	
	private String calculateNextYear(String year) {
		String nextYear = null;
		List<OptionItem> years = DateUtils.getYearsToDisplay();
		for (Iterator<OptionItem> iterator = years.iterator(); iterator.hasNext();) {
			OptionItem yearToDisplay = iterator.next();
			if (yearToDisplay.getValue().equals(year) && iterator.hasNext()) {
				nextYear = iterator.next().getValue();
				break;
			}
		}
		return nextYear;
	}
	
}
