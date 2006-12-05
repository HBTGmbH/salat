package org.tb.web.action;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Vacation;
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
import org.tb.util.DateUtils;
import org.tb.web.form.ShowDailyReportForm;

public abstract class DailyReportAction extends LoginRequiredAction {

	
//	protected OvertimeDAO overtimeDAO;
//	protected CustomerorderDAO customerorderDAO;
//	protected TimereportDAO timereportDAO;
//	protected EmployeecontractDAO employeecontractDAO;
//	protected SuborderDAO suborderDAO;
//	protected EmployeeorderDAO employeeorderDAO;
//	protected VacationDAO vacationDAO;
//	protected PublicholidayDAO publicholidayDAO;
//	protected WorkingdayDAO workingdayDAO;
//	protected EmployeeDAO employeeDAO;
//	
//	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
//		this.employeeDAO = employeeDAO;
//	}
//	
//	public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
//		this.workingdayDAO = workingdayDAO;
//	}
//	
//	public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
//		this.publicholidayDAO = publicholidayDAO;
//	}
//	
//	public void setVacationDAO(VacationDAO vacationDAO) {
//		this.vacationDAO = vacationDAO;
//	}
//	
//	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
//		this.employeeorderDAO = employeeorderDAO;
//	}
//	
//	public void setSuborderDAO(SuborderDAO suborderDAO) {
//		this.suborderDAO = suborderDAO;
//	}
//	
//	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
//		this.employeecontractDAO = employeecontractDAO;
//	}
//	
//	public void setTimereportDAO(TimereportDAO timereportDAO) {
//		this.timereportDAO = timereportDAO;
//	}
//	
//	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
//		this.customerorderDAO = customerorderDAO;
//	}
//	
//	public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
//		this.overtimeDAO = overtimeDAO;
//	}
	
	

	/**
	 * Parses the Stings to create a {@link java.util.Date}. The day- and year-String are expected to represent integers. 
	 * The month-String must be of the sort 'Jan', 'Feb', 'Mar', ...
	 * 
	 *  
	 * 
	 * @param dayString
	 * @param monthString
	 * @param yearString
	 * @return Returns the date associated to the given Strings.
	 */
	protected Date getDateFormStrings(String dayString, String monthString, String yearString) throws Exception {
		int day = new Integer(dayString);
		int year = new Integer(yearString);
		int month = 0;
		
		if (GlobalConstants.MONTH_SHORTFORM_JANUARY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_JANUARY;			
		} else if (GlobalConstants.MONTH_SHORTFORM_FEBRURAY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_FEBRURAY;
		} else if (GlobalConstants.MONTH_SHORTFORM_MARCH.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_MARCH;
		} else if (GlobalConstants.MONTH_SHORTFORM_APRIL.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_APRIL;
		} else if (GlobalConstants.MONTH_SHORTFORM_MAY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_MAY;
		} else if (GlobalConstants.MONTH_SHORTFORM_JUNE.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_JUNE;
		} else if (GlobalConstants.MONTH_SHORTFORM_JULY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_JULY;
		} else if (GlobalConstants.MONTH_SHORTFORM_AUGUST.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_AUGUST;
		} else if (GlobalConstants.MONTH_SHORTFORM_SEPTEMBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_SEPTEMBER;
		} else if (GlobalConstants.MONTH_SHORTFORM_OCTOBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_OCTOBER;
		} else if (GlobalConstants.MONTH_SHORTFORM_NOVEMBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_NOVEMBER;
		} else if (GlobalConstants.MONTH_SHORTFORM_DECEMBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_DECEMBER;
		}
		
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date selectedDate;
		try {
			selectedDate = simpleDateFormat.parse(year+"-"+month+"-"+day);
		} catch (ParseException e) {
			//no date could be constructed - use current date instead
			selectedDate = new Date();
		}
		return selectedDate;
	}
	
	/**
	 * 
	 * @param request
	 * @return Returns the date associated the request. If parsing fails, the current date is returned.
	 */
	protected Date getSelectedDateFromRequest(HttpServletRequest request) {
		String dayString = (String) request.getSession().getAttribute("currentDay");
		String monthString = (String) request.getSession().getAttribute("currentMonth");
		String yearString = (String) request.getSession().getAttribute("currentYear");
		
		Date date;
		try {
			date = getDateFormStrings(dayString, monthString, yearString);
		} catch (Exception e) {
			// if parsing fails, return current date
			date = new Date();
		}
		
		return date;
	}

