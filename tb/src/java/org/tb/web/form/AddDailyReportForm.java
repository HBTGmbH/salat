package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import java.util.Date;
import java.util.Calendar;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.util.DateUtils;

/**
 * Form for adding a timereport
 * 
 * @author oda
 *
 */
public class AddDailyReportForm extends ActionForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L; // -1101951628777959966L;
	private long id;
//	private String employeename;
	private String referenceday;
	private String sortOfReport;
	private String comment;
	private String order;
	private String suborder;
	private String status;
	private Double hours;
	private Double costs;
	private int selectedHourBegin;
	private int selectedMinuteBegin;
	private int selectedHourEnd;
	private int selectedMinuteEnd;
	private int selectedHourDuration;
	private int selectedMinuteDuration;
	
	private long orderId;
	private long suborderSignId;
	private long suborderDescriptionId;
//	private long employeecontractId;
//	private Long employeeId;
	private Long employeeContractId;
	
	private int numberOfSerialDays;
	
	private String action;
	
	public AddDailyReportForm() {
		SimpleDateFormat format = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
		setReferenceday(format.format(new Date()));
	}
	
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getOrderId() {
		return orderId;
	}

	public void setOrderId(long orderId) {
		this.orderId = orderId;
	}

	public String getSuborder() {
		return suborder;
	}

	public void setSuborder(String suborder) {
		this.suborder = suborder;
	}

	public long getSuborderDescriptionId() {
		return suborderDescriptionId;
	}

	public void setSuborderDescriptionId(long suborderDescriptionId) {
		this.suborderDescriptionId = suborderDescriptionId;
	}

	public long getSuborderSignId() {
		return suborderSignId;
	}

	public void setSuborderSignId(long suborderSignId) {
		this.suborderSignId = suborderSignId;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}
	
//	public String getEmployeename() {
//		return employeename;
//	}
//
//	public void setEmployeename(String employeename) {
//		this.employeename = employeename;
//	}
	
	public String getSortOfReport() {
		return sortOfReport;
	}

	public void setSortOfReport(String sortOfReport) {
		this.sortOfReport = sortOfReport;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public Double getCosts() {
		return costs;
	}

	public void setCosts(Double costs) {
		this.costs = costs;
	}

	public Double getHours() {
		return DateUtils.calculateTime(this.selectedHourBegin,
									this.selectedMinuteBegin,
									this.selectedHourEnd,
									this.selectedMinuteEnd);
		//return hours;
	}

	public void setHours(Double hours) {
		this.hours = hours;
	}
	
	public int getSelectedHourBegin() {
		return selectedHourBegin;
	}

	public void setSelectedHourBegin(int selectedHourBegin) {
		this.selectedHourBegin = selectedHourBegin;
	}

	public int getSelectedHourEnd() {
		return selectedHourEnd;
	}

	public void setSelectedHourEnd(int selectedHourEnd) {
		this.selectedHourEnd = selectedHourEnd;
	}

	public int getSelectedMinuteBegin() {
		return selectedMinuteBegin;
	}

	public void setSelectedMinuteBegin(int selectedMinuteBegin) {
		this.selectedMinuteBegin = selectedMinuteBegin;
	}

	public int getSelectedMinuteEnd() {
		return selectedMinuteEnd;
	}

	public void setSelectedMinuteEnd(int selectedMinuteEnd) {
		this.selectedMinuteEnd = selectedMinuteEnd;
	}
	
	public int getSelectedHourDuration() {
		return selectedHourDuration;
	}

	public void setSelectedHourDuration(int selectedHourDuration) {
		this.selectedHourDuration = selectedHourDuration;
	}

	public int getSelectedMinuteDuration() {
		return selectedMinuteDuration;
	}

	public void setSelectedMinuteDuration(int selectedMinuteDuration) {
		this.selectedMinuteDuration = selectedMinuteDuration;
	}

	public Double getHoursDuration() {
		return hours;
	}
	
	public void setHoursDuration(Double hours) {
		this.hours = hours;
	}
	
	public String getOrder() {
		return order;
	}

	public void setOrder(String order) {
		this.order = order;
	}

	public String getReferenceday() {
		return referenceday;
	}

	public void setReferenceday(String referenceday) {
		this.referenceday = referenceday;
	}

	
	
//	public long getEmployeecontractId() {
//		return employeecontractId;
//	}
//
//	public void setEmployeecontractId(long employeecontractId) {
//		this.employeecontractId = employeecontractId;
//	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
		
	public int getNumberOfSerialDays() {
		return numberOfSerialDays;
	}

	public void setNumberOfSerialDays(int numberOfSerialDays) {
		this.numberOfSerialDays = numberOfSerialDays;
	}
	
//	public long getEmployeeId() {
//		return employeeId;
//	}
//
//	public void setEmployeeId(long employeeId) {
//		this.employeeId = employeeId;
//	}

	/**
	 * @return the employeeContractId
	 */
	public Long getEmployeeContractId() {
		return employeeContractId;
	}

	/**
	 * @param employeeContractId the employeeContractId to set
	 */
	public void setEmployeeContractId(Long employeeContractId) {
		this.employeeContractId = employeeContractId;
	}
	
	
	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {
//		Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
		try {
			Employeecontract loginEmployeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
			employeeContractId = loginEmployeecontract.getId();
		}
		catch (Exception e) {
			mapping.findForward("login");
		}
//		employeename = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
		comment = "";
		order = "";
		suborder="";
		status = "";
		sortOfReport = "W";
//		selectedHourBegin = GlobalConstants.BEGINHOUR;
//		selectedMinuteBegin = GlobalConstants.BEGINMINUTE;
//		selectedHourEnd = GlobalConstants.ENDHOUR;
//		selectedMinuteEnd = GlobalConstants.ENDMINUTE;
		selectedHourDuration = 0;
		selectedMinuteDuration = 0;
		referenceday = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
		hours = 8.0;
		DecimalFormat df = new DecimalFormat( "0,00" );
		costs = 0.0; 
		costs = Double.valueOf(df.format(costs)); 
		System.out.println( df.format(costs) );       
		numberOfSerialDays = 0;
//		employeecontractId = (Long) request.getSession().getAttribute("loginEmployeeContractId");
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();

		// actually, no checks here
		return errors;
	}

	

}
