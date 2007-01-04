package org.tb.web.action.admin;

import java.sql.Date;
import java.text.SimpleDateFormat;
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
import org.tb.web.action.LoginRequiredAction;
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
		
		AddEmployeeOrderForm employeeOrderForm = (AddEmployeeOrderForm) form;
		
		// get lists of existing employees and suborders
		List<Employee> employees = employeeDAO.getEmployees();
		
		if ((employees == null) || (employees.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No employee contracts found - please call system administrator.");
			return mapping.findForward("error");
		}
		Employee emp;
		Iterator it = employees.iterator();
		List<Employee> employeeswithcontract = new ArrayList<Employee>();
		while (it.hasNext()) {
			emp = (Employee) it.next();
			if (employeecontractDAO.getEmployeeContractByEmployeeId(emp.getId()) != null) {
				employeeswithcontract.add(emp);
			}
		}
		if ((employeeswithcontract == null) || (employeeswithcontract.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No employees with valid contracts found - please call system administrator.");
			return mapping.findForward("error");
		}
		
	
		// set relevant attributes
//		request.getSession().setAttribute("employees", employees);
		request.getSession().setAttribute("employeeswithcontract", employeeswithcontract);
		
		List<Customerorder> orders;
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		
		if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL)) {
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
		request.getSession().setAttribute("orders", orders);
		request.getSession().setAttribute("orderswithsuborders", orderswithsuborders);
		
		Customerorder firstCustomerorder = orderswithsuborders.get(0);
		if (firstCustomerorder != null) {
			request.getSession().setAttribute("selectedcustomerorder", firstCustomerorder);
		}
		
		//List<Suborder> suborders = suborderDAO.getSuborders();
		//request.getSession().setAttribute("suborders", suborders);
		
		List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeorders();
		request.getSession().setAttribute("employeeorders", employeeorders);

		// reset/init form entries
		employeeOrderForm.reset(mapping, request);
		employeeOrderForm.useDatesFromCustomerOrder(firstCustomerorder);
				
		//	init form with first order and corresponding suborders
		List<Suborder> theSuborders = new ArrayList<Suborder>();
		request.getSession().setAttribute("suborders", theSuborders);
		if ((orders != null) && (orders.size() > 0)) {
			employeeOrderForm.setOrder(orders.get(0).getSign());
			employeeOrderForm.setOrderId(orders.get(0).getId());
			List<Suborder> suborders = orders.get(0).getSuborders();
			request.getSession().setAttribute("suborders", suborders);
			if (suborders != null && !suborders.isEmpty()) {
				request.getSession().setAttribute("selectedsuborder", suborders.get(0));
			}
			if ((orders.get(0).getSuborders() != null) && (orders.get(0).getSuborders().size() > 0)) {
				employeeOrderForm.setSuborder(orders.get(0).getSuborders().get(0).getSign());
				employeeOrderForm.setSuborderId(orders.get(0).getSuborders().get(0).getId());
			}			
		}
		
		
		// forward to form jsp
		checkDatabaseForEmployeeOrder(request, employeeOrderForm, employeecontractDAO, employeeorderDAO);
		return mapping.findForward("success");
	}
	
	
}
