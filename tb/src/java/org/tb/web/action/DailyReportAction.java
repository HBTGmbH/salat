package org.tb.web.action;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Workingday;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.DateUtils;

public abstract class DailyReportAction extends LoginRequiredAction {

	
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

}
