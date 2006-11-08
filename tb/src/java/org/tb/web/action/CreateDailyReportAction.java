package org.tb.web.action;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import org.tb.bdom.Suborder;
import org.tb.bdom.Workingday;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.AddDailyReportForm;

/**
 * Action class for creation of a timereport
 * 
 * @author oda
 *
 */
public class CreateDailyReportAction extends LoginRequiredAction {
	
	private EmployeeDAO employeeDAO;
	private EmployeecontractDAO employeecontractDAO;
	private CustomerorderDAO customerorderDAO;
	private SuborderDAO suborderDAO;
	private TimereportDAO timereportDAO;
	private WorkingdayDAO workingdayDAO;
	

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	
	public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
		this.workingdayDAO = workingdayDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
		AddDailyReportForm reportForm = (AddDailyReportForm) form;
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee"); 	
		Employeecontract ec = null;	
		
		EmployeeHelper eh = new EmployeeHelper();
		if (request.getSession().getAttribute("currentEmployee") != null) {
			String currentEmployeeName = (String) request.getSession().getAttribute("currentEmployee");
			if (currentEmployeeName.equalsIgnoreCase("ALL EMPLOYEES")) {
				ec = employeecontractDAO.getEmployeeContractByEmployeeId(loginEmployee.getId());
				request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
			} else {
				String[] firstAndLast = eh.splitEmployeename(currentEmployeeName);		
				ec = employeecontractDAO.getEmployeeContractByEmployeeName(firstAndLast[0], firstAndLast[1]);
				request.getSession().setAttribute("currentEmployee", currentEmployeeName);
			}
		} else {
			ec = employeecontractDAO.getEmployeeContractByEmployeeId(loginEmployee.getId());
			request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
		}
		
		if (ec == null) {
			request.setAttribute("errorMessage",
							"No employee contract found for employee - please call system administrator.");
			return mapping.findForward("error");
		}

		List<Employee> employeeOptionList = eh.getEmployeeOptions(loginEmployee, employeeDAO);
		request.getSession().setAttribute("employees", employeeOptionList);

		List<Customerorder> orders = customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId());
	
		// set attributes to be analyzed by target jsp
		request.getSession().setAttribute("orders", orders);		
		request.getSession().setAttribute("report", "W");
		request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());
		request.getSession().setAttribute("months", DateUtils.getMonthsToDisplay());
		request.getSession().setAttribute("hours", DateUtils.getHoursToDisplay());
		request.getSession().setAttribute("hoursDuration", DateUtils.getHoursDurationToDisplay());
		request.getSession().setAttribute("minutes", DateUtils.getMinutesToDisplay());
		
		TimereportHelper th = new TimereportHelper();
	
		
		// get selcted date for new report
		int day = new Integer((String) request.getSession().getAttribute("currentDay"));
		String monthString = (String) request.getSession().getAttribute("currentMonth");
		int year = new Integer((String) request.getSession().getAttribute("currentYear"));
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
		
		// search for adequate workingday and set status in session
		java.sql.Date currentDate = DateUtils.getSqlDate(selectedDate);
		Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(currentDate, ec.getId());
		
		boolean workingDayIsAvailable = false;
		if (workingday != null) {
			workingDayIsAvailable = true;
		} 
		request.getSession().setAttribute("workingDayIsAvailable", workingDayIsAvailable);
		
		// set the begin time as the end time of the latest existing timereport of current employee
		// for current day. If no other reports exist so far, set standard begin time (0800).
		int[] beginTime = th.determineBeginTimeToDisplay(ec.getId(), timereportDAO, selectedDate, workingday);
		reportForm.setSelectedHourBegin(beginTime[0]);
		reportForm.setSelectedMinuteBegin(beginTime[1]);
//		TimereportHelper.refreshHours(reportForm);
		
		
		if (workingday != null) {
			// set end time in reportform
			java.util.Date today = new Date();
			SimpleDateFormat minuteFormat = new SimpleDateFormat("mm");
			SimpleDateFormat hourFormat = new SimpleDateFormat("HH");
			int hour = new Integer(hourFormat.format(today));
			int minute = new Integer(minuteFormat.format(today));
			minute = (minute/5)*5;
			if (beginTime[0] < hour || (beginTime[0] == hour && beginTime[1] < minute)) {
				reportForm.setSelectedMinuteEnd(minute);
				reportForm.setSelectedHourEnd(hour);
			} else {
				reportForm.setSelectedMinuteEnd(beginTime[1]);
				reportForm.setSelectedHourEnd(beginTime[0]);
			} 
		} else {
			reportForm.setSelectedHourDuration(0);
			reportForm.setSelectedMinuteDuration(0);
		}
		TimereportHelper.refreshHours(reportForm);
		
		// init form with selected Date
		reportForm.setReferenceday(simpleDateFormat.format(selectedDate));
		
		
		// init form with first order and corresponding suborders
		List<Suborder> theSuborders = new ArrayList<Suborder>();
		if ((orders != null) && (!orders.isEmpty())) {
			reportForm.setOrder(orders.get(0).getSign());
			reportForm.setOrderId(orders.get(0).getId());
			theSuborders = 
				suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), orders.get(0).getId());
			if ((theSuborders == null) || (theSuborders.isEmpty())) {
				request.setAttribute("errorMessage", 
						"Orders/suborders inconsistent for employee - please call system administrator.");
				return mapping.findForward("error");
			}			
		} else {
			request.setAttribute("errorMessage", 
			"no orders found for employee - please call system administrator.");
			return mapping.findForward("error");
		}
		request.getSession().setAttribute("suborders", theSuborders);
		request.getSession().setAttribute("currentSuborderId", theSuborders.get(0).getId());
		
		return mapping.findForward("success");	
	}
	
}
