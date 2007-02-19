package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.bdom.Employeeorder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.web.form.ShowEmployeeOrderForm;

/**
 * action class for deleting an employee order
 * 
 * @author oda
 *
 */
public class DeleteEmployeeorderAction extends EmployeeOrderAction {
	
	private EmployeeorderDAO employeeorderDAO;
	private EmployeecontractDAO employeecontractDAO;
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

		if ((GenericValidator.isBlankOrNull(request.getParameter("eoId"))) ||
				(!GenericValidator.isLong(request.getParameter("eoId")))) 
					return mapping.getInputForward();
		
		ActionMessages errors = new ActionMessages();
		long eoId = Long.parseLong(request.getParameter("eoId"));
		Employeeorder eo = employeeorderDAO.getEmployeeorderById(eoId);
		if (eo == null) 
			return mapping.getInputForward();
		
		boolean deleted = employeeorderDAO.deleteEmployeeorderById(eoId);	
		
		if (!deleted) {
			errors.add(null, new ActionMessage("form.employeeorder.error.hasstatusreports"));	
		}
		
		saveErrors(request, errors);

		refreshEmployeeOrders(request, null, employeeorderDAO, employeecontractDAO);	
				
		// back to employee order display jsp
		return mapping.getInputForward();
	}
	
}
