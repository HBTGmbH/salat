package org.tb.web.action;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
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
import org.tb.web.form.ShowDailyReportForm;

public abstract class DailyReportAction extends LoginRequiredAction {

	


	
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
			TimereportHelper th = new TimereportHelper();
			date = th.getDateFormStrings(dayString, monthString, yearString, true);
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
	public void refreshVacationAndOvertime(HttpServletRequest request, Employeecontract employeecontract, 
			EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, TimereportDAO timereportDAO, OvertimeDAO overtimeDAO) {
		TimereportHelper th = new TimereportHelper();
		int[] overtime = th.calculateOvertime(employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
		int overtimeHours = overtime[0];
		int overtimeMinutes = overtime[1];
		
		boolean overtimeIsNegative = false;
		if (overtimeMinutes < 0) {
			overtimeIsNegative = true;
			overtimeMinutes *= -1;
		}
		if (overtimeHours < 0){ 
			overtimeIsNegative = true;
			overtimeHours *= -1;
		} 
		request.getSession().setAttribute("overtimeIsNegative", overtimeIsNegative);
		
		String overtimeString;
		if(overtimeIsNegative) {
			overtimeString = "-"+overtimeHours+":";
		} else {
			overtimeString = overtimeHours+":";
		}
		
		if (overtimeMinutes < 10) {
			overtimeString += "0";
		}
		overtimeString += overtimeMinutes;
		request.getSession().setAttribute("overtime", overtimeString);
		
		//vacation
		Date now = new Date();

		java.sql.Date sqlNowDate = new java.sql.Date(now.getTime());
		
		List<Employeeorder> employeeOrders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndCustomerOrderSignAndDate(employeecontract.getId(), GlobalConstants.CUSTOMERORDER_SIGN_VACATION, sqlNowDate);
		List<Timereport> vacationReports = new ArrayList<Timereport>();
		for (Employeeorder employeeorder : employeeOrders) {
			Suborder suborder = employeeorder.getSuborder();
			vacationReports.addAll(timereportDAO.getTimereportsBySuborderId(suborder.getId()));
		}
		
		int[] vacationTime = th.calculateLaborTimeAsArray(vacationReports);
		int totalVacation = employeecontract.getVacationEntitlement();
		double dailyWorkingTime = employeecontract.getDailyWorkingTime();
		int dailyWorkingTimeMinutes = new Double(dailyWorkingTime*60).intValue();
		int vacationMinutes = vacationTime[0]*60 + vacationTime[1];
		
		if (vacationMinutes > dailyWorkingTimeMinutes*totalVacation) {
			request.getSession().setAttribute("vacationextended", true);
		} else {
			request.getSession().setAttribute("vacationextended", false);
		}
		
		int usedVacationDays = 0;
		int usedVacationHours = 0;
		int usedVacationMinutes = 0;
		
		if (dailyWorkingTime != 0) {
			usedVacationDays = vacationMinutes/dailyWorkingTimeMinutes;
			vacationMinutes -= dailyWorkingTimeMinutes * usedVacationDays;
			usedVacationHours = vacationMinutes/60;
			usedVacationMinutes = vacationMinutes%60;
		} 
		
		request.getSession().setAttribute("vacationtotal", totalVacation);
		request.getSession().setAttribute("vacationdaysused", usedVacationDays);
		request.getSession().setAttribute("vacationhoursused", usedVacationHours);
		request.getSession().setAttribute("vacationminutesused", usedVacationMinutes);

	}
	
	
	/**
	 * Refreshes the list of timereports and all session attributes, that depend on the list of timereports.
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm
	 * @param customerorderDAO
	 * @param timereportDAO
	 * @param employeecontractDAO
	 * @param suborderDAO
	 * @param employeeorderDAO
	 * @param publicholidayDAO
	 * @param overtimeDAO
	 * @param vacationDAO
	 * @param employeeDAO
	 * @return Returns true, if refreshing was succesful. 
	 */
	protected boolean refreshTimereports(ActionMapping mapping,
			HttpServletRequest request, ShowDailyReportForm reportForm, CustomerorderDAO customerorderDAO, 
			TimereportDAO timereportDAO, EmployeecontractDAO employeecontractDAO, SuborderDAO suborderDAO,
			EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, OvertimeDAO overtimeDAO, 
			VacationDAO vacationDAO, EmployeeDAO employeeDAO) {

		//selected view and selected dates
		String selectedView = reportForm.getView();
		if (selectedView == null) {
			throw new RuntimeException("view = null");
		}
		Date beginDate;
		Date endDate;
		
		try {
			TimereportHelper th = new TimereportHelper();
			
			if (selectedView.equals(GlobalConstants.VIEW_DAILY)) {
				request.getSession().setAttribute("view", GlobalConstants.VIEW_DAILY);
				
				beginDate = th.getDateFormStrings(reportForm.getDay(), reportForm.getMonth(), reportForm.getYear(), true);
				endDate = beginDate;
			} else if (selectedView.equals(GlobalConstants.VIEW_MONTHLY)) {
				request.getSession().setAttribute("view", GlobalConstants.VIEW_MONTHLY);
				beginDate = th.getDateFormStrings("1", reportForm.getMonth(), reportForm.getYear(), true);
				GregorianCalendar gc = new GregorianCalendar();
				gc.setTime(beginDate);
				int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
				String maxDayString = "";
				if (maxday < 10) {
					maxDayString+="0";
				}
				maxDayString+=maxday;
				endDate = th.getDateFormStrings(maxDayString, reportForm.getMonth(), reportForm.getYear(), true);
			} else if (selectedView.equals(GlobalConstants.VIEW_CUSTOM)) {
				request.getSession().setAttribute("view", GlobalConstants.VIEW_CUSTOM);
				beginDate = th.getDateFormStrings(reportForm.getDay(), reportForm.getMonth(), reportForm.getYear(), true);
				if (reportForm.getLastday() == null || reportForm.getLastmonth() == null || reportForm.getLastyear() == null) {
					reportForm.setLastday(reportForm.getDay());
					reportForm.setLastmonth(reportForm.getMonth());
					reportForm.setLastyear(reportForm.getYear());
				}
				endDate = th.getDateFormStrings(reportForm.getLastday(), reportForm.getLastmonth(), reportForm.getLastyear(), true);
			} else {
				throw new RuntimeException("no view type selected");
			}
			
		} catch (Exception e) {
			throw new RuntimeException("date cannot be parsed for form",e);
		}
		java.sql.Date beginSqlDate = new java.sql.Date(beginDate.getTime());
		java.sql.Date endSqlDate = new java.sql.Date(endDate.getTime());
		
//		String sqlDateString = reportForm.getYear() + "-" + 
//			DateUtils.getMonthMMStringFromShortstring(reportForm.getMonth()) + "-" + reportForm.getDay();
//		java.sql.Date sqlDate = java.sql.Date.valueOf(sqlDateString);
		
		// test, if an order is select, the selected employee is not associated with
		long employeeId = reportForm.getEmployeeId();
		if (employeeId != 0 && employeeId != -1) {
			String selectedOrder = reportForm.getOrder();
			Customerorder order = customerorderDAO
					.getCustomerorderBySign(selectedOrder);
			List<Employeeorder> employeeOrders = null;
			if (order != null) {
				employeeOrders = employeeorderDAO
						.getEmployeeordersByOrderIdAndEmployeeId(order.getId(),
								employeeId);
			}
			if (employeeOrders == null || employeeOrders.isEmpty()) {
				reportForm.setOrder(GlobalConstants.ALL_ORDERS);
			}
		}		
		
		
		if (reportForm.getEmployeeId() == -1) {
			// consider timereports for all employees
			List<Customerorder> orders = customerorderDAO.getCustomerorders();
			request.getSession().setAttribute("orders", orders);

			if ((reportForm.getOrder() == null)
					|| (reportForm.getOrder().equals(GlobalConstants.ALL_ORDERS))) {
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
			// long employeeId = reportForm.getEmployeeId();
			
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
					|| (reportForm.getOrder().equals(GlobalConstants.ALL_ORDERS))) {
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
			refreshVacationAndOvertime(request, ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
		}

		// refresh all relevant attributes
		if (reportForm.getEmployeeId() == -1) {
			request.getSession().setAttribute("currentEmployee", GlobalConstants.ALL_EMPLOYEES);
		} else {
			request.getSession().setAttribute("currentEmployee", employeeDAO.getEmployeeById(reportForm.getEmployeeId()).getName());
		}
		request.getSession().setAttribute("currentEmployeeId", reportForm.getEmployeeId());
		if ((reportForm.getOrder() == null)
				|| (reportForm.getOrder().equals("ALL ORDERS"))) {
			request.getSession().setAttribute("currentOrder", GlobalConstants.ALL_ORDERS);
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
