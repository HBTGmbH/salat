package org.tb.web.action;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.tb.bdom.comparators.TimereportByEmployeeAscComparator;
import org.tb.bdom.comparators.TimereportByEmployeeDescComparator;
import org.tb.bdom.comparators.TimereportByOrderAscComparator;
import org.tb.bdom.comparators.TimereportByOrderDescComparator;
import org.tb.bdom.comparators.TimereportByRefdayAscComparator;
import org.tb.bdom.comparators.TimereportByRefdayDescComparator;
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
 * @author oda, th
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
		
		String sortModus = (String) request.getSession().getAttribute("timereportSortModus");
		if (sortModus == null || !(sortModus.equals("+") || sortModus.equals("-"))) {
			sortModus = "+";
			request.getSession().setAttribute("timereportSortModus", sortModus);
		}
		String sortColumn = (String) request.getSession().getAttribute("timereportSortColumn");
		if (sortColumn == null || sortColumn.trim().equals("")) {
			sortColumn = "employee";
			request.getSession().setAttribute("timereportSortColumn", sortColumn);
		}
		
		if ((request.getParameter("task") != null)
				&& (request.getParameter("task").equals("sort"))) {
			List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
			String column = request.getParameter("column");
			Comparator<Timereport> comparator = new TimereportByEmployeeAscComparator();
			if ("employee".equals(column)){
				if (sortColumn.equalsIgnoreCase(column) && sortModus.equals("+")) {
					comparator = new TimereportByEmployeeDescComparator();
					request.getSession().setAttribute("timereportSortModus", "-");
				} else {
					comparator = new TimereportByEmployeeAscComparator();
					request.getSession().setAttribute("timereportSortModus", "+");
					request.getSession().setAttribute("timereportSortColumn", column);
				}
			} else if ("refday".equals(column)){
				if (sortColumn.equalsIgnoreCase(column) && sortModus.equals("+")) {
					comparator = new TimereportByRefdayDescComparator();
					request.getSession().setAttribute("timereportSortModus", "-");
				} else {
					comparator = new TimereportByRefdayAscComparator();
					request.getSession().setAttribute("timereportSortModus", "+");
					request.getSession().setAttribute("timereportSortColumn", column);
				}
			} else if ("order".equals(column)){
				if (sortColumn.equalsIgnoreCase(column) && sortModus.equals("+")) {
					comparator = new TimereportByOrderDescComparator();
					request.getSession().setAttribute("timereportSortModus", "-");
				} else {
					comparator = new TimereportByOrderAscComparator();
					request.getSession().setAttribute("timereportSortModus", "+");
					request.getSession().setAttribute("timereportSortColumn", column);
				}
			}
			Collections.sort(timereports, comparator);
			request.getSession().setAttribute("timereports", timereports);
			request.getSession().setAttribute("timereportComparator", comparator);
			return mapping.findForward("success");
		}
		
		
		
		if ((request.getParameter("task") != null)
				&& ((request.getParameter("task").equals("saveBegin")) || (request.getParameter("task").equals("saveBreak")) )) {
			
			Employeecontract ec = getEmployeeContractFromRequest(request, employeecontractDAO);	
						
			if (ec == null) {
				request.setAttribute("errorMessage",
								"No employee contract found for employee - please call system administrator.");
				return mapping.findForward("error");
			}

			Workingday workingday;
			try {
				workingday = getWorkingdayForReportformAndEmployeeContract(reportForm, ec, workingdayDAO);
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
			
			request.getSession().setAttribute("reportForm", reportForm);
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
				
				if (reportForm.getEmployeeContractId() == -1) {
					request.getSession().setAttribute("currentEmployeeId", -1);
					request.getSession().setAttribute("currentEmployee", GlobalConstants.ALL_EMPLOYEES);
					request.getSession().setAttribute("currentEmployeeContract", null);
				} else {
					Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
					request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
					request.getSession().setAttribute("currentEmployee", employeecontract.getEmployee().getName());
					request.getSession().setAttribute("currentEmployeeContract", employeecontract);
				}	
			
				// refresh workingday				
				Workingday workingday;
				try {
					workingday = refreshWorkingday(mapping, reportForm, request, employeecontractDAO, workingdayDAO);
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
				request.getSession().setAttribute("reportForm", reportForm);
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
				request.getSession().setAttribute("reportForm", reportForm);
				return mapping.findForward("success");
			}
		}
        
        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("print"))){
            //conversion and localization of day values
            Map<String, String> monthMap = new HashMap<String, String>();
            monthMap.put("Jan", "main.timereport.select.month.jan.text");
            monthMap.put("Feb", "main.timereport.select.month.feb.text");
            monthMap.put("Mar", "main.timereport.select.month.mar.text");
            monthMap.put("Apr", "main.timereport.select.month.apr.text");
            monthMap.put("May", "main.timereport.select.month.mai.text");
            monthMap.put("Jun", "main.timereport.select.month.jun.text");
            monthMap.put("Jul", "main.timereport.select.month.jul.text");
            monthMap.put("Aug", "main.timereport.select.month.aug.text");
            monthMap.put("Sep", "main.timereport.select.month.sep.text");
            monthMap.put("Oct", "main.timereport.select.month.oct.text");
            monthMap.put("Nov", "main.timereport.select.month.nov.text");
            monthMap.put("Dec", "main.timereport.select.month.dec.text");
            request.getSession().setAttribute("MonthKey", monthMap.get(reportForm.getMonth()));
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
			
//			List<Employee> employees = employeeDAO.getEmployees();
//			List<Employee> employeesWithContract = employeeDAO.getEmployeesWithContracts();
			
			List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
			
// 			// make sure, that admin is in list
//			if (loginEmployee.getSign().equalsIgnoreCase("adm") && 
//					loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
//				if (!employeesWithContract.contains(loginEmployee)) {
//					employeesWithContract.add(loginEmployee);
//				}
//			}
//			
//			if ((employeesWithContract == null) || (employeesWithContract.size() <= 0)) {
//				request.setAttribute("errorMessage", 
//						"No employees with valid contracts found - please call system administrator.");
//				return mapping.findForward("error");
//			}
			
			if ((employeecontracts == null) || (employeecontracts.size() <= 0)) {
				request.setAttribute("errorMessage", 
						"No employees with valid contracts found - please call system administrator.");
				return mapping.findForward("error");
			}
			
			// set view
//			String view = (String) request.getSession().getAttribute("view");
//			if (view == null || view == "") {
				request.getSession().setAttribute("view", GlobalConstants.VIEW_DAILY);
//			}
			
			request.getSession().setAttribute("employeecontracts", employeecontracts);	
//			request.getSession().setAttribute("employeeswithcontract", employeesWithContract);
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
				
				Long currentEmployeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
				if (currentEmployeeId == null || currentEmployeeId == 0) {
					currentEmployeeId = loginEmployee.getId();
					request.getSession().setAttribute("currentEmployeeId", currentEmployeeId);
				} 
				
				List<Timereport> timereports;
				
				if (currentEmployeeId == -1) {
					// all employees
					timereports = timereportDAO.getTimereportsByDate(sqlDate);
				} else {
					timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ec
						.getId(), sqlDate);
				}
				
				String laborTimeString = th.calculateLaborTime(timereports);
				request.getSession().setAttribute("labortime", laborTimeString);
				request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
				request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
				
