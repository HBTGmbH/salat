package org.tb.web.action;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.helper.TimereportHelper;
import org.tb.helper.VacationViewer;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.web.form.LoginEmployeeForm;

/**
 * Action class for the login of an employee
 * 
 * @author oda, th
 *
 */
public class LoginEmployeeAction extends Action {
	
	private EmployeeDAO employeeDAO;
	private PublicholidayDAO publicholidayDAO;
	private EmployeecontractDAO employeecontractDAO;
	private SuborderDAO suborderDAO;
	private EmployeeorderDAO employeeorderDAO;
	private OvertimeDAO overtimeDAO;
	private TimereportDAO timereportDAO;
	
	
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	
	public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
		this.overtimeDAO = overtimeDAO;
	}
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
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
		
		Date date = new Date();
		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), date);
		if(employeecontract == null && !(loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM))) {
			ActionMessages errors = getErrors(request);
			if(errors == null) errors = new ActionMessages();
			errors.add(null, new ActionMessage("form.login.error.invalidcontract"));

			saveErrors(request, errors);
			return mapping.getInputForward();
		}
		
		request.getSession().setAttribute("loginEmployee", loginEmployee);
		String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
		request.getSession().setAttribute("loginEmployeeFullName", loginEmployeeFullName);
		request.getSession().setAttribute("report", "W");  
		
		request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
		
		if ((loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_BL)) || 
			    (loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_GF)) ||
			    (loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM))) {
					request.getSession().setAttribute("employeeAuthorized", true);
		} else {
			request.getSession().setAttribute("employeeAuthorized", false);
		}
		
		// not necessary at the moment
//		if(employeeDAO.isAdmin(loginEmployee)) {
//			request.getSession().setAttribute("admin", Boolean.TRUE);
//		}
		
		// check if public holidays are available
		publicholidayDAO.checkPublicHolidaysForCurrentYear();
		
		// check if employee has an employee contract and is has employee orders for all standard suborders
//		Date date = new Date();
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
		String dateString = simpleDateFormat.format(date);
		date = simpleDateFormat.parse(dateString);
