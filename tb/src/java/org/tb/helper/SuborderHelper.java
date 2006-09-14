package org.tb.helper;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employeecontract;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.form.AddDailyReportForm;
import org.tb.web.form.ShowDailyReportForm;

/**
 * Helper class for suborder handling which does not directly deal with persistence
 * 
 * @author oda
 *
 */
public class SuborderHelper {

	/**
	 * refreshes suborder list after change of customer order in the 'add timereport' view 
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm - AddDailyReportForm
	 * @param sd - SuborderDAO being used
	 * @param ecd - EmployeecontractDAO being used
	 * 
	 * @return boolean
	 */
	public boolean refreshSuborders(ActionMapping mapping, HttpServletRequest request, AddDailyReportForm reportForm,
			SuborderDAO sd, EmployeecontractDAO ecd) {

		EmployeeHelper eh = new EmployeeHelper();
		String[] firstAndLast = eh.splitEmployeename(reportForm.getEmployeename());
		Employeecontract ec = ecd.getEmployeeContractByEmployeeName(firstAndLast[0], firstAndLast[1]);
		
		if (ec == null) {
			request.setAttribute("errorMessage", 
					"No employee contract found for employee - please call system administrator.");
			return false;
		}
		
		// get suborders related to employee AND selected customer order...
		long customerorderId = reportForm.getOrderId();
		request.getSession().setAttribute("suborders", 
					sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId));
		return true;
	}
	
	/**
	 * refreshes suborder list after change of customer order in the 'show timereport' views
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm - ShowDailyReportForm
	 * @param sd - SuborderDAO being used
	 * @param ecd - EmployeecontractDAO being used
	 * 
	 * @return boolean
	 */
	public boolean refreshDailyOverviewSuborders(ActionMapping mapping, HttpServletRequest request, ShowDailyReportForm reportForm,
			SuborderDAO sd, EmployeecontractDAO ecd) {
		
		EmployeeHelper eh = new EmployeeHelper();
		String[] firstAndLast = eh.splitEmployeename(reportForm.getEmployeename());
		Employeecontract ec = ecd.getEmployeeContractByEmployeeName(firstAndLast[0], firstAndLast[1]);
		
		if (ec == null) {
			request.setAttribute("errorMessage", 
					"No employee contract found for employee - please call system administrator.");
			return false;
		}
		
		// get suborders related to employee AND selected customer order...
		long customerorderId = reportForm.getTrOrderId();
		request.getSession().setAttribute("suborders", 
					sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId));
		
		return true;
	}
}
