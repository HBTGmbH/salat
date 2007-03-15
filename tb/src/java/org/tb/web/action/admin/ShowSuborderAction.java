package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowSuborderForm;

/**
 * action class for showing all suborders
 * 
 * @author oda
 *
 */
public class ShowSuborderAction extends LoginRequiredAction {

	
	private SuborderDAO suborderDAO;
	private CustomerorderDAO customerorderDAO;
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {
		
		
		ShowSuborderForm suborderForm = (ShowSuborderForm) form;
		
		List<Customerorder> visibleCustomerOrders = customerorderDAO.getVisibleCustomerorders();
		request.getSession().setAttribute("visibleCustomerOrders", visibleCustomerOrders);
				
		String filter = null;
		Boolean show = null;
		Long customerOrderId = null; 
		
		if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("refresh"))) {
			filter = suborderForm.getFilter();

			if (filter != null && !filter.trim().equals("")) {
				filter = filter.toUpperCase();
				filter = "%" + filter + "%";
			}			
			request.getSession().setAttribute("suborderFilter", filter);
			
			show = suborderForm.getShow();
			request.getSession().setAttribute("suborderShow", show);
			
			customerOrderId = suborderForm.getCustomerOrderId();
			request.getSession().setAttribute("suborderCustomerOrderId", customerOrderId);
		} else {
			if (request.getSession().getAttribute("suborderFilter") != null) {
				filter = (String) request.getSession().getAttribute("suborderFilter");
				suborderForm.setFilter(filter);
			}
			if (request.getSession().getAttribute("suborderShow") != null) {
				show = (Boolean) request.getSession().getAttribute("suborderShow");
				suborderForm.setShow(show);
			}
			if (request.getSession().getAttribute("suborderCustomerOrderId") != null) {
				customerOrderId = (Long) request.getSession().getAttribute("suborderCustomerOrderId");
				suborderForm.setCustomerOrderId(customerOrderId);
			}
		}
		
		request.getSession().setAttribute("suborders", suborderDAO.getSubordersByFilters(show, filter, customerOrderId));			

		
		
		
		
		// check if loginEmployee has responsibility for some orders
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		List<Customerorder> orders = customerorderDAO.getVisibleCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
		boolean employeeIsResponsible = false;
		
		if (orders != null && orders.size() > 0) {
			employeeIsResponsible =  true;
		}
		request.getSession().setAttribute("employeeIsResponsible", employeeIsResponsible);
		
		// check if there are visible customer orders
		orders = customerorderDAO.getVisibleCustomerorders();
		boolean visibleOrdersPresent = false;
		if (orders != null && !orders.isEmpty()) {
			visibleOrdersPresent = true;
		} 
		request.getSession().setAttribute("visibleOrdersPresent", visibleOrdersPresent);
		
		if (request.getParameter("task") != null) {
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				// back to main menu
				return mapping.findForward("backtomenu");
			} else {
				// forward to show suborders jsp
				return mapping.findForward("success");
			}
		} else {		
			// forward to show suborders jsp
			return mapping.findForward("success");
		}
	}

}
