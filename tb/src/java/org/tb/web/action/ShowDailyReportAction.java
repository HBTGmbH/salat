package org.tb.web.action;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Monthlyreport;
import org.tb.bdom.Timereport;
import org.tb.bdom.Vacation;
import org.tb.bdom.Workingday;
import org.tb.helper.CustomerorderHelper;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.SuborderHelper;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.MonthlyreportDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.ShowDailyReportForm;

/**
 * Action class for a timereport to be shown in the daily display
 * 
 * @author oda
 *
 */
public class ShowDailyReportAction extends DailyReportAction {

	private EmployeeDAO employeeDAO;

	private EmployeecontractDAO employeecontractDAO;

	private TimereportDAO timereportDAO;

	private CustomerorderDAO customerorderDAO;

	private SuborderDAO suborderDAO;

	private MonthlyreportDAO monthlyreportDAO;

	private VacationDAO vacationDAO;
	
	private WorkingdayDAO workingdayDAO;

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
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

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	public void setmonthlyreportDAO(MonthlyreportDAO monthlyreportDAO) {
		this.monthlyreportDAO = monthlyreportDAO;
	}

	public void setVacationDAO(VacationDAO vacationDAO) {
		this.vacationDAO = vacationDAO;
	}

	public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
		this.workingdayDAO = workingdayDAO;
	}
	
	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {

		
		TimereportHelper th = new TimereportHelper();
		
		// check if special tasks initiated from the daily display need to be carried out...
		ShowDailyReportForm reportForm = (ShowDailyReportForm) form;
		if ((request.getParameter("task") != null)
				&& ((request.getParameter("task").equals("saveBegin")) || (request.getParameter("task").equals("saveBreak")) )) {
			
			Employeecontract ec = getEmployeeContractFromRequest(request);	
						
			if (ec == null) {
				request.setAttribute("errorMessage",
								"No employee contract found for employee - please call system administrator.");
				return mapping.findForward("error");
			}

			Workingday workingday;
			try {
				workingday = getWorkingdayForReportformAndEmployeeContract(reportForm, ec);
			} catch (ParseException e) {
				return mapping.findForward("error");
			}
			
			if (request.getParameter("task").equals("saveBegin")) {
				workingday.setStarttimehour(reportForm.getSelectedWorkHourBegin());
				workingday.setStarttimeminute(reportForm.getSelectedWorkMinuteBegin());
			} else if (request.getParameter("task").equals("saveBreak")) {
				workingday.setBreakhours(reportForm.getSelectedBreakHour());
				workingday.setBreakminutes(reportForm.getSelectedBreakMinute());
			}
			workingdayDAO.save(workingday);
			
			request.getSession().setAttribute("quittingtime",th.calculateQuittingTime(workingday, request));
			
			return mapping.findForward("success");
		}
		
		
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("refreshTimereports"))) {
			// refresh list of timereports to be displayed
			if (refreshTimereports(mapping, request, reportForm) != true) {
				return mapping.findForward("error");
			} else {
								
				List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
				request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
				request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
				request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
				//refresh workingday
				try {
					refreshWorkingday(mapping, reportForm, request);
				} catch (Exception e) {
					return mapping.findForward("error");
				}
				
				return mapping.findForward("success");
			}
		}

		if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("refreshOrders"))) {
			// refresh orders to be displayed in the select menu
			CustomerorderHelper ch = new CustomerorderHelper();
			if (ch.refreshOrders(mapping, request, reportForm,
						customerorderDAO, employeeDAO, employeecontractDAO, suborderDAO) != true) {
				return mapping.findForward("error");
			} else {
				List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
				request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
				request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
				request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
				return mapping.findForward("success");
			}
		}
			
		if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("refreshSuborders"))) {
			// refresh suborders to be displayed in the select menu
			SuborderHelper sh = new SuborderHelper();
			if (sh.refreshDailyOverviewSuborders(mapping, request, reportForm,
					suborderDAO, employeecontractDAO) != true) {
				return mapping.findForward("error");
			} else {
				List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
				request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
				request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
				request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
				return mapping.findForward("success");
			}
		}
		
				
		if (request.getParameter("task") != null) {
			// just go back to main menu
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				return mapping.findForward("backtomenu");
			} else {
				return mapping.findForward("success");
			}
		}

		if (request.getParameter("task") == null) {
						
			// no special task - prepare everything to show reports
			Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
			EmployeeHelper eh = new EmployeeHelper();
			Employeecontract ec = eh.setCurrentEmployee(loginEmployee, request,
					employeeDAO, employeecontractDAO);
			
			if (ec == null) {
				request.setAttribute("errorMessage",
								"No employee contract found for employee - please call system administrator.");
				return mapping.findForward("error");
			}
			
			List<Employee> employees = employeeDAO.getEmployees();
			List<Employee> employeesWithContract = new LinkedList<Employee>();
			Iterator<Employee> it = employees.iterator();
			Employee emp;
			while (it.hasNext()) {
				emp = (Employee) it.next();
				if (employeecontractDAO.getEmployeeContractByEmployeeId(emp.getId()) != null) {
					employeesWithContract.add(emp);
				}
			}
			if ((employeesWithContract == null) || (employeesWithContract.size() <= 0)) {
				request.setAttribute("errorMessage", 
						"No employees with valid contracts found - please call system administrator.");
				return mapping.findForward("error");
			}
			
			request.getSession().setAttribute("employeeswithcontract", employeesWithContract);
			request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());
			request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());
			request.getSession().setAttribute("months", DateUtils.getMonthsToDisplay());
			request.getSession().setAttribute("hours", DateUtils.getHoursToDisplay());
			request.getSession().setAttribute("breakhours", DateUtils.getCompleteHoursToDisplay());
			request.getSession().setAttribute("breakminutes", DateUtils.getMinutesToDisplay());
			request.getSession().setAttribute("hoursDuration", DateUtils.getHoursDurationToDisplay());
			request.getSession().setAttribute("minutes", DateUtils.getMinutesToDisplay());
			if (reportForm.getMonth() != null) {
				// call from list select change
				request.getSession().setAttribute("currentDay", reportForm.getDay());
				request.getSession().setAttribute("currentMonth", reportForm.getMonth());
				request.getSession().setAttribute("currentYear", reportForm.getYear());
				
				String sqlDateString = reportForm.getYear() + "-" + 
				DateUtils.getMonthMMStringFromShortstring(reportForm.getMonth()) + "-" + reportForm.getDay();
				java.sql.Date sqlDate = java.sql.Date.valueOf(sqlDateString);	
				
				List<Timereport> timereports = timereportDAO
				.getTimereportsByDateAndEmployeeContractId(ec
						.getId(), sqlDate);
				
				String laborTimeString = th.calculateLaborTime(timereports);
				request.getSession().setAttribute("labortime", laborTimeString);
				request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
				request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
				
//				refresh workingday
				Workingday workingday;
				try {
					workingday = refreshWorkingday(mapping, reportForm, request);
				} catch (Exception e) {
					return mapping.findForward("error");
				}
				request.getSession().setAttribute("quittingtime",th.calculateQuittingTime(workingday, request));
				request.getSession().setAttribute("timereports", timereports);
			} else {
				java.util.Date today = new java.util.Date();
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
				String todayString = simpleDateFormat.format(today);
				try {
					today = simpleDateFormat.parse(todayString);
				} catch (ParseException e){
					throw new RuntimeException("this should not happen!");
				}
				java.sql.Date refDate = new java.sql.Date(today.getTime());
				
				Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(refDate, ec.getId());
				if (workingday == null) {
					workingday = new Workingday();
				}
				reportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
				reportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
				reportForm.setSelectedBreakHour(workingday.getBreakhours());
				reportForm.setSelectedBreakMinute(workingday.getBreakminutes());
				
				
				// call from main menu: set current month, year, timereports,
				// orders, suborders...
				Date dt = new Date();
				// get day string (e.g., '31') from java.util.Date
				String dayString = dt.toString().substring(8, 10);
				// get month string (e.g., 'Jan') from java.util.Date
				String monthString = dt.toString().substring(4, 7);
				// get year string (e.g., '2006') from java.util.Date
				int length = dt.toString().length();
				String yearString = dt.toString().substring(length-4, length);

				request.getSession().setAttribute("currentDay", dayString);
				request.getSession().setAttribute("currentMonth", monthString);
				request.getSession().setAttribute("currentYear", yearString);
				
				String sqlDateString = yearString + "-" + 
					DateUtils.getMonthMMStringFromShortstring(monthString) + "-" + dayString;
				java.sql.Date sqlDate = java.sql.Date.valueOf(sqlDateString);	

				String currentEmployeeName = (String) request.getSession()
						.getAttribute("currentEmployee");
				List<Timereport> timereports;
				if ((currentEmployeeName != null)
						&& (currentEmployeeName
								.equalsIgnoreCase("ALL EMPLOYEES"))) {
					timereports = timereportDAO.getTimereportsByDate(sqlDate);
				} else {
					timereports = timereportDAO
					.getTimereportsByDateAndEmployeeContractId(
							ec.getId(), sqlDate);
				}
				String laborTimeString = th.calculateLaborTime(timereports);
				request.getSession().setAttribute("labortime", laborTimeString);
				request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
				request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
				request.getSession().setAttribute("timereports", timereports);
				request.getSession().setAttribute("quittingtime",th.calculateQuittingTime(workingday, request));

				// orders
				List<Customerorder> orders = null;
				if ((currentEmployeeName != null)
						&& (currentEmployeeName
								.equalsIgnoreCase("ALL EMPLOYEES"))) {
					orders = customerorderDAO.getCustomerorders();
				} else {
					orders = customerorderDAO
							.getCustomerordersByEmployeeContractId(ec.getId());
				}
				request.getSession().setAttribute("orders", orders);
				request.getSession().setAttribute("currentOrder", "ALL ORDERS");
				
				if (orders.size() > 0) {
//					List<List> suborderlists = new ArrayList<List>();
//					for (Customerorder customerorder : orders) {
//						suborderlists.add(customerorder.getSuborders());
//					}
					request.getSession().setAttribute("suborders",
									suborderDAO.getSubordersByEmployeeContractId(ec.getId()));
				}
				
				// hour balance
				Monthlyreport mr = monthlyreportDAO
						.getMonthlyreportByYearAndMonthAndEmployeecontract(ec
								.getId(), Integer.parseInt(yearString),
								DateUtils
										.getMonthMMFromShortstring(monthString));
				if (mr == null) {
					// add new daily report
					mr = monthlyreportDAO.setNewReport(ec, Integer
							.parseInt(yearString), DateUtils
							.getMonthMMFromShortstring(monthString));
				}
				request.getSession().setAttribute("hourbalance",
						mr.getHourbalance());

				// vacation balance
				Vacation va = vacationDAO.getVacationByYearAndEmployeecontract(ec.getId(), Integer.parseInt(yearString));
				if (va == null) {
					// should not be the case!
					va = vacationDAO.setNewVacation(ec, Integer.parseInt(yearString));
				} 
				String vacationBalance = "" + va.getUsed().intValue() + "/" + va.getEntitlement().intValue(); 
				request.getSession().setAttribute("vacation", vacationBalance);

			}

		}
		return mapping.findForward("success");
	}

	/**
	 * refreshes timereports to be displayed after changes of relevant criteria
	 * (e.g., employee or order)
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm
	 * 
	 * @return boolean
	 */
	private boolean refreshTimereports(ActionMapping mapping,
			HttpServletRequest request, ShowDailyReportForm reportForm) {

		String sqlDateString = reportForm.getYear() + "-" + 
			DateUtils.getMonthMMStringFromShortstring(reportForm.getMonth()) + "-" + reportForm.getDay();
		java.sql.Date sqlDate = java.sql.Date.valueOf(sqlDateString);
		
		if (reportForm.getEmployeename().equalsIgnoreCase("ALL EMPLOYEES")) {
			// consider timereports for all employees
			List<Customerorder> orders = customerorderDAO.getCustomerorders();
			request.getSession().setAttribute("orders", orders);

			if ((reportForm.getOrder() == null)
					|| (reportForm.getOrder().equals("ALL ORDERS"))) {
				// get the timereports for specific date, all employees, all orders
				request.getSession().setAttribute("timereports", timereportDAO
						.getTimereportsByDate(sqlDate));
			} else {
				Customerorder co = customerorderDAO
						.getCustomerorderBySign(reportForm.getOrder());
				long orderId = co.getId();
				// get the timereports for specific date, all employees, specific order
				request.getSession().setAttribute("timereports", timereportDAO
						.getTimereportsByDateAndCustomerorder(orderId, sqlDate, "W"));
			}

		} else {
			// consider timereports for specific employee
			EmployeeHelper eh = new EmployeeHelper();
			String[] firstAndLast = eh.splitEmployeename(reportForm.getEmployeename());
			Employeecontract ec = employeecontractDAO
					.getEmployeeContractByEmployeeName(firstAndLast[0],
							firstAndLast[1]);

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
						.getTimereportsByDateAndEmployeeContractId(ec.getId(), sqlDate));
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
										.getTimereportsByDateAndEmployeeContractIdAndCustomerorderId(
												ec.getId(), orderId, sqlDate, "W"));
			}
			// refresh hour balance
			Monthlyreport mr = monthlyreportDAO
					.getMonthlyreportByYearAndMonthAndEmployeecontract(ec
							.getId(), Integer.parseInt(reportForm.getYear()),
							DateUtils.getMonthMMFromShortstring(reportForm
									.getMonth()));
			if (mr == null) {
				// add new daily report
				mr = monthlyreportDAO.setNewReport(ec, Integer
						.parseInt(reportForm.getYear()), DateUtils
						.getMonthMMFromShortstring(reportForm.getMonth()));
			}
			request.getSession().setAttribute("hourbalance", mr.getHourbalance());
			
			//	refresh vacation balance
			Vacation va = vacationDAO.getVacationByYearAndEmployeecontract(ec.getId(), Integer.parseInt(reportForm.getYear()));
			if (va == null) {
				// should not be the case!
				va = vacationDAO.setNewVacation(ec, Integer.parseInt(reportForm.getYear()));
			} 
			String vacationBalance = "" + va.getUsed().intValue() + "/" + va.getEntitlement().intValue(); 
			request.getSession().setAttribute("vacation", vacationBalance);
		}

		// refresh all relevant attributes
		request.getSession().setAttribute("currentEmployee",
				reportForm.getEmployeename());
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

		return true;

	}
	
	
	
	/**
	 * 
	 * @param request
	 * @return
	 */
	private Employeecontract getEmployeeContractFromRequest(HttpServletRequest request) {
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee"); 	
		Employeecontract ec = null;	
		
		EmployeeHelper eh = new EmployeeHelper();
		if (request.getSession().getAttribute("currentEmployee") != null) {
			String currentEmployeeName = (String) request.getSession().getAttribute("currentEmployee");
			if (currentEmployeeName.equalsIgnoreCase("ALL EMPLOYEES")) {
				ec = employeecontractDAO.getEmployeeContractByEmployeeId(loginEmployee.getId());
			} else {
				String[] firstAndLast = eh.splitEmployeename(currentEmployeeName);		
				ec = employeecontractDAO.getEmployeeContractByEmployeeName(firstAndLast[0], firstAndLast[1]);
			}
		} else {
			ec = employeecontractDAO.getEmployeeContractByEmployeeId(loginEmployee.getId());
		}
		return ec;
	}
	
	
	/**
	 * 
	 * @param reportForm
	 * @param ec
	 * @return Returns the adequate {@link Workingday} for the selected date in the reportForm and the given
	 * {@link Employeecontract}. If this workingday does not exist in the database so far, a new one is created.
	 * @throws ParseException
	 */
	private Workingday getWorkingdayForReportformAndEmployeeContract(ShowDailyReportForm reportForm, Employeecontract ec) throws ParseException {
		String dayString = reportForm.getDay();
		String monthString = reportForm.getMonth();
		String yearString = reportForm.getYear();
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
		
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
		Date tmp = simpleDateFormat.parse(yearString+month+dayString);
		
		java.sql.Date refDate = new java.sql.Date(tmp.getTime());
		
		Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(refDate, ec.getId());
		if (workingday == null) {
			workingday = new Workingday();
			workingday.setRefday(refDate);
			workingday.setEmployeecontract(ec);
		}
		return workingday;
	}
	
	
	/**
	 * Refreshes the workingday.
	 * @param mapping
	 * @param reportForm
	 * @param request
	 * @throws Exception
	 */
	private Workingday refreshWorkingday(ActionMapping mapping, ShowDailyReportForm reportForm, HttpServletRequest request) throws Exception {
		Employeecontract employeecontract = getEmployeeContractFromRequest(request);
		if (employeecontract == null) {
			request.setAttribute("errorMessage",
							"No employee contract found for employee - please call system administrator.");
			throw new Exception("No employee contract found for employee");
		}
		TimereportHelper th = new TimereportHelper();
		Workingday workingday = getWorkingdayForReportformAndEmployeeContract(reportForm, employeecontract);
		
		reportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
		reportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
		reportForm.setSelectedBreakHour(workingday.getBreakhours());
		reportForm.setSelectedBreakMinute(workingday.getBreakminutes());
		return workingday;
	}
}
