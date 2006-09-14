package org.tb.web.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.web.form.LoginEmployeeForm;

/**
 * Action class for the login of an employee
 * 
 * @author oda
 *
 */
public class LoginEmployeeAction extends Action {
	
	private EmployeeDAO employeeDAO;
	private PublicholidayDAO publicholidayDAO;
	
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	 
	public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
		this.publicholidayDAO = publicholidayDAO;
	}

	@Override
	public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
		LoginEmployeeForm loginEmployeeForm = (LoginEmployeeForm) form;

		Employee loginEmployee = employeeDAO.getLoginEmployee(loginEmployeeForm.getLoginname(), loginEmployeeForm.getPassword());
		if(loginEmployee == null) {
			ActionMessages errors = getErrors(request);
			if(errors == null) errors = new ActionMessages();
			errors.add(null, new ActionMessage("form.login.error.unknownuser"));

			saveErrors(request, errors);
			return mapping.getInputForward();
			//return mapping.findForward("error");
		}
		request.getSession().setAttribute("loginEmployee", loginEmployee);
		String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
		request.getSession().setAttribute("loginEmployeeFullName", loginEmployeeFullName);
		request.getSession().setAttribute("report", "W");  
		
		if ((loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_BL)) || 
			(loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_PL))) {
				request.getSession().setAttribute("employeeAuthorized", "true");
		}
		
		if(employeeDAO.isAdmin(loginEmployee)) {
			request.getSession().setAttribute("admin", Boolean.TRUE);
		}
		
		// check if public holidays are available
		publicholidayDAO.checkPublicHolidaysForCurrentYear();
		
		return mapping.findForward("success");
	}

}
