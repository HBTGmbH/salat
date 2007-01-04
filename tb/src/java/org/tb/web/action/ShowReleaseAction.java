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
		
		request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());
		request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());
		
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), new Date());
		
		
		// date from contract here
		Date releaseDateFromContract = employeecontract.getReportReleaseDate();
		Date acceptanceDateFromContract = employeecontract.getReportAcceptanceDate();
		
		TimereportHelper th = new TimereportHelper();
		
		if ((Boolean) request.getSession().getAttribute("employeeAuthorized")) {
			List<Employeecontract> employeeContracts = employeecontractDAO.getEmployeeContracts();
			request.getSession().setAttribute("employeecontracts", employeeContracts);
		}
		
		
		if ((request.getParameter("task") != null)
				&& ((request.getParameter("task").equals("release")))) {
			
			// validate form data
			ActionMessages errorMessages = validateFormData(request, releaseForm);
			if (errorMessages.size() > 0) {
				return mapping.getInputForward();
			}
			
			// set selected date in session
			request.getSession().setAttribute("releaseDay", releaseForm.getDay());
			request.getSession().setAttribute("releaseMonth", releaseForm.getMonth());
			request.getSession().setAttribute("releaseYear", releaseForm.getYear());
			

			java.util.Date releaseDate = (java.util.Date) request.getSession().getAttribute("releaseDate");
			java.sql.Date sqlReleaseDate = new java.sql.Date(releaseDate.getTime());
			
			// set status in timereports
			List<Timereport> timereports = timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeecontract.getId(), sqlReleaseDate);		
			for (Timereport timereport : timereports) {
				timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_COMMITED);
			}
			releaseDateFromContract = releaseDate;
			
			request.getSession().setAttribute("days", getDayList(releaseDateFromContract));
			
			// store new release date in employee contract
			
			 
			
			
		}
		
		if ((request.getParameter("task") != null)
				&& ((request.getParameter("task").equals("refreshDate")))) {
			// set selected date in session
			request.getSession().setAttribute("releaseDay", releaseForm.getDay());
			request.getSession().setAttribute("releaseMonth", releaseForm.getMonth());
			request.getSession().setAttribute("releaseYear", releaseForm.getYear());
			
			Date selectedDate = th.getDateFormStrings("01", releaseForm.getMonth(), releaseForm.getDay(), false);
				
			request.getSession().setAttribute("days", getDayList(selectedDate));
						
		}
		
		if (request.getParameter("task") == null) {
			String[] dateArray = th.getDateAsStringArray(releaseDateFromContract);
			
			// set form entries
			releaseForm.setDay(dateArray[0]);
			releaseForm.setMonth(dateArray[1]);
			releaseForm.setYear(dateArray[2]);
			
			request.getSession().setAttribute("releaseDay", dateArray[0]);
			request.getSession().setAttribute("releaseMonth", dateArray[1]);
			request.getSession().setAttribute("releaseYear", dateArray[2]);
			
			request.getSession().setAttribute("days", getDayList(releaseDateFromContract));
		}

		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
		
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
	private ActionMessages validateFormData(HttpServletRequest request, ShowReleaseForm releaseForm) {

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
		
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), new Date());
		
		if(date.before(employeecontract.getValidFrom()) || date.after(employeecontract.getValidUntil())) {
			errors.add("releasedate", new ActionMessage("form.release.error.date.invalid.foremployeecontract"));
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