	/**
	 * Calculates the overtime and vaction and sets the attributes in the session.
	 * @param request
	 * @param selectedYear
	 * @param employeecontract
	 */
	public void refreshVacationAndOvertime(HttpServletRequest request, int selectedYear, Employeecontract employeecontract, 
			EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, TimereportDAO timereportDAO, OvertimeDAO overtimeDAO, VacationDAO vacationDAO) {
		TimereportHelper th = new TimereportHelper();
		int[] overtime = th.calculateOvertime(employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
		Vacation vacation = vacationDAO.getVacationByYearAndEmployeecontract(employeecontract.getId(), selectedYear);
		int totalVacation = vacation.getEntitlement();
		int usedVacation = vacation.getUsed();
		int overtimeHours = overtime[0];
		int overtimeMinutes = overtime[1];
		String overtimeString = overtimeHours+":";
		if (overtimeMinutes < 0) {
			request.getSession().setAttribute("overtimeIsNegative", true);
			overtimeMinutes *= -1;
		} else if (overtimeHours < 0){ 
			request.getSession().setAttribute("overtimeIsNegative", true);
		} else {
			request.getSession().setAttribute("overtimeIsNegative", false);
		}
		if (overtimeMinutes < 10) {
			overtimeString += 0;
		}
		overtimeString += overtimeMinutes;
		request.getSession().setAttribute("vacationtotal", totalVacation);
		request.getSession().setAttribute("vacationused", usedVacation);
		request.getSession().setAttribute("overtime", overtimeString);
		
	}
	
	protected boolean refreshTimereports(ActionMapping mapping,
			HttpServletRequest request, ShowDailyReportForm reportForm, CustomerorderDAO customerorderDAO, 
			TimereportDAO timereportDAO, EmployeecontractDAO employeecontractDAO, SuborderDAO suborderDAO,
			EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, OvertimeDAO overtimeDAO, 
			VacationDAO vacationDAO, EmployeeDAO employeeDAO) {

		//selected view and selected dates
		String selectedView = reportForm.getView();
		Date beginDate;
		Date endDate;
		
		try {
			if (selectedView.equals(GlobalConstants.VIEW_DAILY)) {
				request.getSession().setAttribute("view", GlobalConstants.VIEW_DAILY);
				beginDate = getDateFormStrings(reportForm.getDay(), reportForm.getMonth(), reportForm.getYear());
				endDate = beginDate;
			} else if (selectedView.equals(GlobalConstants.VIEW_MONTHLY)) {
				request.getSession().setAttribute("view", GlobalConstants.VIEW_MONTHLY);
				beginDate = getDateFormStrings("1", reportForm.getMonth(), reportForm.getYear());
				GregorianCalendar gc = new GregorianCalendar();
				gc.setTime(beginDate);
				int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
				String maxDayString = "";
				if (maxday < 10) {
					maxDayString+="0";
				}
				maxDayString+=maxday;
				endDate = getDateFormStrings(maxDayString, reportForm.getMonth(), reportForm.getYear());
			} else if (selectedView.equals(GlobalConstants.VIEW_CUSTOM)) {
				request.getSession().setAttribute("view", GlobalConstants.VIEW_CUSTOM);
				beginDate = getDateFormStrings(reportForm.getDay(), reportForm.getMonth(), reportForm.getYear());
				if (reportForm.getLastday() == null || reportForm.getLastmonth() == null || reportForm.getLastyear() == null) {
					reportForm.setLastday(reportForm.getDay());
					reportForm.setLastmonth(reportForm.getMonth());
					reportForm.setLastyear(reportForm.getYear());
				}
				endDate = getDateFormStrings(reportForm.getLastday(), reportForm.getLastmonth(), reportForm.getLastyear());
			} else {
				throw new RuntimeException("no view type selected");
			}
			
		} catch (Exception e) {
			throw new RuntimeException("date cannot be parsed for form");
		}
		java.sql.Date beginSqlDate = new java.sql.Date(beginDate.getTime());
		java.sql.Date endSqlDate = new java.sql.Date(endDate.getTime());
		
//		String sqlDateString = reportForm.getYear() + "-" + 
//			DateUtils.getMonthMMStringFromShortstring(reportForm.getMonth()) + "-" + reportForm.getDay();
//		java.sql.Date sqlDate = java.sql.Date.valueOf(sqlDateString);
		
		if (reportForm.getEmployeeId() == -1) {
			// consider timereports for all employees
			List<Customerorder> orders = customerorderDAO.getCustomerorders();
			request.getSession().setAttribute("orders", orders);

			if ((reportForm.getOrder() == null)
					|| (reportForm.getOrder().equals("ALL ORDERS"))) {
				// get the timereports for specific date, all employees, all orders
				request.getSession().setAttribute("timereports", timereportDAO
						.getTimereportsByDates(beginSqlDate, endSqlDate));
			} else {
				Customerorder co = customerorderDAO
						.getCustomerorderBySign(reportForm.getOrder());
				long orderId = co.getId();
				// get the timereports for specific date, all employees, specific order
				request.getSession().setAttribute("timereports", timereportDAO
						.getTimereportsByDatesAndCustomerOrderId(beginSqlDate, endSqlDate, orderId));
			}

		} else {
			// consider timereports for specific employee
			long employeeId = reportForm.getEmployeeId();
			
			Employeecontract ec = employeecontractDAO
					.getEmployeeContractByEmployeeId(employeeId);

			if (ec == null) {
				request
						.setAttribute("errorMessage",
								"No employee contract found for employee - please call system administrator.");
				return false;
			}

			// also refresh orders/suborders to be displayed for specific employee 
			List<Customerorder> orders = customerorderDAO
					.getCustomerordersByEmployeeContractId(ec.getId());
			request.getSession().setAttribute("orders", orders);
			if (orders.size() > 0) {
				request.getSession().setAttribute("suborders",
								suborderDAO.getSubordersByEmployeeContractId(ec.getId()));
			}

			if ((reportForm.getOrder() == null)
					|| (reportForm.getOrder().equals("ALL ORDERS"))) {
				// get the timereports for specific date, specific employee, all orders
				request.getSession().setAttribute("timereports", timereportDAO
						.getTimereportsByDatesAndEmployeeContractId(ec.getId(), beginSqlDate, endSqlDate));
			} else {
				Customerorder co = customerorderDAO
						.getCustomerorderBySign(reportForm.getOrder());
				long orderId = co.getId();
				// get the timereports for specific date, specific employee, specific order
				// fill up order-specific list with 'working' reports only...
				request.getSession()
						.setAttribute(
								"timereports",
								timereportDAO
										.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(
												ec.getId(), beginSqlDate, endSqlDate, orderId));
			}
			// refresh overtime and vacation
			String year = (String) request.getSession().getAttribute("currentYear");
			refreshVacationAndOvertime(request, new Integer(year), ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, vacationDAO);
		}

		// refresh all relevant attributes
		if (reportForm.getEmployeeId() == -1) {
			request.getSession().setAttribute("currentEmployee", "ALL EMPLOYEES");
		} else {
			request.getSession().setAttribute("currentEmployee", employeeDAO.getEmployeeById(reportForm.getEmployeeId()).getName());
		}
		request.getSession().setAttribute("currentEmployeeId", reportForm.getEmployeeId());
		if ((reportForm.getOrder() == null)
				|| (reportForm.getOrder().equals("ALL ORDERS"))) {
			request.getSession().setAttribute("currentOrder", "ALL ORDERS");
		} else {
			request.getSession().setAttribute("currentOrder",
					reportForm.getOrder());
		}
		request.getSession().setAttribute("currentDay", reportForm.getDay());
		request.getSession().setAttribute("currentMonth", reportForm.getMonth());
		request.getSession().setAttribute("currentYear", reportForm.getYear());
		
		request.getSession().setAttribute("lastDay", reportForm.getLastday());
		request.getSession().setAttribute("lastMonth", reportForm.getLastmonth());
		request.getSession().setAttribute("lastYear", reportForm.getLastyear());

		return true;

	}
	
}
