package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for showing timereports in 'daily' display.
 * 
 * @author oda
 *
 */
public class ShowDailyReportForm extends ActionForm {

	private String day;
	private String month;
	private String year;
	private String employeename;
	
	private String comment;
	private String order;
	private String suborder;
	private String status;
	private Double costs;
	private int selectedHourBegin;
	private int selectedMinuteBegin;
	private int selectedHourEnd;
	private int selectedMinuteEnd;
	
	private long trOrderId;
	private long orderId;
	private long trSuborderId;
	
	
	
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

	public String getEmployeename() {
		return employeename;
	}

	public void setEmployeename(String employeename) {
		this.employeename = employeename;
	}
	
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

	@Override
	public void reset(ActionMapping mapping, HttpServletRequest request) {
		month = null;
		employeename = null;
	}

	@Override
	public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
		ActionErrors errors = new ActionErrors();
		
		// actually, no checks here
		return errors;
	}

}
