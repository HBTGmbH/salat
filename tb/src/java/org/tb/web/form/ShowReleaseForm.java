package org.tb.web.form;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employeecontract;
import org.tb.helper.TimereportHelper;

public class ShowReleaseForm extends ActionForm {
	
	private String day;
	private String month;
	private String year;
	
	private String acceptanceDay;
	private String acceptanceMonth;
	private String acceptanceYear;
	
	private String reopenDay;
	private String reopenMonth;
	private String reopenYear;
	
	Long employeeContractId;
	
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
	public String getAcceptanceDay() {
		return acceptanceDay;
	}
	public void setAcceptanceDay(String acceptanceDay) {
		this.acceptanceDay = acceptanceDay;
	}
	public String getAcceptanceMonth() {
		return acceptanceMonth;
	}
	public void setAcceptanceMonth(String acceptanceMonth) {
		this.acceptanceMonth = acceptanceMonth;
	}
	public String getAcceptanceYear() {
		return acceptanceYear;
	}
	public void setAcceptanceYear(String acceptanceYear) {
		this.acceptanceYear = acceptanceYear;
	}
	public Long getEmployeeContractId() {
		return employeeContractId;
	}
	public void setEmployeeContractId(Long employeeContractId) {
		this.employeeContractId = employeeContractId;
	}
	public String getReopenDay() {
		return reopenDay;
	}
	public void setReopenDay(String reopenDay) {
		this.reopenDay = reopenDay;
	}
	public String getReopenMonth() {
		return reopenMonth;
	}
	public void setReopenMonth(String reopenMonth) {
		this.reopenMonth = reopenMonth;
	}
	public String getReopenYear() {
		return reopenYear;
	}
	public void setReopenYear(String reopenYear) {
		this.reopenYear = reopenYear;
	}
	
	
	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {
		TimereportHelper th = new TimereportHelper();
		Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
		Date releaseDate = new Date();
		Date acceptanceDate = new Date();
		if (employeecontract != null) {
//			employeeContractId = employeecontract.getId();
			releaseDate = employeecontract.getReportReleaseDate();
			acceptanceDate = employeecontract.getReportAcceptanceDate();
			if (releaseDate == null) {
				releaseDate = employeecontract.getValidFrom();				
			}
			if (acceptanceDate == null) {
				acceptanceDate = employeecontract.getValidFrom();
			}
		}
		
		String[] releaseDateArray = th.getDateAsStringArray(releaseDate);
		day = releaseDateArray[0];
		month = releaseDateArray[1];
		year = releaseDateArray[2];
		String[] acceptanceDateArray = th.getDateAsStringArray(acceptanceDate);
		acceptanceDay = acceptanceDateArray[0];
		acceptanceMonth = acceptanceDateArray[1];
		acceptanceYear = acceptanceDateArray[2];
		String[] reopenDateArray = th.getDateAsStringArray(releaseDate);
		reopenDay = reopenDateArray[0];
		reopenMonth = reopenDateArray[1];
		reopenYear = reopenDateArray[2];
//		request.getSession().setAttribute("releaseDay", day);
//		request.getSession().setAttribute("releaseMonth", month);
//		request.getSession().setAttribute("releaseYear", year);
		
		
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		
		// actually, no checks here
		return errors;
	}

}
