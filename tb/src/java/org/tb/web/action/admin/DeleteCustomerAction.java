package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.bdom.Customer;
import org.tb.persistence.CustomerDAO;
import org.tb.web.action.LoginRequiredAction;

/**
 * action class for deleting a customer
 * 
 * @author oda
 *
 */
public class DeleteCustomerAction extends LoginRequiredAction {
	
	private CustomerDAO customerDAO;
	
	public void setCustomerDAO(CustomerDAO customerDAO) {
		this.customerDAO = customerDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

		if ((GenericValidator.isBlankOrNull(request.getParameter("cuId"))) ||
				(!GenericValidator.isLong(request.getParameter("cuId")))) 
					return mapping.getInputForward();
		
		ActionMessages errors = new ActionMessages();
		long cuId = Long.parseLong(request.getParameter("cuId"));
		Customer cu = customerDAO.getCustomerById(cuId);
		if (cu == null) 
			return mapping.getInputForward();
		
		
		boolean deleted = customerDAO.deleteCustomerById(cuId);	
		
		if (!deleted) {
			errors.add(null, new ActionMessage("form.customer.error.hascustomerorders"));	
		}
		
		saveErrors(request, errors);
		
		String filter = null;

		if (request.getSession().getAttribute("customerFilter") != null) {
			filter = (String) request.getSession().getAttribute("customerFilter");
		}
			
		request.getSession().setAttribute("customers", customerDAO.getCustomersByFilter(filter));

		// back to customer display jsp
		return mapping.getInputForward();
	}
	
}
