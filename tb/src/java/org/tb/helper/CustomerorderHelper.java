package org.tb.helper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
		
		String dateString = reportForm.getReferenceday();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date date;
		try {
			date = simpleDateFormat.parse(dateString);
		} catch (Exception e) {
			throw new RuntimeException("error while parsing date");
		}		
//		Employeecontract ec = ecd.getEmployeeContractByIdAndDate(reportForm.getEmployeeContractId(), date);		
		Employeecontract ec = ecd.getEmployeeContractById(reportForm.getEmployeeContractId());
//		Date ec1beginn = ec.getValidFrom();
//		Date ec1end = ec.getValidUntil();
//		Date ec2beginn = ec2.getValidFrom();
//		Date ec2end = ec2.getValidUntil();
//		SimpleDateFormat debugFormat = new SimpleDateFormat("dd.MM.yyyy");
//		String ec1beginString = debugFormat.format(ec1beginn);
//		String ec1endString = debugFormat.format(ec1end);
//		String ec2beginString = debugFormat.format(ec2beginn);
//		String ec2endString = debugFormat.format(ec2end);
//		System.out.println(ec1beginString + " " + ec1endString + " " + ec2beginString + " " + ec2endString);
		
		
		if (ec == null) {
			request.setAttribute("errorMessage", 
					"No employee contract found for employee - please call system administrator.");
			return false;
		}
		
		request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
		request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
		request.getSession().setAttribute("currentEmployeeContract", ec);
		
//		ecd.getEmployeeContractById(reportForm.getEmployeeContractId());

		// get orders related to employee
//		List<Customerorder> orders = cd.getCustomerordersByEmployeeContractId(ec.getId());
		List<Customerorder> orders = cd.getCustomerordersWithValidEmployeeOrders(ec.getId(), date);

		if ((orders == null) || (orders.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No orders found for employee - please call system administrator.");
			return false;
		}
		
		request.getSession().setAttribute("orders", orders);
		
		Customerorder customerorder = cd.getCustomerorderById(reportForm.getOrderId());
		Long suborderId;
		List<Suborder> theSuborders;
		if (customerorder != null && orders.contains(customerorder)) {
			theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorder.getId(),date);
//			theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorder.getId());
			Suborder suborder = sd.getSuborderById(reportForm.getSuborderSignId());
			if (suborder != null && theSuborders.contains(suborder)) {
				suborderId = suborder.getId();
			} else {
				suborderId = theSuborders.get(0).getId();
			}
		} else {
			customerorder = orders.get(0);
			theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), customerorder.getId(),date);
//			theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorder.getId());
			suborderId = theSuborders.get(0).getId();
		}
		
		
		
		// set form entries
		reportForm.setOrderId(customerorder.getId());
		reportForm.setSuborderSignId(suborderId);
		reportForm.setSuborderDescriptionId(suborderId);
		
//		request.getSession().setAttribute("currentOrder", customerorder.getSign());
		request.getSession().setAttribute("currentSuborderId", suborderId);
		
		// get suborders related to employee AND selected customer order...
//		long customerorderId = orders.get(0).getId();
		
//		List<Suborder> theSuborders = sd.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), customerorderId);
		
//		 prepare second collection of suborders sorted by description
//		List<Suborder> subordersByDescription = new ArrayList<Suborder>();
//		subordersByDescription.addAll(theSuborders);
//		Collections.sort(subordersByDescription, new SubOrderByDescriptionComparator());
		request.getSession().setAttribute("suborders", theSuborders);
//		request.getSession().setAttribute("subordersByDescription", subordersByDescription);

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

		Employeecontract ec = ecd.getEmployeeContractById(reportForm.getEmployeeContractId());
		
		if (ec == null) {
			request.setAttribute("errorMessage", 
					"No employee contract found for employee - please call system administrator.");
			return false;
		}
		
		request.getSession().setAttribute("currentEmployee", ec.getEmployee().getName());
		request.getSession().setAttribute("currentEmployeeId", ec.getEmployee().getId());
		request.getSession().setAttribute("currentEmployeeContract", ec);
		

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
