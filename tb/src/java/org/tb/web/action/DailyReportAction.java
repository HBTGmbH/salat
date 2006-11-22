package org.tb.web.action;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Vacation;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.OvertimeDAO;
import org.tb.persistence.PublicholidayDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.VacationDAO;

public abstract class DailyReportAction extends LoginRequiredAction {

	
	private OvertimeDAO overtimeDAO;
	
	public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
		this.overtimeDAO = overtimeDAO;
	}
	
	protected Date getSelectedDateFromRequest(HttpServletRequest request) {
		int day = new Integer((String) request.getSession().getAttribute("currentDay"));
		String monthString = (String) request.getSession().getAttribute("currentMonth");
		int year = new Integer((String) request.getSession().getAttribute("currentYear"));
		int month = 0;
		
		if (GlobalConstants.MONTH_SHORTFORM_JANUARY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_JANUARY;			
		} else if (GlobalConstants.MONTH_SHORTFORM_FEBRURAY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_FEBRURAY;
		} else if (GlobalConstants.MONTH_SHORTFORM_MARCH.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_MARCH;
		} else if (GlobalConstants.MONTH_SHORTFORM_APRIL.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_APRIL;
		} else if (GlobalConstants.MONTH_SHORTFORM_MAY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_MAY;
		} else if (GlobalConstants.MONTH_SHORTFORM_JUNE.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_JUNE;
		} else if (GlobalConstants.MONTH_SHORTFORM_JULY.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_JULY;
		} else if (GlobalConstants.MONTH_SHORTFORM_AUGUST.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_AUGUST;
		} else if (GlobalConstants.MONTH_SHORTFORM_SEPTEMBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_SEPTEMBER;
		} else if (GlobalConstants.MONTH_SHORTFORM_OCTOBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_OCTOBER;
		} else if (GlobalConstants.MONTH_SHORTFORM_NOVEMBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_NOVEMBER;
		} else if (GlobalConstants.MONTH_SHORTFORM_DECEMBER.equals(monthString)) {
			month = GlobalConstants.MONTH_INTVALUE_DECEMBER;
		}
		
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date selectedDate;
		try {
			selectedDate = simpleDateFormat.parse(year+"-"+month+"-"+day);
		} catch (ParseException e) {
			//no date could be constructed - use current date instead
			selectedDate = new Date();
		}
		return selectedDate;
	}

	/**
	 * Calculates the overtime and vaction and sets the attributes in the session.
	 * @param request
	 * @param selectedYear
	 * @param vacationDAO
	 * @param employeecontract
	 * @param employeeorderDAO
	 * @param publicholidayDAO
	 * @param timereportDAO
	 */
	public void refreshVacationAndOvertime(HttpServletRequest request, int selectedYear, VacationDAO vacationDAO, Employeecontract employeecontract, EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, TimereportDAO timereportDAO) {
		TimereportHelper th = new TimereportHelper();
		int[] overtime = th.calculateOvertime(employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO);
		Vacation vacation = vacationDAO.getVacationByYearAndEmployeecontract(employeecontract.getId(), selectedYear);
		int totalVacation = vacation.getEntitlement();
		int usedVacation = vacation.getUsed();
		int overtimeHours = overtime[0];
		int overtimeMinutes = overtime[1];
		String overtimeString = overtimeHours+":";
		if (overtimeMinutes < 0) {
			request.getSession().setAttribute("overtimeIsNegative", true);
			overtimeMinutes *= -1;
		} else if (overtimeHours < 0){ 
			request.getSession().setAttribute("overtimeIsNegative", true);
		} else {
			request.getSession().setAttribute("overtimeIsNegative", false);
		}
		if (overtimeMinutes < 10) {
			overtimeString += 0;
		}
		overtimeString += overtimeMinutes;
		request.getSession().setAttribute("vacationtotal", totalVacation);
		request.getSession().setAttribute("vacationused", usedVacation);
		request.getSession().setAttribute("overtime", overtimeString);
		
	}
	
}
