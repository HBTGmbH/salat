package org.tb.web.action.admin;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.web.form.ShowEmployeeOrderForm;

/**
 * action class for showing all employee orders
 * 
 * @author oda
 *
 */
public class ShowEmployeeorderAction extends EmployeeOrderAction {

	private EmployeeorderDAO employeeorderDAO;
	private EmployeecontractDAO employeecontractDAO;
	private EmployeeDAO employeeDAO;
	private CustomerorderDAO customerorderDAO;
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
		
	public void setEmployeeDAO (EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {
		
		ShowEmployeeOrderForm orderForm = (ShowEmployeeOrderForm) form;
		
		
		// get valid employeecontracts
		Date now = new Date();
		// List<Employeecontract> employeeContracts = employeecontractDAO.getEmployeeContractsValidForDate(now);
		
		List<Employee> employees = employeeDAO.getEmployeesWithContractsValidForDate(now);
		
		// make sure, that admin is in list
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		if (loginEmployee.getSign().equalsIgnoreCase("adm") && 
				loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
			if (!employees.contains(loginEmployee)) {
				employees.add(loginEmployee);
			}
		}
		
		request.getSession().setAttribute("employees", employees);
		
		List<Customerorder> orders = customerorderDAO.getCustomerorders();
		request.getSession().setAttribute("orders", orders);
		
		if (request.getParameter("task") != null) {
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				// back to main menu
				return mapping.findForward("backtomenu");
			} else {
				// forward to show employee orders jsp
				return mapping.findForward("success");
			}
		}
		
 		// check if loginEmployee has responsibility for some orders
//		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		orders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
		boolean employeeIsResponsible = false;
		
		if (orders != null && orders.size() > 0) {
			employeeIsResponsible =  true;
		}
		request.getSession().setAttribute("employeeIsResponsible", employeeIsResponsible);
		
			
//		if (request.getParameter("task") == null) {
			
			refreshEmployeeOrders(request, orderForm, employeeorderDAO);		
			
			return mapping.findForward("success");
			
//		} else {	
			// forward to show employee orders jsp
//			return mapping.findForward("success");
//		}
	}
	
}
