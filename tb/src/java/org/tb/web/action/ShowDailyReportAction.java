package org.tb.web.action;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.helper.CustomerorderHelper;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.SuborderHelper;
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
import org.tb.web.form.ShowDailyReportForm;

/**
 * Action class for a timereport to be shown in the daily display
 * 
 * @author oda
 *
 */
public class ShowDailyReportAction extends DailyReportAction {

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
			} catch (Exception e) {
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
			if (refreshTimereports(mapping, request, reportForm, customerorderDAO, timereportDAO, employeecontractDAO, 
					suborderDAO, employeeorderDAO, publicholidayDAO, overtimeDAO, vacationDAO, employeeDAO) != true) {
				return mapping.findForward("error");
			} else {
								
				List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
				request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
				request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
				request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
				//refresh workingday
				Workingday workingday;
				try {
					workingday = refreshWorkingday(mapping, reportForm, request);
				} catch (Exception e) {
					return mapping.findForward("error");
				}
				request.getSession().setAttribute("quittingtime",th.calculateQuittingTime(workingday, request));
				
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
        
        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("print"))){
            return mapping.findForward("print");
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
			
			// set daily view as standard
			reportForm.setView(GlobalConstants.VIEW_DAILY);
						
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
				
				request.getSession().setAttribute("lastDay", dayString);
				request.getSession().setAttribute("lastMonth", monthString);
				request.getSession().setAttribute("lastYear", yearString);
				
				String sqlDateString = yearString + "-" + 
					DateUtils.getMonthMMStringFromShortstring(monthString) + "-" + dayString;
				java.sql.Date sqlDate = java.sql.Date.valueOf(sqlDateString);	

				String currentEmployeeName = (String) request.getSession()
						.getAttribute("currentEmployee");
				Long employeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
				List<Timereport> timereports;
				if ((employeeId != null)
						&& (employeeId == -1)) {
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
				if ((employeeId != null)
						&& (employeeId == -1)) {
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

				// vacation and overtime balance
				String year = (String) request.getSession().getAttribute("currentYear");
				refreshVacationAndOvertime(request, new Integer(year), ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, vacationDAO);

			}

		}
		return mapping.findForward("success");
	}

	
	
	/**
	 * 
	 * @param request
	 * @return
	 */
	private Employeecontract getEmployeeContractFromRequest(HttpServletRequest request) {
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee"); 	
		Employeecontract ec = null;	
		long employeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
		
		if (employeeId != 0 && employeeId != -1) {	
			ec = employeecontractDAO.getEmployeeContractByEmployeeId(employeeId);	
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
	private Workingday getWorkingdayForReportformAndEmployeeContract(ShowDailyReportForm reportForm, Employeecontract ec) throws Exception {
		String dayString = reportForm.getDay();
		String monthString = reportForm.getMonth();
		String yearString = reportForm.getYear();
				
		Date tmp = getDateFormStrings(dayString, monthString, yearString);
				
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
		
		Workingday workingday = getWorkingdayForReportformAndEmployeeContract(reportForm, employeecontract);
		
		reportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
		reportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
		reportForm.setSelectedBreakHour(workingday.getBreakhours());
		reportForm.setSelectedBreakMinute(workingday.getBreakminutes());
		return workingday;
	}
}
