package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employeecontract;

/**
 * Form for showing timereports in 'daily' display.
 * 
 * @author oda
 *
 */
public class ShowDailyReportForm extends ActionForm {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L; // 3595871670101021846L;
	private String day;
	private String month;
	private String year;
	
	private String lastday;
	private String lastmonth;
	private String lastyear;
	
//	private long employeeId;
	private long employeeContractId;
	
	private String comment;
	private String order;
	private String suborder;
	private String status;
	private Double costs;
	private int selectedHourBegin;
	private int selectedMinuteBegin;
	private int selectedHourEnd;
	private int selectedMinuteEnd;
	private int selectedWorkHourBegin;
	private int selectedWorkMinuteBegin;
	private int selectedBreakHour;
	private int selectedBreakMinute;
	
	private long trOrderId;
	private long orderId;
	private long suborderId;
	private long trSuborderId;
	
	private String view;
	
	private Boolean avoidRefresh;
	
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

//	public long getEmployeeId() {
//		return employeeId;
//	}
//
//	public void setEmployeeId(long employeeId) {
//		this.employeeId = employeeId;
//	}
	
	public String getOrder() {
		return order;
	}

	public void setOrder(String order) {
		this.order = order;
	}

	public long getOrderId() {
		return orderId;
	}

	public void setOrderId(long orderId) {
		this.orderId = orderId;
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
	
	

	/**
	 * @return the selectedBreakHour
	 */
	public int getSelectedBreakHour() {
		return selectedBreakHour;
	}

	/**
	 * @param selectedBreakHour the selectedBreakHour to set
	 */
	public void setSelectedBreakHour(int selectedBreakHour) {
		this.selectedBreakHour = selectedBreakHour;
	}

	/**
	 * @return the selectedBreakMinute
	 */
	public int getSelectedBreakMinute() {
		return selectedBreakMinute;
	}

	/**
	 * @param selectedBreakMinute the selectedBreakMinute to set
	 */
	public void setSelectedBreakMinute(int selectedBreakMinute) {
		this.selectedBreakMinute = selectedBreakMinute;
	}

	/**
	 * @return the selectedWorkHourBegin
	 */
	public int getSelectedWorkHourBegin() {
		return selectedWorkHourBegin;
	}

	/**
	 * @param selectedWorkHourBegin the selectedWorkHourBegin to set
	 */
	public void setSelectedWorkHourBegin(int selectedWorkHourBegin) {
		this.selectedWorkHourBegin = selectedWorkHourBegin;
	}

	/**
	 * @return the selectedWorkMinuteBegin
	 */
	public int getSelectedWorkMinuteBegin() {
		return selectedWorkMinuteBegin;
	}

	/**
	 * @param selectedWorkMinuteBegin the selectedWorkMinuteBegin to set
	 */
	public void setSelectedWorkMinuteBegin(int selectedWorkMinuteBegin) {
		this.selectedWorkMinuteBegin = selectedWorkMinuteBegin;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getSuborder() {
		return suborder;
	}

	public void setSuborder(String suborder) {
		this.suborder = suborder;
	}

	public long getTrSuborderId() {
		return trSuborderId;
	}

	public void setTrSuborderId(long trSuborderId) {
		this.trSuborderId = trSuborderId;
	}
	
	public long getTrOrderId() {
		return trOrderId;
	}

	public void setTrOrderId(long trOrderId) {
		this.trOrderId = trOrderId;
	}
	
	

	/**
	 * @return the view
	 */
	public String getView() {
		return view;
	}

	/**
	 * @param view the view to set
	 */
	public void setView(String view) {
		this.view = view;
	}
	
	

	/**
	 * @return the lastday
	 */
	public String getLastday() {
		return lastday;
	}

	/**
	 * @param lastday the lastday to set
	 */
	public void setLastday(String lastday) {
		this.lastday = lastday;
	}

	/**
	 * @return the lastmonth
	 */
	public String getLastmonth() {
		return lastmonth;
	}

	/**
	 * @param lastmonth the lastmonth to set
	 */
	public void setLastmonth(String lastmonth) {
		this.lastmonth = lastmonth;
	}

	/**
	 * @return the lastyear
	 */
	public String getLastyear() {
		return lastyear;
	}

	/**
	 * @param lastyear the lastyear to set
	 */
	public void setLastyear(String lastyear) {
		this.lastyear = lastyear;
	}
	

	/**
	 * @return the employeeContractId
	 */
	public long getEmployeeContractId() {
		return employeeContractId;
	}

	/**
	 * @param employeeContractId the employeeContractId to set
	 */
	public void setEmployeeContractId(long employeeContractId) {
		this.employeeContractId = employeeContractId;
	}

	/**
	 * @return the suborderId
	 */
	public long getSuborderId() {
		return suborderId;
	}

	/**
	 * @param suborderId the suborderId to set
	 */
	public void setSuborderId(long suborderId) {
		this.suborderId = suborderId;
	}

	/**
	 * @return the avoidRefresh
	 */
	public Boolean getAvoidRefresh() {
		return avoidRefresh;
	}

	/**
	 * @param avoidRefresh the avoidRefresh to set
	 */
	public void setAvoidRefresh(Boolean avoidRefresh) {
		this.avoidRefresh = avoidRefresh;
	}

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {
//		if (null != request.getSession().getAttribute("currentEmployeeId")) {
//			employeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
//		} else {
//			employeeId = -1;
//		}
		
		Employeecontract employeecontract;
		if (null != request.getSession().getAttribute("currentEmployeeContract")) {
			employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
			employeeContractId = employeecontract.getId();
		} else {
			employeeContractId = -1;
		}
		
		avoidRefresh = false;
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		
		// actually, no checks here
		return errors;
	}

}
