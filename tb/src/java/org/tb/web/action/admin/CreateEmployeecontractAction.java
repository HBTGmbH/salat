package org.tb.web.action.admin;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeContractForm;

/**
 * action class for creating a new employee contract
 * 
 * @author oda
 *
 */
public class CreateEmployeecontractAction extends LoginRequiredAction {
	
	private EmployeecontractDAO employeecontractDAO;
	private EmployeeDAO employeeDAO;
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}	


	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
		AddEmployeeContractForm employeeContractForm = (AddEmployeeContractForm) form;
		
		// get lists of existing employees and employee contracts
		List<Employee> employees = employeeDAO.getEmployees();
		request.getSession().setAttribute("employees", employees);
		
		List<Employeecontract> employeecontracts = employeecontractDAO.getEmployeeContracts();
		
		if ((employees == null) || (employees.size() <= 0)) {
			request.setAttribute("errorMessage", 
					"No employees found - please call system administrator.");
			return mapping.findForward("error");
		}
	
		// set relevant attributes
		request.getSession().setAttribute("employeecontracts", employeecontracts);			
		request.getSession().setAttribute("employees", employees);
		
		// reset/init form entries
		employeeContractForm.reset(mapping, request);
		
		// forward to form jsp
		return mapping.findForward("success");
	}
	
}
