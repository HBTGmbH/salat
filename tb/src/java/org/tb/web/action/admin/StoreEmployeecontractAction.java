package org.tb.web.action.admin;

import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Overtime;
import org.tb.bdom.Timereport;
import org.tb.bdom.Vacation;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.util.DateUtils;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeContractForm;
import org.tb.web.form.AddEmployeeOrderForm;

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
	private TimereportDAO timereportDAO;
	private EmployeeorderDAO employeeorderDAO;
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}
	
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	
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
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
			
//			 remove list with timereports out of range
			request.getSession().removeAttribute("timereportsOutOfRange");
			
			// Task for setting the date, previous, next and to-day for both, until and from date
			if ((request.getParameter("task") != null) && (request.getParameter("task").equals("setDate"))) { 
				String which = request.getParameter("which").toLowerCase();
				Integer howMuch = Integer.parseInt(request.getParameter("howMuch"));
				
				String datum = which.equals("until") ? ecForm.getValidUntil() : ecForm.getValidFrom();
				Integer day, month, year;
				Calendar cal = Calendar.getInstance();
				
				if (howMuch != 0) {
					ActionMessages errorMessages = valiDate(request, ecForm, which);
					if (errorMessages.size() > 0) {
						return mapping.getInputForward();
					}
					
					day = Integer.parseInt(datum.substring(8));
					month = Integer.parseInt(datum.substring(5, 7));
					year = Integer.parseInt(datum.substring(0, 4));
					
					cal.set(Calendar.DATE, day);
					cal.set(Calendar.MONTH, month - 1);
					cal.set(Calendar.YEAR, year);
					
					cal.add(Calendar.DATE, howMuch);
				}
							
				datum = howMuch == 0 ? format.format(new java.util.Date()) : format.format(cal.getTime());

				request.getSession().setAttribute(which.equals("until") ? "validUntil" : "validFrom", datum);
				
				if (which.equals("until")) {
					ecForm.setValidUntil(datum); 
				} else {
					ecForm.setValidFrom(datum);
				}
				
				return mapping.findForward("reset");
			}	
			
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
						// test wether there are too many numbers after point-seperator
						if (overtimeString.contains(".") && (overtimeString.length() - overtimeString.indexOf(".") > 2)) {
							//if yes, cut off
							overtimeDouble = Double.parseDouble(overtimeString.substring(0, overtimeString.indexOf('.') + 3));
						} else {
							overtimeDouble = Double.parseDouble(overtimeString);
						}
						
						if(overtimeDouble == 0){
							errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat2"));
						}
						
						
						if (!GenericValidator.isDouble(overtimeString) ||
								(!GenericValidator.isInRange(overtimeDouble, 
										GlobalConstants.MIN_OVERTIME, GlobalConstants.MAX_OVERTIME))) {
							errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
						}

						ecForm.setNewOvertime(""+(overtimeDouble));
						
						Double time = overtimeDouble * 100000;
						

						if (time >= 0) {
							time += 0.5;
						} else {
							time -= 0.5;
						}
						int time2 = time.intValue();
						int modulo = time2%5000;
						
						if (modulo != 0) {
							errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat2"));
						}
					} catch (NumberFormatException e) {
						errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
					}
				}
				
				// new comment
				if (ecForm.getNewOvertimeComment().length() > GlobalConstants.EMPLOYEECONTRACT_OVERTIME_COMMENT_MAX_LENGTH) {
					errors.add("newOvertimeComment", new ActionMessage("form.employeecontract.error.overtimecomment.toolong"));
				} else if (ecForm.getNewOvertimeComment().trim().length() == 0) {
					errors.add("newOvertimeComment", new ActionMessage("form.employeecontract.error.overtimecomment.missing"));
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
				Double totalOvertime = 0.0;
				for (Overtime ot : overtimes) {
					totalOvertime += ot.getTime();
				}
				
				// optimizing totalOvertime
				String tOString = totalOvertime.toString();
				
				if (tOString.length() - tOString.indexOf(".") > 2) {
					tOString = tOString.substring(0, tOString.indexOf(".") + 3);
					totalOvertime = Double.parseDouble(tOString);
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
				} 
				boolean newContract = false;
				if (ec == null) {
					// new employee contract
					ec = new Employeecontract();
					newContract = true;
				}
				
				Employee theEmployee = (employeeDAO.getEmployeeById(employeeId));
				
				
//				EmployeeHelper eh = new EmployeeHelper();
				
				
				
				ec.setEmployee(theEmployee);
				
				ActionMessages errorMessages = validateFormData(request, ecForm, theEmployee, ec);
				if (errorMessages.size() > 0) {
					return mapping.getInputForward();
				}

				if (ecForm.getValidUntil() != null && !ecForm.getValidUntil().trim().equals("")) {
					Date untilDate = Date.valueOf(ecForm.getValidUntil());
					ec.setValidUntil(untilDate);
				} else {
					ec.setValidUntil(null);
				}
				
				Date fromDate = Date.valueOf(ecForm.getValidFrom());
				ec.setValidFrom(fromDate);
				
				Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
				
				
				// adjust employeeorders
				List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractId(ec.getId());
				if (employeeorders != null && !employeeorders.isEmpty()) {
					for (Employeeorder employeeorder : employeeorders) {
						boolean changed = false;
						 if (employeeorder.getFromDate().before(fromDate)) {
							 employeeorder.setFromDate(fromDate);
							 changed = true;
						 }
						 if (employeeorder.getUntilDate() != null && employeeorder.getUntilDate().before(fromDate)) {
							 employeeorder.setUntilDate(fromDate);
							 changed = true;
						 }
						 if (ec.getValidUntil() != null) {
							 if (employeeorder.getFromDate().after(ec.getValidUntil())) {
								 employeeorder.setFromDate(ec.getValidUntil());
								 changed = true;
							 }
							 if (employeeorder.getUntilDate() == null || employeeorder.getUntilDate().after(ec.getValidUntil())) {
								 employeeorder.setUntilDate(ec.getValidUntil());
								 changed = true;
							 }
						 }
						if (changed) {
							employeeorderDAO.save(employeeorder, loginEmployee);
						}						 
					}
				}
				
				
/*  Supervisor validation */		
				if(ecForm.getSupervisorid()== employeeId){
					ActionMessages errors = getErrors(request);
					if (errors == null) errors = new ActionMessages();
					errors.add("invalidSupervisor", new ActionMessage("form.timereport.error.employeecontract.invalidsupervisor"));
					saveErrors(request, errors);
					return mapping.getInputForward();
				}else{
					ec.setSupervisor(employeeDAO.getEmployeeById(ecForm.getSupervisorid()));
				}
				
				ec.setTaskDescription(ecForm.getTaskdescription());
				ec.setFreelancer(ecForm.getFreelancer());
				ec.setHide(ecForm.getHide());
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
				
				boolean addMoreContracts = Boolean.parseBoolean(request.getParameter("continue"));
				if (!addMoreContracts) {
					
					String filter = null;
					Boolean show = null;
					Long filterEmployeeId = null; 
					
					if (request.getSession().getAttribute("employeeContractFilter") != null) {
						filter = (String) request.getSession().getAttribute("employeeContractFilter");
					}
					if (request.getSession().getAttribute("employeeContractShow") != null) {
						show = (Boolean) request.getSession().getAttribute("employeeContractShow");
					}
					if (request.getSession().getAttribute("employeeContractEmployeeId") != null) {
						filterEmployeeId = (Long) request.getSession().getAttribute("employeeContractEmployeeId");
					}
					
					request.getSession().setAttribute("employeecontracts", employeecontractDAO.getEmployeeContractsByFilters(show, filter, filterEmployeeId));			

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
	
	private ActionMessages valiDate(HttpServletRequest request, AddEmployeeContractForm ecForm, String which) {
		ActionMessages errors = getErrors(request);
		if (errors == null) errors = new ActionMessages();
		
		String dateString = "";
		if (which.equals("from")) {
			dateString = ecForm.getValidFrom().trim();
		} else {
			dateString = ecForm.getValidUntil().trim();
		}
		
		int minus=0;
		for (int i = 0; i < dateString.length(); i++) {
			if (dateString.charAt(i) == '-') minus++;	
		}
		if (dateString.length() != 10 || minus != 2) {
			if (which.equals("from")) {
				errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
			} else {
				errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
			}
		}
		
		saveErrors(request, errors);
		return errors;
	}
	
	
	/**
	 * validates the form data (syntax and logic)
	 * 
	 * @param request
	 * @param cuForm
	 * @return
	 */
	private ActionMessages validateFormData(HttpServletRequest request, AddEmployeeContractForm ecForm,
			Employee theEmployee, Employeecontract employeecontract) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
		
		// check date formats (must now be 'yyyy-MM-dd')
		String dateFromString = ecForm.getValidFrom().trim();
		boolean dateError = DateUtils.validateDate(dateFromString);
		if (dateError) {
			errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
		} 
		
		String dateUntilString = ecForm.getValidUntil().trim();
		if (dateUntilString != null && !dateUntilString.equals("")) {
			dateError = DateUtils.validateDate(dateUntilString);
			if (dateError) {
				errors.add("validUntil", new ActionMessage(
						"form.timereport.error.date.wrongformat"));
			}
		}		
		//		String validFrom = ecForm.getValidFrom();
//		String validUntil = ecForm.getValidUntil();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		java.util.Date newContractValidFrom;
		java.util.Date newContractValidUntil = null;
		try {
			newContractValidFrom = simpleDateFormat.parse(dateFromString);
			if (dateUntilString != null && !dateUntilString.equals("")) {
				newContractValidUntil = simpleDateFormat.parse(dateUntilString);							
			}
		} catch (ParseException e) {
			// this is not expected...
			throw new RuntimeException("Date cannot be parsed - fatal error!");
		}
		
		if (newContractValidUntil != null && newContractValidFrom.after(newContractValidUntil)){
			errors.add("validFrom", new ActionMessage("form.employeecontract.error.endbeforebegin"));		
		}
		
		// for a new employeecontract, check if other contract for this employee already exists
		Long ecId = (Long) request.getSession().getAttribute("ecId");
		if (ecId == null) {
			List<Employeecontract> allEmployeecontracts = employeecontractDAO.getEmployeeContracts();
			for (Iterator iter = allEmployeecontracts.iterator(); iter.hasNext();) {
				Employeecontract ec = (Employeecontract) iter.next();
				if ((ec.getEmployee().getId() == theEmployee.getId()) && ec.getId() != employeecontract.getId() ) {
					// contract for the same employee found but not the same contract - check overleap
					java.util.Date existingContractValidFrom = ec.getValidFrom();
					java.util.Date existingContractValidUntil = ec.getValidUntil();
					
										
					if (newContractValidUntil != null && existingContractValidUntil != null) {
						if (!newContractValidFrom.before(existingContractValidFrom)
								&& !newContractValidFrom.after(existingContractValidUntil)) {
							// validFrom overleaps!
							errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
							break;
						}
						if (!newContractValidUntil.before(existingContractValidFrom)
								&& !newContractValidUntil.after(existingContractValidUntil)) {
							// validUntil overleaps!
							errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
							break;
						}
						if (newContractValidFrom.before(existingContractValidFrom)
								&& newContractValidUntil.after(existingContractValidUntil)) {
							// new Employee contract enclosures an existing one
							errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
							break;
						}
					} else if (newContractValidUntil == null && existingContractValidUntil != null) {
						if (!newContractValidFrom.after(existingContractValidUntil)) {
							errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
							break;
						}	
					} else if (newContractValidUntil != null && existingContractValidUntil == null) {
						if (!newContractValidUntil.before(existingContractValidFrom)) {
							errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
							break;
						}
					} else {
						// two employee contracts with open end MUST overleap
						errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
						break;
					}
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
		time += 0.5;
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
				
				time = initialOvertimeDouble * 100000;
				time += 0.5;
				time2 = time.intValue();
				modulo = time2%5000;
				ecForm.setInitialOvertime(""+(time2/100000.0));
				
				
				if (modulo != 0) {
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
		
		// check, if dates fit to existing timereports
		Date sqlUntilDate = null;
		if (newContractValidUntil != null) {
			sqlUntilDate = new java.sql.Date(newContractValidUntil.getTime());
		}
		if (ecId == null) {
			ecId = 0l;
		}
		List<Timereport> timereportsInvalidForDates = timereportDAO.
			getTimereportsByEmployeeContractIdInvalidForDates(new java.sql.Date(newContractValidFrom.getTime()), sqlUntilDate, ecId);
		if (timereportsInvalidForDates != null && !timereportsInvalidForDates.isEmpty()) {
			request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
			errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));
			
		}
		
		
		saveErrors(request, errors);
		
		return errors;
	}
}
