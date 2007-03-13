package org.tb.web.action;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Timereport;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.DateUtils;
import org.tb.util.OptionItem;
import org.tb.web.form.ShowReleaseForm;

public class ShowReleaseAction extends LoginRequiredAction {

	private EmployeecontractDAO employeecontractDAO;
	private TimereportDAO timereportDAO;
	
	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}
	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}
	
	
	@Override
	protected ActionForward executeAuthenticated(ActionMapping mapping,
			ActionForm form, HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		
		ShowReleaseForm releaseForm = (ShowReleaseForm) form;
		boolean updateEmployee = false;
		
		request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());
		request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());
		
		
		Employeecontract employeecontract = null;
		if (releaseForm.getEmployeeContractId() != null) {
			employeecontract = employeecontractDAO
					.getEmployeeContractById(releaseForm
							.getEmployeeContractId());
			
		}		
		
		if ((Boolean) request.getSession().getAttribute("employeeAuthorized")) {
			Employeecontract currentEmployeeContract = null;
			if ((request.getParameter("task") != null)
					&& ((request.getParameter("task").equals("updateEmployee")))) {
				updateEmployee = true;
			} else {
				currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
			}
			if (currentEmployeeContract != null) {
				if (!currentEmployeeContract.equals(employeecontract)) {
					employeecontract = currentEmployeeContract;
				}				
				releaseForm.setEmployeeContractId(employeecontract.getId());
			}			
			
		}
		
		
		if (employeecontract == null) {
			Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
			employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), new Date());
			releaseForm.setEmployeeContractId(employeecontract.getId());
		}	
		
		
//		if ((request.getSession().getAttribute("employeeAuthorized") != null) && ((Boolean) request.getSession().getAttribute("employeeAuthorized"))) {
			List<Employeecontract> employeeContracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
			request.getSession().setAttribute("employeecontracts", employeeContracts);
