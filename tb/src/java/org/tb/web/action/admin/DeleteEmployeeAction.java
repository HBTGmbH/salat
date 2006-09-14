package org.tb.web.action.admin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;
import org.tb.web.action.LoginRequiredAction;

/**
 * action class for deleting an employee
 * 
 * @author oda
 *
 */
public class DeleteEmployeeAction extends LoginRequiredAction {
	
	private EmployeeDAO employeeDAO;
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

		if ((GenericValidator.isBlankOrNull(request.getParameter("emId"))) ||
				(!GenericValidator.isLong(request.getParameter("emId")))) 
					return mapping.getInputForward();
		
		ActionMessages errors = new ActionMessages();
		long emId = Long.parseLong(request.getParameter("emId"));
		Employee em = employeeDAO.getEmployeeById(emId);
		if (em == null) {
			return mapping.getInputForward();
		}
		
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		if (em.getId() == loginEmployee.getId()) {
			errors.add(null, new ActionMessage("form.employee.error.delete.isloginemployee"));	
			saveErrors(request, errors);
			return mapping.getInputForward();
		}
			
		boolean deleted = employeeDAO.deleteEmployeeById(emId);	
		
		if (!deleted) {
			errors.add(null, new ActionMessage("form.employee.error.hasemployeecontract"));	
		}
		
		saveErrors(request, errors);
		request.getSession().setAttribute("employees", employeeDAO.getEmployees());
		
		// set current employee back to loginEmployee to make sure that current employee is not the
		// one just deleted...
		request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
		
		// back to employee display jsp
		return mapping.getInputForward();
	}
	
}
