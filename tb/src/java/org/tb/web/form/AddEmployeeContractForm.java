package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.util.DateUtils;

/**
 * Form for adding an employee contract
 * 
 * @author oda
 *
 */
public class AddEmployeeContractForm extends ActionForm {

	private long id;
	private String taskdescription;
	private String validFrom;
	private String validUntil;
	private Boolean freelancer;
	private Double dailyworkingtime;
	private Integer yearlyvacation;
	private String employeename;
	
	private long employeeId;
	
	private String action;

	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}
	
	public Double getDailyworkingtime() {
		return dailyworkingtime;
	}

	public void setDailyworkingtime(Double dailyworkingtime) {
		this.dailyworkingtime = dailyworkingtime;
	}
	
	public Integer getYearlyvacation() {
		return yearlyvacation;
	}

	public void setYearlyvacation(Integer yearlyvacation) {
		this.yearlyvacation = yearlyvacation;
	}

	public long getEmployeeId() {
		return employeeId;
	}

	public void setEmployeeId(long employeeId) {
		this.employeeId = employeeId;
	}

	public Boolean getFreelancer() {
		return freelancer;
	}

	public void setFreelancer(Boolean freelancer) {
		this.freelancer = freelancer;
	}

	public String getTaskdescription() {
		return taskdescription;
	}

	public void setTaskdescription(String taskdescription) {
		this.taskdescription = taskdescription;
	}

	public String getValidFrom() {
		return validFrom;
	}

	public void setValidFrom(String validFrom) {
		this.validFrom = validFrom;
	}

	public String getValidUntil() {
		return validUntil;
	}

	public void setValidUntil(String validUntil) {
		this.validUntil = validUntil;
	}

	public String getEmployeename() {
		return employeename;
	}

	public void setEmployeename(String employeename) {
		this.employeename = employeename;
	}

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {	
		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		employeename = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
		taskdescription = "";
		validFrom = DateUtils.getCurrentYearString() + "-01-01";
		validUntil = DateUtils.getCurrentYearString() + "-12-31";
		freelancer = new Boolean(Boolean.FALSE);
		dailyworkingtime = 8.0;
		yearlyvacation = 30;
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();

		// actually, no checks here
		return errors;
	}

}
