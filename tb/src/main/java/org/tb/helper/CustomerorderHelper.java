package org.tb.helper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
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
	 * @param ecd - EmployeecontractDAO being used
	 * @param sd - SuborderDAO being used
	 * 
	 * @return boolean
	 */
	public boolean refreshOrders(ActionMapping mapping, HttpServletRequest request, AddDailyReportForm reportForm,
			CustomerorderDAO cd, EmployeecontractDAO ecd, SuborderDAO sd) {
		
		String dateString = reportForm.getReferenceday();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
		Date date;
		try {
			date = simpleDateFormat.parse(dateString);
		} catch (Exception e) {
			throw new RuntimeException("error while parsing date");
		}		
	
		Employeecontract ec = ecd.getEmployeeContractById(reportForm.getEmployeeContractId());
		
		if (ec == null) {
			request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator."); //TODO: MessageResources
			return false;
		}
		
		request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
		request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
		request.getSession().setAttribute("currentEmployeeContract", ec);

		// get orders related to employee
		List<Customerorder> orders = cd.getCustomerordersWithValidEmployeeOrders(ec.getId(), date);

		if ((orders == null) || (orders.size() <= 0)) {
			request.setAttribute("errorMessage", "No orders found for employee - please call system administrator."); //TODO: MessageResources
			return false;
		}
		
		request.getSession().setAttribute("orders", orders);
		
		Customerorder customerorder = cd.getCustomerorderById(reportForm.getOrderId());
		Long suborderId;
		List<Suborder> theSuborders;
		if (customerorder != null && orders.contains(customerorder)) {
			theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorder.getId(),date);
			Suborder suborder = sd.getSuborderById(reportForm.getSuborderSignId());
			if (suborder != null && theSuborders.contains(suborder)) {
				suborderId = suborder.getId();
			} else {
				suborderId = theSuborders.get(0).getId();
			}
		} else {
			customerorder = orders.get(0);
			theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorder.getId(),date);
			suborderId = theSuborders.get(0).getId();
		}
		
		// set form entries
		reportForm.setOrderId(customerorder.getId());
		reportForm.setSuborderSignId(suborderId);
		reportForm.setSuborderDescriptionId(suborderId);
		
		request.getSession().setAttribute("currentSuborderId", suborderId);
		request.getSession().setAttribute("suborders", theSuborders);

		return true;		
	}
	
	/**
	 * refreshes customer order list after change of employee in the 'show timereport' views
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm - ShowDailyReportForm
	 * @param cd - CustomerorderDAO being used
	 * @param ecd - EmployeecontractDAO being used
	 * @param sd - SuborderDAO being used
	 * 
	 * @return boolean
	 */
	public boolean refreshOrders(ActionMapping mapping, HttpServletRequest request, ShowDailyReportForm reportForm,
			CustomerorderDAO cd, EmployeecontractDAO ecd, SuborderDAO sd) {

		Employeecontract ec = ecd.getEmployeeContractById(reportForm.getEmployeeContractId());
		
		if (ec == null) {
			request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator."); //TODO: MessageResources
			return false;
		}
		
		request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
		request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
		request.getSession().setAttribute("currentEmployeeContract", ec);
		

		// get orders related to employee
		List<Customerorder> orders = cd.getCustomerordersByEmployeeContractId(ec.getId());
		request.getSession().setAttribute("orders", orders);
		
		if ((orders == null) || (orders.size() <= 0)) {
			request.setAttribute("errorMessage", "No orders found for employee - please call system administrator."); //TODO: MessageResources
			return false;
		}
		// get suborders related to employee AND selected customer order...
		long customerorderId = orders.get(0).getId();
		request.getSession().setAttribute("suborders", sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId, reportForm.getShowOnlyValid()));	

		return true;		
	}
	
	public boolean isOrderStandard(Customerorder order) {
        
    	if (order != null &&
    			(order.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_VACATION) ||
                 order.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_EXTRA_VACATION) ||
                 order.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_ILL) ||
                 order.getSign().equalsIgnoreCase(GlobalConstants.CUSTOMERORDER_SIGN_REMAINING_VACATION))) {
    		return true;
    	}
		return false;
	}
	
}
