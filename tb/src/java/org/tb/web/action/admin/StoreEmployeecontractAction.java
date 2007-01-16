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
import org.tb.bdom.Overtime;
import org.tb.bdom.Vacation;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.OvertimeDAO;
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
	private VacationDAO vacationDAO;
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
	
	public void setVacationDAO(VacationDAO vacationDAO) {
		this.vacationDAO = vacationDAO;
	}

	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
			AddEmployeeContractForm ecForm = (AddEmployeeContractForm) form;
			
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("storeOvertime")) ||
					(request.getParameter("ecId") != null)) {
				
				// check form entries
				ActionMessages errors = getErrors(request);
				if(errors == null) errors = new ActionMessages();
				
				// new overtime
				Double overtimeDouble = 0.0;
				if (ecForm.getNewOvertime() != null) {
					String overtimeString = ecForm.getNewOvertime();
//					if (overtimeString.contains(",")) {
//						errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
//					}
					
					try {
						overtimeDouble = Double.parseDouble(overtimeString);
					
						if (!GenericValidator.isDouble(overtimeString) ||
								(!GenericValidator.isInRange(overtimeDouble, 
										GlobalConstants.MIN_OVERTIME, GlobalConstants.MAX_OVERTIME))) {
							errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
						}
						if ((overtimeDouble * 100)%5 != 0.0) {
							errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat2"));
						}
					} catch (NumberFormatException e) {
						errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
					}
				}
				
				// new comment
				if (ecForm.getNewOvertimeComment().length() > GlobalConstants.EMPLOYEECONTRACT_OVERTIME_COMMENT_MAX_LENGTH) {
					errors.add("newOvertimeComment", new ActionMessage("form.employeecontract.error.overtimecomment.toolong"));
				}
				
				saveErrors(request, errors);
				
				if (errors.size() > 0) {
					return mapping.getInputForward();
				}
				
				// get employeecontract
				long ecId = -1;
				ecId = Long.parseLong(request.getSession().getAttribute("ecId").toString());
				Employeecontract ec = employeecontractDAO.getEmployeeContractById(ecId);
				
				Overtime overtime = new Overtime();
				overtime.setComment(ecForm.getNewOvertimeComment());
				overtime.setEmployeecontract(ec);
				overtime.setTime(overtimeDouble);
				
				Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
				
				overtimeDAO.save(overtime, loginEmployee);
				
				// refresh list of overtime adjustments
				List<Overtime> overtimes = overtimeDAO.getOvertimesByEmployeeContractId(ecId);
				double totalOvertime = 0.0;
				for (Overtime ot : overtimes) {
					totalOvertime += ot.getTime();
				}
				request.getSession().setAttribute("overtimes", overtimes);
				request.getSession().setAttribute("totalovertime", totalOvertime);
				
				// reset form
				ecForm.setNewOvertime("0.0");
				ecForm.setNewOvertimeComment("");
				
				return mapping.findForward("reset");
			}
			
			
			if ((request.getParameter("task") != null) && 
					(request.getParameter("task").equals("save")) ||
					(request.getParameter("ecId") != null)) {
									
				//	'main' task - prepare everything to store the employee contract.
				// I.e., copy properties from the form into the employee contract before saving.
				long ecId = -1;
				Employeecontract ec = null;
				long employeeId = ecForm.getEmployee();
				if (request.getSession().getAttribute("ecId") != null) {
					// edited employeecontract
					ecId = Long.parseLong(request.getSession().getAttribute("ecId").toString());
					ec = employeecontractDAO.getEmployeeContractById(ecId);
					if (ec != null) {
						employeeId = ec.getEmployee().getId();
					}					
				} else {
					ec = employeecontractDAO.getEmployeeContractByEmployeeId(ecForm.getEmployee());
				}
				boolean newContract = false;
				if (ec == null) {
					// new employee contract
					ec = new Employeecontract();
					newContract = true;
				}
				
				Employee theEmployee = (Employee) (employeeDAO.getEmployeeById(employeeId));
				
				
//				EmployeeHelper eh = new EmployeeHelper();
				
				
				
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
//				if ((ec.getMonthlyreports() == null) || (ec.getMonthlyreports().size() <= 0)) {					
//					List<Monthlyreport> mrList = new ArrayList<Monthlyreport>();
//					Monthlyreport mr = monthlyreportDAO.setNewReport
//								(ec, DateUtils.getCurrentYear(), DateUtils.getCurrentMonth());
//					mrList.add(mr);
//					ec.setMonthlyreports(mrList);
//				} 
				
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
				
				Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
				
				employeecontractDAO.save(ec, loginEmployee);
				
				if (newContract) {
					Overtime overtime = new Overtime();
					overtime.setComment("initial overtime");
					overtime.setEmployeecontract(ec);
					// if no value is selected, set 0.0
					if (ecForm.getInitialOvertime() == null) {
						ecForm.setInitialOvertime("0.0");
					}
					// the ecForm entry is checked before
					overtime.setTime(new Double(ecForm.getInitialOvertime()));
					overtimeDAO.save(overtime, loginEmployee);
				}		
				
				request.getSession().setAttribute("currentEmployee", employeeDAO.getEmployeeById(ecForm.getEmployee()).getName());
				request.getSession().setAttribute("currentEmployeeId", ecForm.getEmployee());
				
				List<Employee> employeeOptionList = employeeDAO.getEmployees();
				request.getSession().setAttribute("employees", employeeOptionList);
				
				request.getSession().setAttribute("employeecontracts", employeecontractDAO.getEmployeeContracts());
				request.getSession().removeAttribute("ecId");
				
				boolean addMoreContracts = Boolean.parseBoolean((String)request.getParameter("continue"));
				if (!addMoreContracts) {
					return mapping.findForward("success");
				} else {
					// set context
					request.getSession().setAttribute("employeeContractContext", "create");
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
		Double time = ecForm.getDailyworkingtime() * 100000;
		time += 0.0000005;
		int time2 = time.intValue();
		int modulo = time2%5000;
		ecForm.setDailyworkingtime(time2/100000.0);
		
		if (modulo != 0) {
			errors.add("dailyworkingtime", new ActionMessage("form.employeecontract.error.dailyworkingtime.wrongformat2"));
		}
		
		// check initial overtime
		if (ecForm.getInitialOvertime() != null) {
			String initialOvertimeString = ecForm.getInitialOvertime();
//			if (initialOvertimeString.contains(",")) {
//				errors.add("initialOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
//			}
			try {
				Double initialOvertimeDouble = Double.parseDouble(initialOvertimeString);
			
				if (!GenericValidator.isDouble(initialOvertimeString) ||
						(!GenericValidator.isInRange(initialOvertimeDouble, 
								GlobalConstants.MIN_OVERTIME, GlobalConstants.MAX_OVERTIME))) {
					errors.add("initialOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
				}
				if ((initialOvertimeDouble * 100)%5 != 0.0) {
					errors.add("initialOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat2"));
				}
			} catch (NumberFormatException e) {
				errors.add("initialOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
			}
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
