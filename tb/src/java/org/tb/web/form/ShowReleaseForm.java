package org.tb.web.form;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.helper.TimereportHelper;

public class ShowReleaseForm extends ActionForm {
	
	private String day;
	private String month;
	private String year;
	
	
	public String getDay() {
		return day;
	}
	public void setDay(String day) {
		this.day = day;
	}
	public String getMonth() {
		return month;
	}
	public void setMonth(String month) {
		this.month = month;
	}
	public String getYear() {
		return year;
	}
	public void setYear(String year) {
		this.year = year;
	}
	
	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {
		TimereportHelper th = new TimereportHelper();
		Date date = new Date();
		String[] dateArray = th.getDateAsStringArray(date);
		day = dateArray[0];
		month = dateArray[1];
		year = dateArray[2];
		request.getSession().setAttribute("releaseDay", day);
		request.getSession().setAttribute("releaseMonth", month);
		request.getSession().setAttribute("releaseYear", year);
		
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		
		// actually, no checks here
		return errors;
	}

}
