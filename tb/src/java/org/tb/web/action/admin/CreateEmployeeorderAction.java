package org.tb.web.action.admin;

import java.util.ArrayList;
import java.util.Iterator;
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
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.form.AddEmployeeOrderForm;

/**
 * action class for creating a new employee order
 * 
 * @author oda
 *
 */
public class CreateEmployeeorderAction extends EmployeeOrderAction {
	
	private EmployeeorderDAO employeeorderDAO;
	private EmployeeDAO employeeDAO;
	private CustomerorderDAO customerorderDAO;
	private SuborderDAO suborderDAO;
	private EmployeecontractDAO employeecontractDAO;

	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}	

	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
	
	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
//		 remove list with timereports out of range
		request.getSession().removeAttribute("timereportsOutOfRange");
		
		AddEmployeeOrderForm employeeOrderForm = (AddEmployeeOrderForm) form;
		
		// get lists of existing employee contracts and suborders
		List<Employeecontract> employeeContracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
				
		if ((employeeContracts == null) || (employeeContracts.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No employees with valid contracts found - please call system administrator.");
			return mapping.findForward("error");
		}
		
	
		// set relevant attributes
//		request.getSession().setAttribute("employees", employees);
		request.getSession().setAttribute("employeecontracts", employeeContracts);
		
		List<Customerorder> orders;
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		
		if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL) || 
			loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_GF) ||
			loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM) ) {
				orders = customerorderDAO.getCustomerorders();
		} else {
			orders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
		}
		
		Customerorder customerorder;
		List<Customerorder> orderswithsuborders= new ArrayList<Customerorder>();
		Iterator orderiterator = orders.iterator();
		while (orderiterator.hasNext()) {
			customerorder = (Customerorder) orderiterator.next();
			if (!(customerorder.getSuborders() == null || customerorder.getSuborders().isEmpty())) {
				orderswithsuborders.add(customerorder);
			}
		}
		if ((orderswithsuborders == null) || (orderswithsuborders.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No customerorders with valid suborders found - please call system administrator.");
			return mapping.findForward("error");
		}
//		request.getSession().setAttribute("orders", orders);
		request.getSession().setAttribute("orderswithsuborders", orderswithsuborders);
				
		List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeorders();
		request.getSession().setAttribute("employeeorders", employeeorders);
		
		Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
		if (employeecontract != null) {
			employeeOrderForm.setEmployeeContractId(employeecontract.getId());
		}
				
		//	init form with first order and corresponding suborders
		List<Suborder> theSuborders = new ArrayList<Suborder>();
		request.getSession().setAttribute("suborders", theSuborders);
		if ((orderswithsuborders != null) && (orderswithsuborders.size() > 0)) {
			
			Customerorder selectedCustomerorder = null;
			
			Long orderId = (Long) request.getSession().getAttribute("currentOrderId");
			Customerorder customerOrderFromFilter = customerorderDAO.getCustomerorderById(orderId);
			if (orderswithsuborders.contains(customerOrderFromFilter)) {
				selectedCustomerorder = customerOrderFromFilter;
			} else {
				selectedCustomerorder = orderswithsuborders.get(0);
			}
		
			request.getSession().setAttribute("selectedcustomerorder", selectedCustomerorder);
			
			// reset/init form entries
			employeeOrderForm.reset(mapping, request);
			employeeOrderForm.useDatesFromCustomerOrder(selectedCustomerorder);

			employeeOrderForm.setOrder(selectedCustomerorder.getSign());
			employeeOrderForm.setOrderId(selectedCustomerorder.getId());
			
			
			List<Suborder> suborders = selectedCustomerorder.getSuborders();
			// remove hidden suborders
			Iterator<Suborder> suborderIterator = suborders.iterator();
			while (suborderIterator.hasNext()) {
				Suborder suborder = suborderIterator.next();
				if (suborder.getHide() != null && suborder.getHide()) {
					suborderIterator.remove();
				}
			}
			
			request.getSession().setAttribute("suborders", suborders);
			if (suborders != null && !suborders.isEmpty()) {
				request.getSession().setAttribute("selectedsuborder", suborders.get(0));
			}
			if ((selectedCustomerorder.getSuborders() != null) && (selectedCustomerorder.getSuborders().size() > 0)) {
				employeeOrderForm.setSuborder(selectedCustomerorder.getSuborders().get(0).getSign());
				employeeOrderForm.setSuborderId(selectedCustomerorder.getSuborders().get(0).getId());
			}	
			/* suggest value */
			employeeOrderForm.setDebithours(selectedCustomerorder.getSuborders().get(0).getDebithours());

			employeeOrderForm.setDebithoursunit((byte) -1); // default: no unit set
			if (selectedCustomerorder.getSuborders().get(0).getDebithours() != null && selectedCustomerorder.getSuborders().get(0).getDebithours() > 0.0) {
				/* set unit if applicable */
				employeeOrderForm.setDebithoursunit(selectedCustomerorder.getSuborders().get(0).getDebithoursunit());
			}
			
		}
		
		setFormDates(request, employeeOrderForm);
		


		
			
		
		// make sure, no eoId still exists in session
		request.getSession().removeAttribute("eoId");
		
		// forward to form jsp
//		checkDatabaseForEmployeeOrder(request, employeeOrderForm, employeecontractDAO, employeeorderDAO);
		
		request.getSession().setAttribute("newemployeeorder", true);
		return mapping.findForward("success");
	}
	
	
}
