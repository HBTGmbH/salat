package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.bdom.Customerorder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.web.action.LoginRequiredAction;

/**
 * action class for deleting a customer order
 * 
 * @author oda
 *
 */
public class DeleteCustomerorderAction extends LoginRequiredAction {
	
	private CustomerorderDAO customerorderDAO;
	
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

		if ((GenericValidator.isBlankOrNull(request.getParameter("coId"))) ||
				(!GenericValidator.isLong(request.getParameter("coId")))) 
					return mapping.getInputForward();
		
		ActionMessages errors = new ActionMessages();
		long coId = Long.parseLong(request.getParameter("coId"));
		Customerorder co = customerorderDAO.getCustomerorderById(coId);
		if (co == null) 
			return mapping.getInputForward();
		
		boolean deleted = customerorderDAO.deleteCustomerorderById(coId);	
		
		if (!deleted) {
			errors.add(null, new ActionMessage("form.customerorder.error.hassuborders"));	
		}
		
		saveErrors(request, errors);
		
		request.getSession().setAttribute("customerorders", customerorderDAO.getCustomerorders());
		
		// back to customer order display jsp
		return mapping.getInputForward();
	}
	
}
