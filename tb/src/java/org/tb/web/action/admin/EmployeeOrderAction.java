package org.tb.web.action.admin;

import java.text.SimpleDateFormat;

import javax.servlet.http.HttpServletRequest;

import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeOrderForm;
import org.tb.web.form.ShowEmployeeOrderForm;

public abstract class EmployeeOrderAction extends LoginRequiredAction {
	
		
	
//	/**
//	 * Checks, if the employeeorder exists in the database. If it exists, the form is filled with the data and the session attribute "employeeorderalreadyexists" is set to true.
//	 * @param request
//	 * @param eoForm
//	 */
//	protected void checkDatabaseForEmployeeOrder(HttpServletRequest request, AddEmployeeOrderForm eoForm, EmployeecontractDAO employeecontractDAO, EmployeeorderDAO employeeorderDAO) {
//		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(eoForm.getEmployeeContractId());
//		long employeecontractId = employeecontract.getId();
//		long suborderId = eoForm.getSuborderId();
//		
//		Employeeorder employeeorder = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderId(employeecontractId, suborderId);
//		if (employeeorder != null) {
//			request.getSession().setAttribute("employeeorderalreadyexists", true);
//			//fill form with data from existing employeeorder
//			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
//			eoForm.setValidFrom(simpleDateFormat.format(employeeorder.getFromDate()));
//			eoForm.setValidUntil(simpleDateFormat.format(employeeorder.getUntilDate()));
//			eoForm.setStandingorder(employeeorder.getStandingorder());
//			eoForm.setDebithours(employeeorder.getDebithours());
//			eoForm.setStatus(employeeorder.getStatus());
//			eoForm.setStatusreport(employeeorder.getStatusreport());
//		} else {
//			request.getSession().setAttribute("employeeorderalreadyexists", false);
//		}
//	}
	
	
	/**
	 * Refreshes the list of employee orders and stores it in the session.
	 * 
	 * @param request
	 * @param orderForm
	 * @param employeeorderDAO
	 */
	protected void refreshEmployeeOrders(HttpServletRequest request, ShowEmployeeOrderForm orderForm, EmployeeorderDAO employeeorderDAO, EmployeecontractDAO employeecontractDAO) {
		Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
		Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
		Long employeeContractId = 0L;		
		Long orderId = 0L;
		if (orderForm != null) {
			employeeContractId = orderForm.getEmployeeContractId();
			orderId = orderForm.getOrderId();
		}	
			
		if (employeeContractId == null || employeeContractId == 0) {
			if (currentEmployeeContract != null) {
				employeeContractId = currentEmployeeContract.getId();
			} else {
				employeeContractId = loginEmployeeContract.getId();
			} 
			
		}
		
		if (orderId == null || orderId == 0) {
			if (request.getSession().getAttribute("currentOrderId") != null) {
				orderId = (Long) request.getSession().getAttribute("currentOrderId");
			}			
		}
		if (orderId == null || orderId == 0) {
			orderId = -1l;
		}
		
		if (orderForm != null) {
			orderForm.setEmployeeContractId(employeeContractId);
			orderForm.setOrderId(orderId);
		}
		
		request.getSession().setAttribute("currentOrderId", orderId);
				
		if (employeeContractId == -1) {
			if (orderId == -1) {
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getSortedEmployeeorders());
			} else {
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeordersByOrderId(orderId));
			}
			request.getSession().setAttribute("currentEmployeeId", loginEmployeeContract.getEmployee().getId());
			request.getSession().setAttribute("currentEmployeeContract", null);
		} else {
			currentEmployeeContract = employeecontractDAO.getEmployeeContractById(employeeContractId);
			if (orderId == -1) {
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeOrdersByEmployeeContractId(employeeContractId));
			} else {
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeordersByOrderIdAndEmployeeContractId(orderId, employeeContractId));
			}
			request.getSession().setAttribute("currentEmployeeId", currentEmployeeContract.getEmployee().getId());
			request.getSession().setAttribute("currentEmployeeContract", currentEmployeeContract);
		}
	}

}
