package org.tb.web.action.admin;

import java.util.ArrayList;
import java.util.Date;
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
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.AddEmployeeOrderForm;

/**
 * action class for editing an employee order
 * 
 * @author oda
 *
 */
public class EditEmployeeorderAction extends EmployeeOrderAction {
	
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
		
		AddEmployeeOrderForm eoForm = (AddEmployeeOrderForm) form;
		long eoId = Long.parseLong(request.getParameter("eoId"));
		Employeeorder eo = employeeorderDAO.getEmployeeorderById(eoId);
		request.getSession().setAttribute("eoId", eo.getId());
		
		request.getSession().setAttribute("selectedcustomerorder", eo.getSuborder().getCustomerorder());
		request.getSession().setAttribute("selectedsuborder", eo.getSuborder());
		
		// fill the form with properties of employee order to be edited
		setFormEntries(mapping, request, eoForm, eo);
		
		// check if the employeeorder already exists and fill the form with the existing data
		checkDatabaseForEmployeeOrder(request, eoForm, employeecontractDAO, employeeorderDAO);
		
		// forward to employee order add/edit form
		return mapping.findForward("success");	
	}
	
	/**
	 * fills employee order form with properties of given employee order
	 * 
	 * @param mapping
	 * @param request
	 * @param eoForm
	 * @param eo - the employee order
	 */
	private void setFormEntries(ActionMapping mapping, HttpServletRequest request, 
									AddEmployeeOrderForm eoForm, Employeeorder eo) {
		
		Employeecontract ec = eo.getEmployeecontract();
		Employee theEmployee = ec.getEmployee();
//		eoForm.setEmployeeId(theEmployee.getId());
//		eoForm.setEmployeeContractId(ec.getId());
		request.getSession().setAttribute("currentEmployee", theEmployee.getName());
		request.getSession().setAttribute("currentEmployeeId", theEmployee.getId());
		request.getSession().setAttribute("currentEmployeeContract", ec);
		
//		List<Employee> employees = employeeDAO.getEmployees();
//		request.getSession().setAttribute("employees", employees);
		
		List<Employeecontract> employeeContracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
		
		if ((employeeContracts == null) || (employeeContracts.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No employees with valid contracts found - please call system administrator.");
		}
		
		// set relevant attributes
		request.getSession().setAttribute("employeecontracts", employeeContracts);
				
		List<Customerorder> orders;
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL) ||
			loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_GF) ||
			loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
			orders = customerorderDAO.getCustomerorders();
		} else {
			orders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
		}
		
		request.getSession().setAttribute("orders", orders);
		
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
		}
//		request.getSession().setAttribute("orders", orders);
		request.getSession().setAttribute("orderswithsuborders", orderswithsuborders);
		
//		Customerorder firstCustomerorder = orderswithsuborders.get(0);
//		if (firstCustomerorder != null) {
//			request.getSession().setAttribute("selectedcustomerorder", firstCustomerorder);
//		}
		
		//List<Suborder> suborders = suborderDAO.getSuborders();
		//request.getSession().setAttribute("suborders", suborders);		
		request.getSession().setAttribute("suborders", eo.getSuborder().getCustomerorder().getSuborders());
		
		eoForm.setEmployeeContractId(ec.getId());
		eoForm.setOrderId(eo.getSuborder().getCustomerorder().getId());
		eoForm.setSuborderId(eo.getSuborder().getId());
		eoForm.setDebithours(eo.getDebithours());
		eoForm.setSign(eo.getSign());
//		eoForm.setStatus(eo.getStatus());
		eoForm.setStandingorder(eo.getStandingorder());
		eoForm.setStatusreport(eo.getStatusreport());
		
		Date fromDate = new Date(eo.getFromDate().getTime()); // convert to java.util.Date
		eoForm.setValidFrom(DateUtils.getSqlDateString(fromDate));
		Date untilDate = new Date(eo.getUntilDate().getTime()); // convert to java.util.Date
		eoForm.setValidUntil(DateUtils.getSqlDateString(untilDate));
	}
	
	
	
}
