package org.tb.web.action.admin;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Monthlyreport;
import org.tb.bdom.Vacation;
import org.tb.helper.EmployeeHelper;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.MonthlyreportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.util.DateUtils;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeContractForm;

/**
 * action class for storing an employee contractpermanently
 * 
 * @author oda
 *
 */
public class StoreEmployeecontractAction extends LoginRequiredAction {
	
	
	private EmployeeDAO employeeDAO;
	private EmployeecontractDAO employeecontractDAO;
	private MonthlyreportDAO monthlyreportDAO;
	private VacationDAO vacationDAO;

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	public void setMonthlyreportDAO(MonthlyreportDAO monthlyreportDAO) {
		this.monthlyreportDAO = monthlyreportDAO;
	}

	public void setVacationDAO(VacationDAO vacationDAO) {
		this.vacationDAO = vacationDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
			AddEmployeeContractForm ecForm = (AddEmployeeContractForm) form;
	
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("save")) ||
					(request.getParameter("ecId") != null)) {
									
				//	'main' task - prepare everything to store the employee contract.
				// I.e., copy properties from the form into the employee contract before saving.
				long ecId = -1;
				Employeecontract ec = null;
				if (request.getSession().getAttribute("ecId") != null) {
					// edited employeecontract
					ecId = Long.parseLong(request.getSession().getAttribute("ecId").toString());
					ec = employeecontractDAO.getEmployeeContractById(ecId);
				} else {
					// new report
					ec = new Employeecontract();
				}
				
				EmployeeHelper eh = new EmployeeHelper();
				String[] firstAndLast = eh.splitEmployeename(ecForm.getEmployeename());
				Employee theEmployee = (Employee) (employeeDAO.getEmployeeByName(firstAndLast[0], firstAndLast[1]));
				ec.setEmployee(theEmployee);
				
				ActionMessages errorMessages = validateFormData(request, ecForm, theEmployee);
				if (errorMessages.size() > 0) {
					return mapping.getInputForward();
				}

				Date fromDate = Date.valueOf(ecForm.getValidFrom());
				Date untilDate = Date.valueOf(ecForm.getValidUntil());
				ec.setValidFrom(fromDate);
				ec.setValidUntil(untilDate);
				ec.setTaskDescription(ecForm.getTaskdescription());
				ec.setFreelancer(ecForm.getFreelancer());
				ec.setDailyWorkingTime(ecForm.getDailyworkingtime());
				
				// if necessary, add new monthly report for current month
				if ((ec.getMonthlyreports() == null) || (ec.getMonthlyreports().size() <= 0)) {					
					List<Monthlyreport> mrList = new ArrayList<Monthlyreport>();
					Monthlyreport mr = monthlyreportDAO.setNewReport
								(ec, DateUtils.getCurrentYear(), DateUtils.getCurrentMonth());
					mrList.add(mr);
					ec.setMonthlyreports(mrList);
				} 
				
				// if necessary, add new vacation for current year
				Vacation va = null;
				if ((ec.getVacations() == null) || (ec.getVacations().size() <= 0)) {
					List<Vacation> vaList = new ArrayList<Vacation>();
					va = vacationDAO.setNewVacation(ec, DateUtils.getCurrentYear());	
					va.setEntitlement(ecForm.getYearlyvacation());
					vaList.add(va);
					ec.setVacations(vaList);					
				} else {
					for (Iterator iter = ec.getVacations().iterator(); iter.hasNext();) {
						va = (Vacation) iter.next();
						va.setEntitlement(ecForm.getYearlyvacation());
					}
				}
				
				employeecontractDAO.save(ec);
				
				request.getSession().setAttribute("currentEmployee", ecForm.getEmployeename());
				
				List<Employee> employeeOptionList = employeeDAO.getEmployees();
				request.getSession().setAttribute("employees", employeeOptionList);
				
				request.getSession().setAttribute("employeecontracts", employeecontractDAO.getEmployeeContracts());
				request.getSession().removeAttribute("ecId");
				
				boolean addMoreContracts = Boolean.parseBoolean((String)request.getParameter("continue"));
				if (!addMoreContracts) {
					return mapping.findForward("success");
				} else {
					// reuse current input of the form and show add-page
					return mapping.findForward("reset");
				}
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("back"))) {	
				// go back
				request.getSession().removeAttribute("ecId");
				ecForm.reset(mapping, request);
				return mapping.findForward("cancel");
			} 
			if ((request.getParameter("task") != null) && 
				(request.getParameter("task").equals("reset"))) {	
				// reset form
				doResetActions(mapping, request, ecForm);
				return mapping.getInputForward();				
			}	
						
			return mapping.findForward("error");
			
	}
	
	/**
	 * resets the 'add report' form to default values
	 * 
	 * @param mapping
	 * @param request
	 * @param reportForm
	 */
	private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddEmployeeContractForm ecForm) {
		ecForm.reset(mapping, request);
	}
	
	/**
	 * validates the form data (syntax and logic)
	 * 
	 * @param request
	 * @param cuForm
	 * @return
	 */
	private ActionMessages validateFormData(HttpServletRequest request, AddEmployeeContractForm ecForm,
			Employee theEmployee) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
		
		// check date formats (must now be 'yyyy-MM-dd')
		String dateFromString = ecForm.getValidFrom().trim();
		boolean dateError = DateUtils.validateDate(dateFromString);
		if (dateError) {
			errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
		} 
		
		String dateUntilString = ecForm.getValidUntil().trim();
		dateError = DateUtils.validateDate(dateUntilString);
		if (dateError) {
			errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
		} 
		
		// for a new employeecontract, check if other contract for this employee already exists
		if (request.getSession().getAttribute("ecId") == null) {
			List<Employeecontract> allEmployeecontracts = employeecontractDAO.getEmployeeContracts();
			for (Iterator iter = allEmployeecontracts.iterator(); iter.hasNext();) {
				Employeecontract ec = (Employeecontract) iter.next();
				if (ec.getEmployee().getId() == theEmployee.getId()) {
					errors.add("employeename", new ActionMessage("form.employeecontract.error.employee.alreadyexists"));		
					break;
				}
			}
		}
		
		// check length of text fields
		if (ecForm.getTaskdescription().length() > GlobalConstants.EMPLOYEECONTRACT_TASKDESCRIPTION_MAX_LENGTH) {
			errors.add("taskdescription", new ActionMessage("form.employeecontract.error.taskdescription.toolong"));
		}
		
		// check dailyworkingtime format		
		if (!GenericValidator.isDouble(ecForm.getDailyworkingtime().toString()) ||
				(!GenericValidator.isInRange(ecForm.getDailyworkingtime(), 
						0.0, GlobalConstants.MAX_DEBITHOURS))) {
			errors.add("dailyworkingtime", new ActionMessage("form.employeecontract.error.dailyworkingtime.wrongformat"));
		}
		
		// check yearlyvacation format	
		if (!GenericValidator.isInt(ecForm.getYearlyvacation().toString()) ||
				(!GenericValidator.isInRange(ecForm.getYearlyvacation(), 
						0.0, GlobalConstants.MAX_VACATION_PER_YEAR))) {
			errors.add("yearlyvacation", new ActionMessage("form.employeecontract.error.yearlyvacation.wrongformat"));
		}
		
		saveErrors(request, errors);
		
		return errors;
	}
}
