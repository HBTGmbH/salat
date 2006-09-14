package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customer;
import org.tb.persistence.CustomerDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddCustomerForm;

/**
 * action class for creating a new customer
 * 
 * @author oda
 *
 */
public class CreateCustomerAction extends LoginRequiredAction {

	private CustomerDAO customerDAO;
	
	public void setCustomerDAO(CustomerDAO customerDAO) {
		this.customerDAO = customerDAO;
	}

	
	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
			
		AddCustomerForm customerForm = (AddCustomerForm) form;
			
		// get list of existing customers
		List<Customer> customers = customerDAO.getCustomers();
		request.getSession().setAttribute("customers", customers);
		
		// init form entries
		customerForm.setName("");
		customerForm.setAddress("");
	
		// forward to form jsp
		return mapping.findForward("success");		
	}
}
