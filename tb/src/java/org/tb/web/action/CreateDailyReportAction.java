package org.tb.web.action;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
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
		
		// set the begin time as the end time of the latest existing timereport of current employee
		// for current day. If no other reports exist so far, set standard begin time (0800).
		int[] beginTime = th.determineBeginTimeToDisplay(ec.getId(), timereportDAO);
		reportForm.setSelectedHourBegin(beginTime[0]);
		reportForm.setSelectedMinuteBegin(beginTime[1]);
		TimereportHelper.refreshHours(reportForm);
		
		// init form with first order and corresponding suborders
		List<Suborder> theSuborders = new ArrayList<Suborder>();
		if ((orders != null) && (orders.size() > 0)) {
			reportForm.setOrder(orders.get(0).getSign());
			reportForm.setOrderId(orders.get(0).getId());
			theSuborders = 
				suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), orders.get(0).getId());
			if ((theSuborders == null) || (theSuborders.size() <= 0)) {
				request.setAttribute("errorMessage", 
						"Orders/suborders inconsistent for employee - please call system administrator.");
				return mapping.findForward("error");
			}			
		}
		request.getSession().setAttribute("suborders", theSuborders);
		return mapping.findForward("success");
		
	}
	
}
