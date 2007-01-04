package org.tb.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.bdom.comparators.SubOrderByDescriptionComparator;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.form.AddDailyReportForm;
import org.tb.web.form.ShowDailyReportForm;

/**
 * Helper class for customer order handling which does not directly deal with persistence
 * 
 * @author oda
 *
 */
public class CustomerorderHelper {

	/**
	 * refreshes customer order list after change of employee in the 'add timereport' view 
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm - AddDailyReportForm
	 * @param cd - CustomerorderDAO being used
	 * @param ed - EmployeeDAO being used
	 * @param ecd - EmployeecontractDAO being used
	 * @param sd - SuborderDAO being used
	 * 
	 * @return boolean
	 */
	public boolean refreshOrders(ActionMapping mapping, HttpServletRequest request, AddDailyReportForm reportForm,
			CustomerorderDAO cd, EmployeeDAO ed, EmployeecontractDAO ecd, SuborderDAO sd) {
		
		Employeecontract ec = ecd.getEmployeeContractById(reportForm.getEmployeecontractId());		
		if (ec == null) {
			request.setAttribute("errorMessage", 
					"No employee contract found for employee - please call system administrator.");
			return false;
		}
		
		request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
		request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
		
		ecd.getEmployeeContractById(reportForm.getEmployeecontractId());

		// get orders related to employee
		List<Customerorder> orders = cd.getCustomerordersByEmployeeContractId(ec.getId());
		request.getSession().setAttribute("orders", orders);
		
		if ((orders == null) || (orders.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No orders found for employee - please call system administrator.");
			return false;
		}
		// get suborders related to employee AND selected customer order...
		long customerorderId = orders.get(0).getId();
		
		List<Suborder> theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId);
		
//		 prepare second collection of suborders sorted by description
		List<Suborder> subordersByDescription = new ArrayList<Suborder>();
		subordersByDescription.addAll(theSuborders);
		Collections.sort(subordersByDescription, new SubOrderByDescriptionComparator());
		request.getSession().setAttribute("suborders", theSuborders);
		request.getSession().setAttribute("subordersByDescription", subordersByDescription);

		return true;		
	}
	
	/**
	 * refreshes customer order list after change of employee in the 'show timereport' views
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm - ShowDailyReportForm
	 * @param cd - CustomerorderDAO being used
	 * @param ed - EmployeeDAO being used
	 * @param ecd - EmployeecontractDAO being used
	 * @param sd - SuborderDAO being used
	 * 
	 * @return boolean
	 */
	public boolean refreshOrders(ActionMapping mapping, HttpServletRequest request, ShowDailyReportForm reportForm,
			CustomerorderDAO cd, EmployeeDAO ed, EmployeecontractDAO ecd, SuborderDAO sd) {

		Employeecontract ec = ecd.getEmployeeContractByEmployeeId(reportForm.getEmployeeId());
		
		if (ec == null) {
			request.setAttribute("errorMessage", 
					"No employee contract found for employee - please call system administrator.");
			return false;
		}
		
		request.getSession().setAttribute("currentEmployee", ed.getEmployeeById(reportForm.getEmployeeId()).getName());
		request.getSession().setAttribute("currentEmployeeID", reportForm.getEmployeeId());

		// get orders related to employee
		List<Customerorder> orders = cd.getCustomerordersByEmployeeContractId(ec.getId());
		request.getSession().setAttribute("orders", orders);
		
		if ((orders == null) || (orders.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No orders found for employee - please call system administrator.");
			return false;
		}
		// get suborders related to employee AND selected customer order...
		long customerorderId = orders.get(0).getId();
		request.getSession().setAttribute("suborders", 
					sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId));	

		return true;		
	}
	
	
}