//				refresh workingday
				Workingday workingday;
				try {
					workingday = refreshWorkingday(mapping, reportForm, request, employeecontractDAO, workingdayDAO);
				} catch (Exception e) {
					return mapping.findForward("error");
				}
				request.getSession().setAttribute("quittingtime",th.calculateQuittingTime(workingday, request));
				
				if (request.getSession().getAttribute("timereportComparator") != null) {
					Comparator<Timereport> comparator = (Comparator<Timereport>) request
						.getSession().getAttribute("timereportComparator");
					Collections.sort(timereports, comparator);
				}
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
				
				if (request.getSession().getAttribute("timereportComparator") != null) {
					Comparator<Timereport> comparator = (Comparator<Timereport>) request
					.getSession().getAttribute("timereportComparator");
					Collections.sort(timereports, comparator);
				}
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
//				request.getSession().setAttribute("currentOrder", "ALL ORDERS");
//				request.getSession().setAttribute("currentOrderId", -1);
				
				if (orders.size() > 0) {
//					List<List> suborderlists = new ArrayList<List>();
//					for (Customerorder customerorder : orders) {
//						suborderlists.add(customerorder.getSuborders());
//					}
					request.getSession().setAttribute("suborders",
									suborderDAO.getSubordersByEmployeeContractId(ec.getId()));
				}
			}
			// vacation and overtime balance
			refreshVacationAndOvertime(request, ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);

			// set current order = all orders
			request.getSession().setAttribute("currentOrder", "ALL ORDERS");
			request.getSession().setAttribute("currentOrderId", -1l);
			
		}
		request.getSession().setAttribute("reportForm", reportForm);
		request.getSession().setAttribute("currentSuborderId",reportForm.getSuborderId());
		return mapping.findForward("success");
	}
}
