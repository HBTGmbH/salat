package org.tb.web.action.admin;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customer;
import org.tb.bdom.Customerorder;
import org.tb.persistence.CustomerDAO;
import org.tb.persistence.CustomerorderDAO;
import org.tb.util.DateUtils;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddCustomerOrderForm;

/**
 * action class for editing a customer order
 * 
 * @author oda
 *
 */
public class EditCustomerorderAction extends LoginRequiredAction {
	
	private CustomerorderDAO customerorderDAO;
	private CustomerDAO customerDAO;

	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	public void setCustomerDAO(CustomerDAO customerDAO) {
		this.customerDAO = customerDAO;
	}



	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
		AddCustomerOrderForm coForm = (AddCustomerOrderForm) form;
		long coId = Long.parseLong(request.getParameter("coId"));
		Customerorder co = customerorderDAO.getCustomerorderById(coId);
		request.getSession().setAttribute("coId", co.getId());
		
		List<Customer> customers = customerDAO.getCustomers();
		List<Customerorder> customerorders = customerorderDAO.getCustomerorders();
		
		if ((customers == null) || (customers.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No customers found - please call system administrator.");
			return mapping.findForward("error");
		}
	
		request.getSession().setAttribute("customerorders", customerorders);			
		request.getSession().setAttribute("customers", customers);
		
		// fill the form with properties of c�stomer order to be edited
		setFormEntries(mapping, request, coForm, co);
		
		// forward to customer order add/edit form
		return mapping.findForward("success");	
	}
	
	/**
	 * fills customer order form with properties of given c�stomer
	 * 
	 * @param mapping
	 * @param request
	 * @param coForm
	 * @param co - the customer order
	 */
	private void setFormEntries(ActionMapping mapping, HttpServletRequest request, 
									AddCustomerOrderForm coForm, Customerorder co) {
		
		coForm.setCurrency(co.getCurrency());
		coForm.setCustomerId(co.getCustomer().getId());
		coForm.setHourlyRate(co.getHourly_rate());
		coForm.setOrderCustomer(co.getOrder_customer());
		coForm.setResponsibleCustomer(co.getResponsible_customer());
		coForm.setResponsibleHbt(co.getResponsible_hbt());
		coForm.setSign(co.getSign());
		coForm.setDescription(co.getDescription());
		
		Date fromDate = new Date(co.getFromDate().getTime()); // convert to java.util.Date
		//coForm.setValidFrom(DateUtils.getDateString(fromDate));
		coForm.setValidFrom(DateUtils.getSqlDateString(fromDate));
		Date untilDate = new Date(co.getUntilDate().getTime()); // convert to java.util.Date
		//coForm.setValidUntil(DateUtils.getDateString(untilDate));
		coForm.setValidUntil(DateUtils.getSqlDateString(untilDate));
	}
	
}
