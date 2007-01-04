package org.tb.web.action.admin;

import java.text.SimpleDateFormat;

import javax.servlet.http.HttpServletRequest;

import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeOrderForm;
import org.tb.web.form.ShowEmployeeOrderForm;

public abstract class EmployeeOrderAction extends LoginRequiredAction {
	
		
	
	/**
	 * Checks, if the employeeorder exists in the database. If it exists, the form is filled with the data and the session attribute "employeeorderalreadyexists" is set to true.
	 * @param request
	 * @param eoForm
	 */
	protected void checkDatabaseForEmployeeOrder(HttpServletRequest request, AddEmployeeOrderForm eoForm, EmployeecontractDAO employeecontractDAO, EmployeeorderDAO employeeorderDAO) {
		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeId(eoForm.getEmployeeId());
		long employeecontractId = employeecontract.getId();
		long suborderId = eoForm.getSuborderId();
		
		Employeeorder employeeorder = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderId(employeecontractId, suborderId);
		if (employeeorder != null) {
			request.getSession().setAttribute("employeeorderalreadyexists", true);
			//fill form with data from existing employeeorder
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
			eoForm.setValidFrom(simpleDateFormat.format(employeeorder.getFromDate()));
			eoForm.setValidUntil(simpleDateFormat.format(employeeorder.getUntilDate()));
			eoForm.setStandingorder(employeeorder.getStandingorder());
			eoForm.setDebithours(employeeorder.getDebithours());
			eoForm.setStatus(employeeorder.getStatus());
			eoForm.setStatusreport(employeeorder.getStatusreport());
		} else {
			request.getSession().setAttribute("employeeorderalreadyexists", false);
		}
	}
	
	
	/**
	 * Refreshes the list of employee orders and stores it in the session.
	 * 
	 * @param request
	 * @param orderForm
	 * @param employeeorderDAO
	 */
	protected void refreshEmployeeOrders(HttpServletRequest request, ShowEmployeeOrderForm orderForm, EmployeeorderDAO employeeorderDAO) {
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		
		Long employeeId;		
		Long orderId;
		if (orderForm != null) {
			employeeId = orderForm.getEmployeeId();
			orderId = orderForm.getOrderId();
		} else {
			employeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
			orderId = (Long) request.getSession().getAttribute("currentOrderId");
		}
		
		if (employeeId == null || employeeId == 0) {
			employeeId = loginEmployee.getId();
		}
		if (orderForm != null) {
			orderForm.setEmployeeId(employeeId);
		}
		
		if (orderId == null || orderId == 0) {
			orderId = -1l;
		}
		
		request.getSession().setAttribute("currentEmployeeId", employeeId);
		request.getSession().setAttribute("currentOrderId", orderId);
				
		if (employeeId == -1) {
			if (orderId == -1) {
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getSortedEmployeeorders());
			} else {
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeordersByOrderId(orderId));
			}
		} else {
			if (orderId == -1) {
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeOrdersByEmployeeId(employeeId));
			} else {
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeordersByOrderIdAndEmployeeId(orderId, employeeId));
			}
		}
	}

}