//		}
		
		request.getSession().setAttribute("employeeContractId", employeecontract.getId());
		request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
		request.getSession().setAttribute("currentEmployeeContract", employeecontract);
		
		// date from contract
		Date releaseDateFromContract = employeecontract.getReportReleaseDate();
		Date acceptanceDateFromContract = employeecontract.getReportAcceptanceDate();
		
		if (releaseDateFromContract == null) {
			releaseDateFromContract = employeecontract.getValidFrom();
		}
		if (acceptanceDateFromContract == null) {
			acceptanceDateFromContract = employeecontract.getValidFrom();
		}
		
		TimereportHelper th = new TimereportHelper();
		
		
		
		
		if ((request.getParameter("task") != null)
				&& ((request.getParameter("task").equals("release")))) {
			
			// validate form data
			ActionMessages errorMessages = validateFormDataForRelease(request, releaseForm, employeecontract);
			if (errorMessages.size() > 0) {
				return mapping.getInputForward();
			}
			
			// set selected date in session
			request.getSession().setAttribute("releaseDay", releaseForm.getDay());
			request.getSession().setAttribute("releaseMonth", releaseForm.getMonth());
			request.getSession().setAttribute("releaseYear", releaseForm.getYear());
			

			java.util.Date releaseDate = (java.util.Date) request.getSession().getAttribute("releaseDate");
			java.sql.Date sqlReleaseDate = new java.sql.Date(releaseDate.getTime());
			
			Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
			
			// set status in timereports
			List<Timereport> timereports = timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeecontract.getId(), sqlReleaseDate);		
			for (Timereport timereport : timereports) {
				timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_COMMITED);
				timereport.setReleasedby(loginEmployee.getSign());
				timereport.setReleased(new java.util.Date());
				timereportDAO.save(timereport, loginEmployee, false);
			}
			releaseDateFromContract = releaseDate;
			
			request.getSession().setAttribute("days", getDayList(releaseDateFromContract));
			
			// store new release date in employee contract
			employeecontract.setReportReleaseDate(sqlReleaseDate);
			employeecontractDAO.save(employeecontract, loginEmployee); 
			
			
		}
		
		if ((request.getParameter("task") != null)
				&& ((request.getParameter("task").equals("accept")))) {
			
			// validate form data
			ActionMessages errorMessages = validateFormDataForAcceptance(request, releaseForm, employeecontract);
			if (errorMessages.size() > 0) {
				return mapping.getInputForward();
			}
			
			java.util.Date acceptanceDate = (java.util.Date) request.getSession().getAttribute("acceptanceDate");
			java.sql.Date sqlAcceptanceDate = new java.sql.Date(acceptanceDate.getTime());
			
			Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
			
			// set status in timereports
			List<Timereport> timereports = timereportDAO.getCommitedTimereportsByEmployeeContractIdBeforeDate(employeecontract.getId(), sqlAcceptanceDate);		
			for (Timereport timereport : timereports) {
				timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_CLOSED);
				timereport.setAcceptedby(loginEmployee.getSign());
				timereport.setAccepted(new java.util.Date());
				timereportDAO.save(timereport, loginEmployee, false);
			}
			acceptanceDateFromContract = acceptanceDate;
			
			request.getSession().setAttribute("acceptanceDays", getDayList(acceptanceDateFromContract));
			
			// store new acceptance date in employee contract
			employeecontract.setReportAcceptanceDate(sqlAcceptanceDate);
			employeecontractDAO.save(employeecontract, loginEmployee); 
			
			
		}
		
		
		if ((request.getParameter("task") != null)
				&& ((request.getParameter("task").equals("reopen")))) {
			
			Date reopenDate = null;
			
			reopenDate = th.getDateFormStrings(releaseForm.getReopenDay(), releaseForm.getReopenMonth(), releaseForm.getReopenYear(), false);
			
//			SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy");
//			String reopenDateString = format.format(reopenDate);
//			String releaseDateString = format.format(releaseDateFromContract);
//			String acceptancedateString = format.format(acceptanceDateFromContract);
			
			if (reopenDate == null) {
				reopenDate = new Date();
			}
			java.sql.Date sqlReopenDate = new java.sql.Date(reopenDate.getTime());
			
			Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
			
			// set status in timereports
			List<Timereport> timereports = timereportDAO.getTimereportsByEmployeeContractIdAfterDate(employeecontract.getId(), sqlReopenDate);		
			for (Timereport timereport : timereports) {
				timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
				timereportDAO.save(timereport, loginEmployee, false);
			}
			
			long timeMillis = sqlReopenDate.getTime();
			timeMillis -= 12*60*60*1000;
			sqlReopenDate.setTime(timeMillis);
//			String newReopenDateString = format.format(sqlReopenDate);
			
			if (sqlReopenDate.before(releaseDateFromContract)) {
				employeecontract.setReportReleaseDate(sqlReopenDate);
				releaseDateFromContract = sqlReopenDate;
				String[] releaseDateArray = th.getDateAsStringArray(releaseDateFromContract);
				releaseForm.setDay(releaseDateArray[0]);
				releaseForm.setMonth(releaseDateArray[1]);
				releaseForm.setYear(releaseDateArray[2]);
			}
			if (sqlReopenDate.before(acceptanceDateFromContract)) {
				employeecontract.setReportAcceptanceDate(sqlReopenDate);
				acceptanceDateFromContract = sqlReopenDate;
				String[] acceptanceDateArray = th.getDateAsStringArray(acceptanceDateFromContract);
				releaseForm.setAcceptanceDay(acceptanceDateArray[0]);
				releaseForm.setAcceptanceMonth(acceptanceDateArray[1]);
				releaseForm.setAcceptanceYear(acceptanceDateArray[2]);
			}
			
			
			
			request.getSession().setAttribute("reopenDays", getDayList(reopenDate));
			
			// store date in employee contract
			employeecontractDAO.save(employeecontract, loginEmployee); 
			
			
		}
		
		
		if ((request.getParameter("task") != null)
				&& ((request.getParameter("task").equals("refreshDate")))) {
			// set selected date in session
			request.getSession().setAttribute("releaseDay", releaseForm.getDay());
			request.getSession().setAttribute("releaseMonth", releaseForm.getMonth());
			request.getSession().setAttribute("releaseYear", releaseForm.getYear());
			
			Date selectedDate = th.getDateFormStrings("01", releaseForm.getMonth(), releaseForm.getYear(), false);
				
			request.getSession().setAttribute("days", getDayList(selectedDate));
						
		}
		
		if ((request.getParameter("task") != null)
				&& ((request.getParameter("task").equals("refreshAcceptanceDate")))) {
			
			Date selectedDate = th.getDateFormStrings("01", releaseForm.getAcceptanceMonth(), releaseForm.getAcceptanceYear(), false);
				
			request.getSession().setAttribute("acceptanceDays", getDayList(selectedDate));
						
		}
		
		if ((request.getParameter("task") != null)
				&& ((request.getParameter("task").equals("refreshReopenDate")))) {
			
			Date selectedDate = th.getDateFormStrings("01", releaseForm.getReopenMonth(), releaseForm.getReopenYear(), false);
				
			request.getSession().setAttribute("reopenDays", getDayList(selectedDate));
						
		}
		
		if (request.getParameter("task") == null || updateEmployee) {
			String[] releaseDateArray = th.getDateAsStringArray(releaseDateFromContract);
			String[] acceptanceDateArray = th.getDateAsStringArray(acceptanceDateFromContract);
			
			// set form entries
			releaseForm.setDay(releaseDateArray[0]);
			releaseForm.setMonth(releaseDateArray[1]);
			releaseForm.setYear(releaseDateArray[2]);
			releaseForm.setAcceptanceDay(acceptanceDateArray[0]);
			releaseForm.setAcceptanceMonth(acceptanceDateArray[1]);
			releaseForm.setAcceptanceYear(acceptanceDateArray[2]);
			releaseForm.setReopenDay(releaseDateArray[0]);
			releaseForm.setReopenMonth(releaseDateArray[1]);
			releaseForm.setReopenYear(releaseDateArray[2]);
			
			
			request.getSession().setAttribute("releaseDay", releaseDateArray[0]);
			request.getSession().setAttribute("releaseMonth", releaseDateArray[1]);
			request.getSession().setAttribute("releaseYear", releaseDateArray[2]);
			
			request.getSession().setAttribute("days", getDayList(releaseDateFromContract));
			request.getSession().setAttribute("acceptanceDays", getDayList(acceptanceDateFromContract));
			request.getSession().setAttribute("reopenDays", getDayList(releaseDateFromContract));
		}

		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		
		String releasedUntil = simpleDateFormat.format(releaseDateFromContract);
		request.getSession().setAttribute("releasedUntil", releasedUntil);
		
		String acceptedUntil = simpleDateFormat.format(acceptanceDateFromContract);
		request.getSession().setAttribute("acceptedUntil", acceptedUntil);
		
		return mapping.findForward("success");
	}

	
	/**
	 * 
	 * @param request
	 * @param releaseForm
	 * @return
	 */
	private ActionMessages validateFormDataForRelease(HttpServletRequest request, ShowReleaseForm releaseForm, Employeecontract selectedEmployeecontract) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
	
		TimereportHelper th = new TimereportHelper();
		Date date = null;
		try {
			date = th.getDateFormStrings(releaseForm.getDay(), releaseForm.getMonth(), releaseForm.getYear(), false);
		} catch (Exception e) {
			errors.add("releasedate", new ActionMessage("form.release.error.date.corrupted"));
		}
		
		if (date == null) {
			date = new Date();
		}
		request.getSession().setAttribute("releaseDate", date);
		
