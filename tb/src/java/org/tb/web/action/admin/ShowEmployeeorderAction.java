package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.persistence.CustomerorderDAO;
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
		
	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {
		
		ShowEmployeeOrderForm orderForm = (ShowEmployeeOrderForm) form;
				
		// get valid employeecontracts
		List<Employeecontract> employeeContracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
		request.getSession().setAttribute("employeecontracts", employeeContracts);
		

		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

		
		List<Customerorder> orders = customerorderDAO.getCustomerorders();
		request.getSession().setAttribute("orders", orders);
		
		if (request.getParameter("task") != null && request.getParameter("task").equalsIgnoreCase("back")) {
			// back to main menu
			return mapping.findForward("backtomenu");
		} 
		

		orders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
		boolean employeeIsResponsible = false;
		
		if (orders != null && orders.size() > 0) {
			employeeIsResponsible =  true;
		}
		request.getSession().setAttribute("employeeIsResponsible", employeeIsResponsible);
		
		refreshEmployeeOrders(request, orderForm, employeeorderDAO, employeecontractDAO);		
			
		return mapping.findForward("success");
			
	}
	
}
