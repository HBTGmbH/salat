package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeForm;

/**
 * action class for creating a new employee
 * 
 * @author oda
 *
 */
public class CreateEmployeeAction extends LoginRequiredAction {

	private EmployeeDAO employeeDAO;
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
		AddEmployeeForm employeeForm = (AddEmployeeForm) form;
			
		// get list of existing employees
		List<Employee> employees = employeeDAO.getEmployees();
		
		// reset/init form entries
		employeeForm.reset(mapping, request);
		
		// set relevant attributes
		request.getSession().setAttribute("gender", "m");
		request.getSession().setAttribute("employeeStatus", "Employee");
		request.getSession().setAttribute("employees", employees);
		
		// forward to form jsp
		return mapping.findForward("success");		
	}
}
