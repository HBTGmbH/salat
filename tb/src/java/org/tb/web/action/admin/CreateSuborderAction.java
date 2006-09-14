package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddSuborderForm;

/**
 * action class for creating a new suborder
 * 
 * @author oda
 *
 */
public class CreateSuborderAction extends LoginRequiredAction {
	
	private CustomerorderDAO customerorderDAO;
	private SuborderDAO suborderDAO;
	
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	
	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
		AddSuborderForm suborderForm = (AddSuborderForm) form;
		
		// get lists of existing customerorders and suborders
		List<Customerorder> customerorders = customerorderDAO.getCustomerorders();
		List<Suborder> suborders = suborderDAO.getSuborders();
		
		if ((customerorders == null) || (customerorders.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No customer orders found - please call system administrator.");
			return mapping.findForward("error");
		}
	
		// set relevant attributes
		request.getSession().setAttribute("customerorders", customerorders);			
		request.getSession().setAttribute("suborders", suborders);
		request.getSession().setAttribute("invoice", "J");
		
		// reset/init form entries
		suborderForm.reset(mapping, request);
		
		if (customerorders.size() > 0) {
			request.getSession().setAttribute("hourlyRate", customerorders.get(0).getHourly_rate());
			request.getSession().setAttribute("currency", customerorders.get(0).getCurrency());
			suborderForm.setHourlyRate(customerorders.get(0).getHourly_rate());
			suborderForm.setCurrency(customerorders.get(0).getCurrency());
		}
				
		// forward to form jsp
		return mapping.findForward("success");
	}
	
}
