package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customer;
import org.tb.persistence.CustomerDAO;
import org.tb.persistence.CustomerorderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowCustomerOrderForm;

/**
 * action class for showing all customer orders
 * 
 * @author oda
 *
 */
public class ShowCustomerorderAction extends LoginRequiredAction {

	
	private CustomerorderDAO customerorderDAO;
	private CustomerDAO customerDAO;
	
	public void setCustomerDAO(CustomerDAO customerDAO) {
		this.customerDAO = customerDAO;
	}

	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}


	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {

		ShowCustomerOrderForm orderForm = (ShowCustomerOrderForm) form;
		
		List<Customer> customers = customerDAO.getCustomersOrderedByShortName();
		request.getSession().setAttribute("customers", customers);
		
		String filter = null;
		Boolean show = null;
		Long customerId = null; 
		
		if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("refresh"))) {
			filter = orderForm.getFilter();

			if (filter != null && !filter.trim().equals("")) {
				filter = filter.toUpperCase();
				filter = "%" + filter + "%";
			}			
			request.getSession().setAttribute("customerorderFilter", filter);
			
			show = orderForm.getShow();
			request.getSession().setAttribute("customerorderShow", show);
			
			customerId = orderForm.getCustomerId();
			request.getSession().setAttribute("customerorderCustomerId", customerId);
		} else {
			if (request.getSession().getAttribute("customerorderFilter") != null) {
				filter = (String) request.getSession().getAttribute("customerorderFilter");
			}
			if (request.getSession().getAttribute("customerorderShow") != null) {
				show = (Boolean) request.getSession().getAttribute("customerorderShow");
			}
			if (request.getSession().getAttribute("customerorderCustomerId") != null) {
				customerId = (Long) request.getSession().getAttribute("customerorderCustomerId");
			}
		}
		
		request.getSession().setAttribute("customerorders", customerorderDAO.getCustomerordersByFilters(show, filter, customerId));			
		
		if (request.getParameter("task") != null) {
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				// back to main menu
				return mapping.findForward("backtomenu");
			} else {
				// forward to show customer orders jsp
				return mapping.findForward("success");
			}
		} else {	
						
			// forward to show customer orders jsp			
			return mapping.findForward("success");
		}
	}

}
