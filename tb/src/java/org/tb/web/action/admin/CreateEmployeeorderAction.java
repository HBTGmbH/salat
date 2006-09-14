package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeOrderForm;

/**
 * action class for creating a new employee order
 * 
 * @author oda
 *
 */
public class CreateEmployeeorderAction extends LoginRequiredAction {
	
	private EmployeeorderDAO employeeorderDAO;
	private EmployeeDAO employeeDAO;
	private SuborderDAO suborderDAO;
	

	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}	

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
		AddEmployeeOrderForm employeeOrderForm = (AddEmployeeOrderForm) form;
		
		// get lists of existing employees and suborders
		List<Employee> employees = employeeDAO.getEmployees();
		request.getSession().setAttribute("employees", employees);
		
		List<Suborder> suborders = suborderDAO.getSuborders();
		request.getSession().setAttribute("suborders", suborders);
		
		List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeorders();
		
		if ((employees == null) || (employees.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No employee contracts found - please call system administrator.");
			return mapping.findForward("error");
		}
	
		// set relevant attributes
		request.getSession().setAttribute("employeeorders", employeeorders);			
		request.getSession().setAttribute("employees", employees);
		
		// reset/init form entries
		employeeOrderForm.reset(mapping, request);
		
		// forward to form jsp
		return mapping.findForward("success");
	}
	
}
