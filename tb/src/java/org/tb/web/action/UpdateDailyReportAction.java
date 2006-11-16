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
import org.tb.persistence.MonthlyreportDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;
import org.tb.web.form.UpdateDailyReportForm;


/**
 * action class for updating a timereport directly from daily display
 * 
 * @author oda
 *
 */
public class UpdateDailyReportAction extends DailyReportAction {
	
	private SuborderDAO suborderDAO;
//	private CustomerorderDAO customerorderDAO;
	private TimereportDAO timereportDAO;
	private PublicholidayDAO publicholidayDAO;
	private MonthlyreportDAO monthlyreportDAO;
	private VacationDAO vacationDAO;
	private WorkingdayDAO workingdayDAO;
	
	
	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}
	
//	public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
//		this.customerorderDAO = customerorderDAO;
//	}
	
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
//				double hours = TimereportHelper.calculateTime(reportForm);
		
				ActionMessages errorMessages = validateFormData(request, reportForm, theDate, tr);
				if (errorMessages.size() > 0) {
					return mapping.getInputForward();
				}
				
				tr.setTaskdescription(reportForm.getComment());	
//				tr.setEmployeecontract(ec);
	
//				if (tr.getSortofreport().equals("W")) {
					tr.setDurationhours(new Integer(reportForm.getSelectedDurationHour()));
					tr.setDurationminutes(new Integer(reportForm.getSelectedDurationMinute()));					
					tr.setCosts(reportForm.getCosts());
//					tr.setSuborder(suborderDAO.getSuborderById(reportForm.getTrSuborderId()));
//					tr.setStatus(reportForm.getStatus());
//				} else {
//					// 'special' reports: set employee's suborder to null				
//					tr.setSuborder(null);
//					tr.setStatus("");
//					tr.setCosts(0.0);
//				}
				
				// save updated report
				Employee loginEmployee = (Employee)request.getSession().getAttribute("loginEmployee");
				timereportDAO.save(tr, loginEmployee);
				
//				TimereportHelper th = new TimereportHelper();
				if (tr.getSortofreport().equals("W")) {
					// update monthly hour balance...
//					th.updateMonthlyHourBalance(tr, 1, timereportDAO, monthlyreportDAO);
					String year = DateUtils.getYearString(tr.getReferenceday().getRefdate());	// yyyy
					String month = DateUtils.getMonthString(tr.getReferenceday().getRefdate()); // MM
					
					Monthlyreport mr = 
						monthlyreportDAO.getMonthlyreportByYearAndMonthAndEmployeecontract
						(ec.getId(), Integer.parseInt(year), Integer.parseInt(month));
					request.getSession().setAttribute("hourbalance", mr.getHourbalance());
				}
				if (tr.getSortofreport().equals("V")) {
					// update vacation balance
					if (request.getSession().getAttribute("trId") == null) {
						// new report
//						th.updateVacation(tr, 1, vacationDAO);
					}
				}
				
				// get updated list of timereports from DB
				List<Timereport> timereports = timereportDAO
				.getTimereportsByDateAndEmployeeContractId(
						ec.getId(), theDate);
				request.getSession().setAttribute("timereports", timereports);
				
				TimereportHelper th = new TimereportHelper();
				request.getSession().setAttribute("labortime", th.calculateLaborTime(timereports));
				request.getSession().setAttribute("maxlabortime", th.checkLaborTimeMaximum(timereports, GlobalConstants.MAX_HOURS_PER_DAY));
				request.getSession().setAttribute("dailycosts", th.calculateDailyCosts(timereports));
				
				Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(tr.getReferenceday().getRefdate(), ec.getId());
				request.getSession().setAttribute("quittingtime",th.calculateQuittingTime(workingday, request));
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
		
//		long trId = theTimereport.getId();
		
		// end time must be later than begin time
//		int begin = reportForm.getSelectedHourBegin()*100 + reportForm.getSelectedMinuteBegin();
//		int end = reportForm.getSelectedHourEnd()*100 + reportForm.getSelectedMinuteEnd();
//		boolean selectedHourEndError = false;
//		if (theTimereport.getSortofreport().equals("W")) {
//			if (begin >= end) {
//				errors.add("selectedHourEnd", new ActionMessage("form.timereport.error.endbeforebegin"));
//			}
//		}
		
		// check if report types for one day are unique and if there is no time overlap with other reports
//		boolean timeOverlap = false;
//		List<Timereport> dailyReports = 
//				timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
//		if ((dailyReports != null) && (dailyReports.size() > 0)) {
//			for (Iterator iter = dailyReports.iterator(); iter.hasNext();) {
//				Timereport tr = (Timereport) iter.next();
//				if (tr.getId() != trId) { // do not check report against itself in case of edit
//					// uniqueness of types
//					// actually not checked - e.g., combination of sickness and work on ONE day
//					// should be valid
//					// time overlap
//					// do not check for time overlap in this form, otherwise switching/moving of hours
//					// might be very hard... --> consistency of working periods must be checked manually.
//					if (timereportDAO.checkTimeOverlap(tr, reportForm) == true) {
//						timeOverlap = true;
//						if (!selectedHourEndError)
//							errors.add("selectedHourEnd", new ActionMessage("form.timereport.error.timeoverlap"));		
//						break;
//					}
//				}
//			}
//		}
		
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
		
		
		// check hour sum (must be less than 10.0)
//		if ((!timeOverlap) && (theTimereport.getSortofreport().equals("W"))) {
//			List<Timereport> allReports = 
//				timereportDAO.getTimereportsByDateAndEmployeeContractId(ecId, theDate);
//			//double dailyHourSum = 
//			//	hours + TimereportHelper.calculateTimereportWorkingHourSum(allReports, theTimereport.getId());
//			double dailyHourSum = TimereportHelper.calculateDailyHourSum(allReports);
//			if (dailyHourSum > GlobalConstants.MAX_HOURS_PER_DAY) {
//				if (!selectedHourEndError)
//					errors.add("selectedHourEnd", new ActionMessage("form.timereport.error.hours.exceeded"));
//			}
//		}
		
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
			
		// if edited from daily overview, orders/suborders must be checked for consistency		
//		if (request.getParameter("trId") != null) {
//			if (theTimereport.getSortofreport().equals("W")) {
//				// selected suborder must belong to selected order
//				long soId = reportForm.getTrSuborderId();
//				Customerorder co = (Customerorder) customerorderDAO.getCustomerorderById(reportForm.getTrOrderId());
//				boolean consistent = false;
//				for (Iterator iter = co.getSuborders().iterator(); iter.hasNext();) {
//					Suborder soInCo = (Suborder) iter.next();
//					if (soInCo.getId() == soId) {
//						consistent = true;
//						break;
//					}
//				}
//				if (!consistent) {
//					errors.add("trSuborderId", new ActionMessage("form.timereport.error.order.suborder.inconsistent"));
//				}
//			}
//		} 
		
		saveErrors(request, errors);
		
		return errors;
	}
}