//		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
//		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), new Date());
		
		if(date.before(selectedEmployeecontract.getValidFrom()) || (selectedEmployeecontract.getValidUntil() != null && date.after(selectedEmployeecontract.getValidUntil()))) {
			errors.add("releasedate", new ActionMessage("form.release.error.date.invalid.foremployeecontract"));
		}
		
//		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy");
//		String newReleasedate = simpleDateFormat.format(date);
//		String oldReleasedate = simpleDateFormat.format(selectedEmployeecontract.getReportReleaseDate());
		
		if (date.before(selectedEmployeecontract.getReportReleaseDate())) {
			errors.add("releasedate", new ActionMessage("form.release.error.date.before.stored"));
		}
		
		saveErrors(request, errors);
		
		return errors;
		
	}
	
	/**
	 * 
	 * @param request
	 * @param releaseForm
	 * @return
	 */
	private ActionMessages validateFormDataForAcceptance(HttpServletRequest request, ShowReleaseForm releaseForm, Employeecontract selectedEmployeecontract) {

		ActionMessages errors = getErrors(request);
		if(errors == null) errors = new ActionMessages();
	
		TimereportHelper th = new TimereportHelper();
		Date date = null;
		try {
			date = th.getDateFormStrings(releaseForm.getAcceptanceDay(), releaseForm.getAcceptanceMonth(), releaseForm.getAcceptanceYear(), false);
		} catch (Exception e) {
			errors.add("acceptancedate", new ActionMessage("form.release.error.date.corrupted"));
		}
		
		if (date == null) {
			date = new Date();
		}
		request.getSession().setAttribute("acceptanceDate", date);
		
//		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
//		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), new Date());
		
		if(date.before(selectedEmployeecontract.getValidFrom()) || (selectedEmployeecontract.getValidUntil() != null && date.after(selectedEmployeecontract.getValidUntil()))) {
			errors.add("acceptancedate", new ActionMessage("form.release.error.date.invalid.foremployeecontract"));
		}
		
		Date releaseDate = selectedEmployeecontract.getReportReleaseDate();
		if (releaseDate == null) {
			releaseDate = selectedEmployeecontract.getValidFrom();
		}
		
		if (date.after(releaseDate)) {
			errors.add("acceptancedate", new ActionMessage("form.release.error.date.before.release"));
		}
		
		if (date.before(selectedEmployeecontract.getReportAcceptanceDate())) {
			errors.add("acceptancedate", new ActionMessage("form.release.error.date.before.stored"));
		}
		
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		if (selectedEmployeecontract.getEmployee().equals(loginEmployee)) {
			errors.add("acceptancedate", new ActionMessage("form.release.error.foureyesprinciple"));
		}
		
		saveErrors(request, errors);
		
		return errors;
		
	}
	
	/**
	 * Returns a list of days as {@link OptionItem}s ("01", "02", "03",...) fitting to the given date (month, year).
	 * @param date
	 * @return
	 */
	private List<OptionItem> getDayList(Date date) {
		GregorianCalendar gc = new GregorianCalendar();
		gc.setTime(date);
		int maxDays = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
		List<OptionItem> days = new ArrayList<OptionItem>();
				
		String dayValue = "";
		String dayLabel = "";
		for (int i=1; i<=maxDays; i++) {
			if (i<10) {
				dayLabel = "0" + i;
				dayValue = "0" + i;
			} else if (i>=10) {
				dayLabel = "" + i;
				dayValue = "" + i;
			}
			days.add(new OptionItem(dayValue, dayLabel));
		}
		return days;
	}
	
}
