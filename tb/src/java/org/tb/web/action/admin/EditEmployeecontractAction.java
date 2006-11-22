package org.tb.web.action.admin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Overtime;
import org.tb.bdom.Vacation;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.util.DateUtils;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeContractForm;

/**
 * action class for editing an employee contract
 * 
 * @author oda
 *
 */
public class EditEmployeecontractAction extends LoginRequiredAction {
	
	private EmployeecontractDAO employeecontractDAO;
	private EmployeeDAO employeeDAO;
	private OvertimeDAO overtimeDAO;
	
	public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
		this.overtimeDAO = overtimeDAO;
	}
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}


	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		
		AddEmployeeContractForm ecForm = (AddEmployeeContractForm) form;
		long ecId = Long.parseLong(request.getParameter("ecId"));
		Employeecontract ec = employeecontractDAO.getEmployeeContractById(ecId);
		request.getSession().setAttribute("ecId", ec.getId());
		
		// fill the form with properties of employee contract to be edited
		setFormEntries(mapping, request, ecForm, ec);
		
		// set context
		request.getSession().setAttribute("employeeContractContext", "edit");
		
		// get overtime-entries
		List<Overtime> overtimes = overtimeDAO.getOvertimesByEmployeeContractId(ecId);
		double totalOvertime = 0.0;
		for (Overtime overtime : overtimes) {
			totalOvertime += overtime.getTime();
		}
		request.getSession().setAttribute("overtimes", overtimes);
		request.getSession().setAttribute("totalovertime", totalOvertime);
		
		// set day string for overime
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date now = new Date();
		request.getSession().setAttribute("dateString", simpleDateFormat.format(now));
		
		// forward to employee contract add/edit form
		return mapping.findForward("success");	
	}
	
	/**
	 * fills employee contract form with properties of given employee contract
	 * 
	 * @param mapping
	 * @param request
	 * @param ecForm
	 * @param ec - the employee contract
	 */
	private void setFormEntries(ActionMapping mapping, HttpServletRequest request, 
									AddEmployeeContractForm ecForm, Employeecontract ec) {
		
		Employee theEmployee = ec.getEmployee();
		ecForm.setEmployeename(theEmployee.getFirstname() + theEmployee.getLastname());
		request.getSession().setAttribute("currentEmployee", theEmployee.getName());
		
		List<Employee> employees = employeeDAO.getEmployees();
		request.getSession().setAttribute("employees", employees);
		
		ecForm.setEmployeeId(theEmployee.getId());
		ecForm.setTaskdescription(ec.getTaskDescription());
		ecForm.setFreelancer(ec.getFreelancer());
		ecForm.setDailyworkingtime(ec.getDailyWorkingTime());
		if (ec.getVacations().size() > 0) {
			// actually, vacation entitlement is a constant value
			// for an employee (not year-dependent), so just take the
			// first vacation entry to set the form value
			Vacation va = ec.getVacations().get(0);
			ecForm.setYearlyvacation(va.getEntitlement());
		} else {
			ecForm.setYearlyvacation(GlobalConstants.VACATION_PER_YEAR);
		}
			
		Date fromDate = new Date(ec.getValidFrom().getTime()); // convert to java.util.Date
		ecForm.setValidFrom(DateUtils.getSqlDateString(fromDate));
		Date untilDate = new Date(ec.getValidUntil().getTime()); // convert to java.util.Date
		ecForm.setValidUntil(DateUtils.getSqlDateString(untilDate));
	}
	
}
