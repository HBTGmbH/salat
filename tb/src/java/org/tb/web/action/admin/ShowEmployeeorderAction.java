package org.tb.web.action.admin;

import java.util.Date;
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
import org.tb.persistence.EmployeeorderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowEmployeeOrderForm;

/**
 * action class for showing all employee orders
 * 
 * @author oda
 *
 */
public class ShowEmployeeorderAction extends LoginRequiredAction {

	private EmployeeorderDAO employeeorderDAO;
	private EmployeecontractDAO employeecontractDAO;
	private EmployeeDAO employeeDAO;
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
		
	public void setEmployeeDAO (EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) {
		
		ShowEmployeeOrderForm orderForm = (ShowEmployeeOrderForm) form;
		
		
		// get valid employeecontracts
		Date now = new Date();
		// List<Employeecontract> employeeContracts = employeecontractDAO.getEmployeeContractsValidForDate(now);
		
		List<Employee> employees = employeeDAO.getEmployeesWithContractsValidForDate(now);
		request.getSession().setAttribute("employees", employees);
		
		
		// request.getSession().setAttribute("employeecontracts", employeeContracts);
		request.getSession().setAttribute("employeeorders", employeeorderDAO.getSortedEmployeeorders());			
		
		if ((request.getParameter("task") != null) && ("updateEmployeeOrders".equals(request.getParameter("task")))) {
			long employeeId = orderForm.getEmployeeId();
			if (employeeId == -1) {
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getSortedEmployeeorders());
			} else {
				request.getSession().setAttribute("employeeorders", employeeorderDAO.getEmployeeOrdersByEmployeeId(employeeId));
			}
		}
		
		if (request.getParameter("task") != null) {
			if (request.getParameter("task").equalsIgnoreCase("back")) {
				// back to main menu
				return mapping.findForward("backtomenu");
			} else {
				// forward to show employee orders jsp
				return mapping.findForward("success");
			}
		} else {	
			// forward to show employee orders jsp
			return mapping.findForward("success");
		}
	}

}
