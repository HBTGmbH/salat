package org.tb.web.action.admin;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.util.DateUtils;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeOrderForm;

/**
 * action class for editing an employee order
 * 
 * @author oda
 *
 */
public class EditEmployeeorderAction extends LoginRequiredAction {
	
	private EmployeeorderDAO employeeorderDAO;
	private EmployeeDAO employeeDAO;
	private CustomerorderDAO customerorderDAO;
	private SuborderDAO suborderDAO;
	
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

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
		AddEmployeeOrderForm eoForm = (AddEmployeeOrderForm) form;
		long eoId = Long.parseLong(request.getParameter("eoId"));
		Employeeorder eo = employeeorderDAO.getEmployeeorderById(eoId);
		request.getSession().setAttribute("eoId", eo.getId());
		
		// fill the form with properties of employee order to be edited
		setFormEntries(mapping, request, eoForm, eo);
		
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
		eoForm.setEmployeename(theEmployee.getFirstname() + theEmployee.getLastname());
		request.getSession().setAttribute("currentEmployee", theEmployee.getName());
		
		List<Employee> employees = employeeDAO.getEmployees();
		request.getSession().setAttribute("employees", employees);
		
		List<Customerorder> orders = customerorderDAO.getCustomerorders();
		request.getSession().setAttribute("orders", orders);
		
		//List<Suborder> suborders = suborderDAO.getSuborders();
		//request.getSession().setAttribute("suborders", suborders);		
		request.getSession().setAttribute("suborders", eo.getSuborder().getCustomerorder().getSuborders());
		
		eoForm.setEmployeecontractId(ec.getId());
		eoForm.setOrderId(eo.getSuborder().getCustomerorder().getId());
		eoForm.setSuborderId(eo.getSuborder().getId());
		eoForm.setDebithours(eo.getDebithours());
		eoForm.setSign(eo.getSign());
		eoForm.setStatus(eo.getStatus());
		eoForm.setStandingorder(eo.getStandingorder());
		eoForm.setStatusreport(eo.getStatusreport());
		
		Date fromDate = new Date(eo.getFromDate().getTime()); // convert to java.util.Date
		eoForm.setValidFrom(DateUtils.getSqlDateString(fromDate));
		Date untilDate = new Date(eo.getUntilDate().getTime()); // convert to java.util.Date
		eoForm.setValidUntil(DateUtils.getSqlDateString(untilDate));
	}
	
}
