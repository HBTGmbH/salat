package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.bdom.Suborder;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;

/**
 * action class for deleting a suborder
 * 
 * @author oda
 *
 */
public class DeleteSuborderAction extends LoginRequiredAction {
	
	private SuborderDAO suborderDAO;

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}


	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

		if ((GenericValidator.isBlankOrNull(request.getParameter("soId"))) ||
				(!GenericValidator.isLong(request.getParameter("soId")))) 
					return mapping.getInputForward();
		
		ActionMessages errors = new ActionMessages();
		long soId = Long.parseLong(request.getParameter("soId"));
		Suborder so = suborderDAO.getSuborderById(soId);
		if (so == null) 
			return mapping.getInputForward();
		
		boolean deleted = suborderDAO.deleteSuborderById(soId);	
		
		if (!deleted) {
			errors.add(null, new ActionMessage("form.suborder.error.hastimereports.or.employeeorders"));	
		}
		
		saveErrors(request, errors);
		
		request.getSession().setAttribute("suborders", suborderDAO.getSuborders());
		
		// back to suborder display jsp
		return mapping.getInputForward();
	}
	
}
