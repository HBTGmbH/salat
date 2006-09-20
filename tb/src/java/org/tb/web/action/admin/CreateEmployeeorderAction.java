package org.tb.web.action.admin;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeOrderForm;

/**
 * action class for creating a new employee order
 * 
 * @author oda
 *
 */
public class CreateEmployeeorderAction extends LoginRequiredAction {
	
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
		
		AddEmployeeOrderForm employeeOrderForm = (AddEmployeeOrderForm) form;
		
		// get lists of existing employees and suborders
		List<Employee> employees = employeeDAO.getEmployees();
		
		if ((employees == null) || (employees.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No employee contracts found - please call system administrator.");
			return mapping.findForward("error");
		}
	
		// set relevant attributes
		request.getSession().setAttribute("employees", employees);
		
		List<Customerorder> orders = customerorderDAO.getCustomerorders();
		request.getSession().setAttribute("orders", orders);	
		
		//List<Suborder> suborders = suborderDAO.getSuborders();
		//request.getSession().setAttribute("suborders", suborders);
		
		List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeorders();
		request.getSession().setAttribute("employeeorders", employeeorders);

		// reset/init form entries
		employeeOrderForm.reset(mapping, request);
		
		//	init form with first order and corresponding suborders
		List<Suborder> theSuborders = new ArrayList<Suborder>();
		request.getSession().setAttribute("suborders", theSuborders);
		if ((orders != null) && (orders.size() > 0)) {
			employeeOrderForm.setOrder(orders.get(0).getSign());
			employeeOrderForm.setOrderId(orders.get(0).getId());
			request.getSession().setAttribute("suborders", orders.get(0).getSuborders());
			if ((orders.get(0).getSuborders() != null) && (orders.get(0).getSuborders().size() > 0)) {
				employeeOrderForm.setSuborder(orders.get(0).getSuborders().get(0).getSign());
				employeeOrderForm.setSuborderId(orders.get(0).getSuborders().get(0).getId());
			}			
		}
		
		
		// forward to form jsp
		return mapping.findForward("success");
	}
	
}