//		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), date);
		
		if (employeecontract != null) {
			request.getSession().setAttribute("employeeHasValidContract", true);
			
			if (!(loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM))) {
				List<Suborder> standardSuborders = suborderDAO
						.getStandardSuborders();
				if (standardSuborders != null && standardSuborders.size() > 0) {
					// test if employeeorder exists
					Employeeorder employeeorder;
					for (Suborder suborder : standardSuborders) {
						employeeorder = employeeorderDAO
								.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(
										employeecontract.getId(), suborder
												.getId(), date);
						if (employeeorder == null) {
							// create employeeorder
							SimpleDateFormat yearFormat = new SimpleDateFormat(
									"yyyy");
							String year = yearFormat.format(date);
							Date fromDate = simpleDateFormat.parse(year
									+ "0101");
							Date untilDate = simpleDateFormat.parse(year
									+ "1231");
							java.sql.Date sqlFromDate = new java.sql.Date(
									fromDate.getTime());
							java.sql.Date sqlUntilDate = new java.sql.Date(
									untilDate.getTime());

							employeeorder = new Employeeorder();
							if (suborder
									.getCustomerorder()
									.getSign()
									.equals(
											GlobalConstants.CUSTOMERORDER_SIGN_VACATION)) {
								employeeorder.setDebithours(employeecontract
										.getDailyWorkingTime()
										* employeecontract
												.getVacationEntitlement());
							} else {
								employeeorder.setDebithours(suborder
										.getCustomerorder().getHourly_rate());
							}
							employeeorder.setEmployeecontract(employeecontract);
							employeeorder.setFromDate(sqlFromDate);
							employeeorder.setSign(" ");
							employeeorder.setStandingorder(true);
							employeeorder.setStatus(" ");
							employeeorder.setStatusreport(false);
							employeeorder.setSuborder(suborder);
							employeeorder.setUntilDate(sqlUntilDate);

							// create tmp employee
							Employee tmp = new Employee();
							tmp.setSign("system");

							employeeorderDAO.save(employeeorder, tmp);

						}
					}
				}
			}
			if (employeecontract.getReportAcceptanceDate() == null) {
				java.sql.Date validFromDate = employeecontract.getValidFrom();
				employeecontract.setReportAcceptanceDate(validFromDate);
				// create tmp employee
				Employee tmp = new Employee();
				tmp.setSign("system");
				employeecontractDAO.save(employeecontract, tmp);
			}
			if (employeecontract.getReportReleaseDate() == null) {
				java.sql.Date validFromDate = employeecontract.getValidFrom();
				employeecontract.setReportReleaseDate(validFromDate);
				// create tmp employee
				Employee tmp = new Employee();
				tmp.setSign("system");
				employeecontractDAO.save(employeecontract, tmp);
			}
			// set used employee contract of login employee
			request.getSession().setAttribute("loginEmployeeContract", employeecontract);
			request.getSession().setAttribute("loginEmployeeContractId", employeecontract.getId());
			
			
			
			// get info about vacation, overtime and report status
			String releaseDate = employeecontract.getReportReleaseDateString();
			String acceptanceDate = employeecontract.getReportAcceptanceDateString();
			
			request.getSession().setAttribute("releasedUntil", releaseDate);
			request.getSession().setAttribute("acceptedUntil", acceptanceDate);
			
			TimereportHelper th = new TimereportHelper();
			int[] overtime = th.calculateOvertime(employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
			int overtimeHours = overtime[0];
			int overtimeMinutes = overtime[1];
			
			boolean overtimeIsNegative = false;
			if (overtimeMinutes < 0) {
				overtimeIsNegative = true;
				overtimeMinutes *= -1;
			}
			if (overtimeHours < 0){ 
				overtimeIsNegative = true;
				overtimeHours *= -1;
			} 
			request.getSession().setAttribute("overtimeIsNegative", overtimeIsNegative);
			
			String overtimeString;
			if(overtimeIsNegative) {
				overtimeString = "-"+overtimeHours+":";
			} else {
				overtimeString = overtimeHours+":";
			}
			
			if (overtimeMinutes < 10) {
				overtimeString += "0";
			}
			overtimeString += overtimeMinutes;
			request.getSession().setAttribute("overtime", overtimeString);
			
			try {
				//overtime this month
				Date currentDate = new Date();
				SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyyyy");
				String dateString2 = dateFormat.format(currentDate);
				String monthYearString = dateString2.substring(2);
				Date start = dateFormat.parse("01" + monthYearString);
				int[] monthlyOvertime = th.calculateOvertime(start, currentDate,
						employeecontract, employeeorderDAO, publicholidayDAO,
						timereportDAO, overtimeDAO, false);
				int monthlyOvertimeHours = monthlyOvertime[0];
				int monthlyOvertimeMinutes = monthlyOvertime[1];
				boolean monthlyOvertimeIsNegative = false;
				if (monthlyOvertimeMinutes < 0) {
					monthlyOvertimeIsNegative = true;
					monthlyOvertimeMinutes *= -1;
				}
				if (monthlyOvertimeHours < 0) {
					monthlyOvertimeIsNegative = true;
					monthlyOvertimeHours *= -1;
				}
				request.getSession().setAttribute("monthlyOvertimeIsNegative",
						monthlyOvertimeIsNegative);
				String monthlyOvertimeString;
				if (monthlyOvertimeIsNegative) {
					monthlyOvertimeString = "-" + monthlyOvertimeHours + ":";
				} else {
					monthlyOvertimeString = monthlyOvertimeHours + ":";
				}
				if (monthlyOvertimeMinutes < 10) {
					monthlyOvertimeString += "0";
				}
				monthlyOvertimeString += monthlyOvertimeMinutes;
				request.getSession().setAttribute("monthlyOvertime",
						monthlyOvertimeString);
				
				SimpleDateFormat format = new SimpleDateFormat("yyyy-MM");
				request.getSession().setAttribute("overtimeMonth",
						format.format(start));
			} catch (ParseException e) {
				throw new RuntimeException("Error occured while parsing date");
			}
			
			
//			 vacation v2
			java.sql.Date today = new java.sql.Date(new java.util.Date().getTime());
			
			List<Employeeorder> orders = new ArrayList<Employeeorder>();
			
			List<Employeeorder> specialVacationOrders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndCustomerOrderSignAndDate(employeecontract.getId(), GlobalConstants.CUSTOMERORDER_SIGN_REMAINING_VACATION, today);
			List<Employeeorder> vacationOrders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndCustomerOrderSignAndDate(employeecontract.getId(), GlobalConstants.CUSTOMERORDER_SIGN_VACATION, today);
			List<Employeeorder> extraVacationOrders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndCustomerOrderSignAndDate(employeecontract.getId(), GlobalConstants.CUSTOMERORDER_SIGN_EXTRA_VACATION, today);

			
			orders.addAll(specialVacationOrders);
			orders.addAll(vacationOrders);
			orders.addAll(extraVacationOrders);
			
			List<VacationViewer> vacations = new ArrayList<VacationViewer>();
			
			for (Employeeorder employeeorder : orders) {
				VacationViewer vacationView = new VacationViewer(employeecontract);
				vacationView.setSuborderSign(employeeorder.getSuborder().getDescription());
				vacationView.setBudget(employeeorder.getDebithours());
				
				List<Timereport> timereports = timereportDAO.getTimereportsBySuborderIdAndEmployeeContractId(employeeorder.getSuborder().getId(), employeecontract.getId());
				for (Timereport timereport : timereports) {
					vacationView.addVacationHours(timereport.getDurationhours());
					vacationView.addVacationMinutes(timereport.getDurationminutes());
				}
				vacations.add(vacationView);
			}
			request.getSession().setAttribute("vacations", vacations);

			
		} else {
			request.getSession().setAttribute("employeeHasValidContract", false);
		}
		
		// show change password site, if password equals username
		if (loginEmployee.getLoginname().equalsIgnoreCase(loginEmployee.getPassword())) {
			return mapping.findForward("password");
		}
		
//		 create collection of employees with contracts
		List<Employee> employeesWithContracts = employeeDAO.getEmployeesWithContractsValidForDate(date);
		request.getSession().setAttribute("employeeswithcontract", employeesWithContracts);

		
		return mapping.findForward("success");
	}

}
