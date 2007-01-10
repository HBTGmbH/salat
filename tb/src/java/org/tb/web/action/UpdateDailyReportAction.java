package org.tb.web.action;

import java.sql.Date;
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
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.MonthlyreportDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.ShowDailyReportForm;
import org.tb.web.form.UpdateDailyReportForm;


/**
 * action class for updating a timereport directly from daily display
 * 
 * @author oda
 *
 */
public class UpdateDailyReportAction extends DailyReportAction {
	
	private SuborderDAO suborderDAO;
	private CustomerorderDAO customerorderDAO;
	private TimereportDAO timereportDAO;
	private PublicholidayDAO publicholidayDAO;
	private MonthlyreportDAO monthlyreportDAO;
	private VacationDAO vacationDAO;
	private WorkingdayDAO workingdayDAO;
	private EmployeeorderDAO employeeorderDAO;
	private OvertimeDAO overtimeDAO;
	private EmployeeDAO employeeDAO;
	private EmployeecontractDAO employeecontractDAO;
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}
	
	public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
		this.overtimeDAO = overtimeDAO;
	}
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
		this.customerorderDAO = customerorderDAO;
	}
	
	public TimereportDAO getTimereportDAO() {
		return timereportDAO;
	}

	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}

	public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
		this.publicholidayDAO = publicholidayDAO;
	}
	
	public void setMonthlyreportDAO(MonthlyreportDAO monthlyreportDAO) {
		this.monthlyreportDAO = monthlyreportDAO;
	}
	
	public void setVacationDAO(VacationDAO vacationDAO) {
		this.vacationDAO = vacationDAO;
	}
	
	public void setWorkingdayDAO(WorkingdayDAO workingdayDAO) {
		this.workingdayDAO = workingdayDAO;
	}
	
	public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
		this.employeeorderDAO = employeeorderDAO;
	}

	/* (non-Javadoc)
	 * @see org.tb.web.action.LoginRequiredAction#executeAuthenticated(org.apache.struts.action.ActionMapping, org.apache.struts.action.ActionForm, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
			UpdateDailyReportForm reportForm = (UpdateDailyReportForm) form;
			
			if (request.getParameter("trId") != null) {
				long trId = Long.parseLong(request.getParameter("trId"));;
				Timereport tr = timereportDAO.getTimereportById(trId);
				
				Date theDate = tr.getReferenceday().getRefdate();
				Employeecontract ec = tr.getEmployeecontract();
		
				ActionMessages errorMessages = validateFormData(request, reportForm, theDate, tr);
				if (errorMessages.size() > 0) {
					return mapping.getInputForward();
				}
				
				tr.setTaskdescription(reportForm.getComment());	
				tr.setDurationhours(new Integer(reportForm.getSelectedDurationHour()));
				tr.setDurationminutes(new Integer(reportForm.getSelectedDurationMinute()));					
				tr.setCosts(reportForm.getCosts());

				
				// save updated report
				Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
				timereportDAO.save(tr, loginEmployee);
				

//				if (tr.getSortofreport().equals("W")) {
//					// update monthly hour balance...
//
//					String year = DateUtils.getYearString(tr.getReferenceday().getRefdate());	// yyyy
//					String month = DateUtils.getMonthString(tr.getReferenceday().getRefdate()); // MM
//					
//					Monthlyreport mr = 
//						monthlyreportDAO.getMonthlyreportByYearAndMonthAndEmployeecontract
//						(ec.getId(), Integer.parseInt(year), Integer.parseInt(month));
//					request.getSession().setAttribute("hourbalance", mr.getHourbalance());
//				}
				
				// get updated list of timereports from DB
				ShowDailyReportForm showDailyReportForm = new ShowDailyReportForm();
				showDailyReportForm.setDay((String)request.getSession().getAttribute("currentDay"));
				showDailyReportForm.setMonth((String)request.getSession().getAttribute("currentMonth"));
				showDailyReportForm.setYear((String)request.getSession().getAttribute("currentYear"));				
				showDailyReportForm.setLastday((String)request.getSession().getAttribute("lastDay"));
				showDailyReportForm.setLastmonth((String)request.getSession().getAttribute("lastMonth"));
				showDailyReportForm.setLastyear((String)request.getSession().getAttribute("lastYear"));
				showDailyReportForm.setEmployeeId(ec.getEmployee().getId());
				showDailyReportForm.setView((String)request.getSession().getAttribute("view"));
				
				refreshTimereports(mapping, request, showDailyReportForm, customerorderDAO, timereportDAO, employeecontractDAO, suborderDAO, employeeorderDAO, publicholidayDAO, overtimeDAO, vacationDAO, employeeDAO);
				List<Timereport> timereports = (List<Timereport>) request.getSession().getAttribute("timereports");
				
				
				TimereportHelper th = new TimereportHelper();
				request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
				request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
				request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
				
				Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(tr.getReferenceday().getRefdate(), ec.getId());
				request.getSession().setAttribute("quittingtime",th.calculateQuittingTime(workingday, request));
				
				//refresh overtime
				refreshVacationAndOvertime(request, ec, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
				
				return mapping.findForward("success");
			} 
						
			return mapping.findForward("error");		
	}
	
	
	/**
	 * validates the form data (syntax and logic)
	 * 
	 * @param request
	 * @param reportForm
	 * @param theDate - sql date
	 * @param theTimereport
	 * @return
	 */
	private ActionMessages validateFormData(HttpServletRequest request, 
											UpdateDailyReportForm reportForm,
											Date theDate,
											Timereport theTimereport
											
											) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
		
		
		// if sort of report is not 'W' reports are only allowed for workdays
		// e.g., vacation cannot be set on a Sunday
		if (!theTimereport.getSortofreport().equals("W")) {
			boolean valid = true;
			String dow = DateUtils.getDow(theDate);
			if ((dow.equalsIgnoreCase("Sat")) || (dow.equalsIgnoreCase("Sun"))) {
				valid = false;
			}
			
			// checks for public holidays
			if (valid) {			
				String publicHoliday = publicholidayDAO.getPublicHoliday(theDate);
				if ((publicHoliday != null) && (publicHoliday.length() > 0)) {
					valid = false;
				}
			}
			
			if (!valid) {
				errors.add("sortOfReport", new ActionMessage("form.timereport.error.sortofreport.invalidday"));		
			}
		}

		
		if (theTimereport.getSortofreport().equals("W")) {
			// check costs format		
			if (!GenericValidator.isDouble(reportForm.getCosts().toString()) ||
				(!GenericValidator.isInRange(reportForm.getCosts(), 
						0.0, GlobalConstants.MAX_COSTS))) {
				errors.add("costs", new ActionMessage("form.timereport.error.costs.wrongformat"));
			}
		}
			
		// check comment length
		if (!GenericValidator.maxLength(reportForm.getComment(),GlobalConstants.COMMENT_MAX_LENGTH)) {
			errors.add("comment", new ActionMessage("form.timereport.error.comment.toolarge"));
		}
			
		
		saveErrors(request, errors);
		
		return errors;
	}
}
