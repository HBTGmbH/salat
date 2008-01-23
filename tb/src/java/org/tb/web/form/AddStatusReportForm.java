package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

public class AddStatusReportForm extends ActionForm {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L; // 2991618984884697261L;
	private Long customerOrderId;
	private Long senderId;
	private Long recipientId;
	
	private Byte overallStatus;
	
	private Byte sort;
	private Byte phase;
	private String validFrom;
	private String validUntil;
	private String allocator;
	private Byte trend;
	private Byte trendstatus;
	private String needforaction_text;
	private String needforaction_source;
	private Byte needforaction_status;
	private String aim_text;
	private String aim_source;
	private String aim_action;
	private Byte aim_status;
	private String budget_resources_date_text;
	private String budget_resources_date_source;
	private String budget_resources_date_action;
	private Byte budget_resources_date_status;
	private String riskmonitoring_text;
	private String riskmonitoring_source;
	private String riskmonitoring_action;
	private Byte riskmonitoring_status;
	private String changedirective_text;
	private String changedirective_source;
	private String changedirective_action;
	private Byte changedirective_status;
	private String communication_text;
	private String communication_source;
	private String communication_action;
	private Byte communication_status;
	private String improvement_text;
	private String improvement_source;
	private String improvement_action;
	private Byte improvement_status;
	private String miscellaneous_text;
	private String miscellaneous_source;
	private String miscellaneous_action;
	private Byte miscellaneous_status;
	private String notes;
	
	
	/**
	 * @return the aim_action
	 */
	public String getAim_action() {
		return aim_action;
	}
	/**
	 * @param aim_action the aim_action to set
	 */
	public void setAim_action(String aim_action) {
		this.aim_action = aim_action;
	}
	/**
	 * @return the aim_source
	 */
	public String getAim_source() {
		return aim_source;
	}
	/**
	 * @param aim_source the aim_source to set
	 */
	public void setAim_source(String aim_source) {
		this.aim_source = aim_source;
	}
	/**
	 * @return the aim_status
	 */
	public Byte getAim_status() {
		return aim_status;
	}
	/**
	 * @param aim_status the aim_status to set
	 */
	public void setAim_status(Byte aim_status) {
		this.aim_status = aim_status;
	}
	/**
	 * @return the aim_text
	 */
	public String getAim_text() {
		return aim_text;
	}
	/**
	 * @param aim_text the aim_text to set
	 */
	public void setAim_text(String aim_text) {
		this.aim_text = aim_text;
	}
	/**
	 * @return the allocator
	 */
	public String getAllocator() {
		return allocator;
	}
	/**
	 * @param allocator the allocator to set
	 */
	public void setAllocator(String allocator) {
		this.allocator = allocator;
	}
	/**
	 * @return the budget_resources_date_action
	 */
	public String getBudget_resources_date_action() {
		return budget_resources_date_action;
	}
	/**
	 * @param budget_resources_date_action the budget_resources_date_action to set
	 */
	public void setBudget_resources_date_action(String budget_resources_date_action) {
		this.budget_resources_date_action = budget_resources_date_action;
	}
	/**
	 * @return the budget_resources_date_source
	 */
	public String getBudget_resources_date_source() {
		return budget_resources_date_source;
	}
	/**
	 * @param budget_resources_date_source the budget_resources_date_source to set
	 */
	public void setBudget_resources_date_source(String budget_resources_date_source) {
		this.budget_resources_date_source = budget_resources_date_source;
	}
	/**
	 * @return the budget_resources_date_status
	 */
	public Byte getBudget_resources_date_status() {
		return budget_resources_date_status;
	}
	/**
	 * @param budget_resources_date_status the budget_resources_date_status to set
	 */
	public void setBudget_resources_date_status(Byte budget_resources_date_status) {
		this.budget_resources_date_status = budget_resources_date_status;
	}
	/**
	 * @return the budget_resources_date_text
	 */
	public String getBudget_resources_date_text() {
		return budget_resources_date_text;
	}
	/**
	 * @param budget_resources_date_text the budget_resources_date_text to set
	 */
	public void setBudget_resources_date_text(String budget_resources_date_text) {
		this.budget_resources_date_text = budget_resources_date_text;
	}
	/**
	 * @return the changedirective_action
	 */
	public String getChangedirective_action() {
		return changedirective_action;
	}
	/**
	 * @param changedirective_action the changedirective_action to set
	 */
	public void setChangedirective_action(String changedirective_action) {
		this.changedirective_action = changedirective_action;
	}
	/**
	 * @return the changedirective_source
	 */
	public String getChangedirective_source() {
		return changedirective_source;
	}
	/**
	 * @param changedirective_source the changedirective_source to set
	 */
	public void setChangedirective_source(String changedirective_source) {
		this.changedirective_source = changedirective_source;
	}
	/**
	 * @return the changedirective_status
	 */
	public Byte getChangedirective_status() {
		return changedirective_status;
	}
	/**
	 * @param changedirective_status the changedirective_status to set
	 */
	public void setChangedirective_status(Byte changedirective_status) {
		this.changedirective_status = changedirective_status;
	}
	/**
	 * @return the changedirective_text
	 */
	public String getChangedirective_text() {
		return changedirective_text;
	}
	/**
	 * @param changedirective_text the changedirective_text to set
	 */
	public void setChangedirective_text(String changedirective_text) {
		this.changedirective_text = changedirective_text;
	}
	/**
	 * @return the communication_action
	 */
	public String getCommunication_action() {
		return communication_action;
	}
	/**
	 * @param communication_action the communication_action to set
	 */
	public void setCommunication_action(String communication_action) {
		this.communication_action = communication_action;
	}
	/**
	 * @return the communication_source
	 */
	public String getCommunication_source() {
		return communication_source;
	}
	/**
	 * @param communication_source the communication_source to set
	 */
	public void setCommunication_source(String communication_source) {
		this.communication_source = communication_source;
	}
	/**
	 * @return the communication_status
	 */
	public Byte getCommunication_status() {
		return communication_status;
	}
	/**
	 * @param communication_status the communication_status to set
	 */
	public void setCommunication_status(Byte communication_status) {
		this.communication_status = communication_status;
	}
	/**
	 * @return the communication_text
	 */
	public String getCommunication_text() {
		return communication_text;
	}
	/**
	 * @param communication_text the communication_text to set
	 */
	public void setCommunication_text(String communication_text) {
		this.communication_text = communication_text;
	}
	/**
	 * @return the customerOrderId
	 */
	public Long getCustomerOrderId() {
		return customerOrderId;
	}
	/**
	 * @param customerOrderId the customerOrderId to set
	 */
	public void setCustomerOrderId(Long customerOrderId) {
		this.customerOrderId = customerOrderId;
	}
	/**
	 * @return the fromDateString
	 */
	public String getValidFrom() {
		return validFrom;
	}
	/**
	 * @param fromDateString the fromDateString to set
	 */
	public void setValidFrom(String fromDateString) {
		this.validFrom = fromDateString;
	}
	/**
	 * @return the improvement_action
	 */
	public String getImprovement_action() {
		return improvement_action;
	}
	/**
	 * @param improvement_action the improvement_action to set
	 */
	public void setImprovement_action(String improvement_action) {
		this.improvement_action = improvement_action;
	}
	/**
	 * @return the improvement_source
	 */
	public String getImprovement_source() {
		return improvement_source;
	}
	/**
	 * @param improvement_source the improvement_source to set
	 */
	public void setImprovement_source(String improvement_source) {
		this.improvement_source = improvement_source;
	}
	/**
	 * @return the improvement_status
	 */
	public Byte getImprovement_status() {
		return improvement_status;
	}
	/**
	 * @param improvement_status the improvement_status to set
	 */
	public void setImprovement_status(Byte improvement_status) {
		this.improvement_status = improvement_status;
	}
	/**
	 * @return the improvement_text
	 */
	public String getImprovement_text() {
		return improvement_text;
	}
	/**
	 * @param improvement_text the improvement_text to set
	 */
	public void setImprovement_text(String improvement_text) {
		this.improvement_text = improvement_text;
	}
	/**
	 * @return the miscellaneous_action
	 */
	public String getMiscellaneous_action() {
		return miscellaneous_action;
	}
	/**
	 * @param miscellaneous_action the miscellaneous_action to set
	 */
	public void setMiscellaneous_action(String miscellaneous_action) {
		this.miscellaneous_action = miscellaneous_action;
	}
	/**
	 * @return the miscellaneous_source
	 */
	public String getMiscellaneous_source() {
		return miscellaneous_source;
	}
	/**
	 * @param miscellaneous_source the miscellaneous_source to set
	 */
	public void setMiscellaneous_source(String miscellaneous_source) {
		this.miscellaneous_source = miscellaneous_source;
	}
	/**
	 * @return the miscellaneous_status
	 */
	public Byte getMiscellaneous_status() {
		return miscellaneous_status;
	}
	/**
	 * @param miscellaneous_status the miscellaneous_status to set
	 */
	public void setMiscellaneous_status(Byte miscellaneous_status) {
		this.miscellaneous_status = miscellaneous_status;
	}
	/**
	 * @return the miscellaneous_text
	 */
	public String getMiscellaneous_text() {
		return miscellaneous_text;
	}
	/**
	 * @param miscellaneous_text the miscellaneous_text to set
	 */
	public void setMiscellaneous_text(String miscellaneous_text) {
		this.miscellaneous_text = miscellaneous_text;
	}
	/**
	 * @return the needforaction_source
	 */
	public String getNeedforaction_source() {
		return needforaction_source;
	}
	/**
	 * @param needforaction_source the needforaction_source to set
	 */
	public void setNeedforaction_source(String needforaction_source) {
		this.needforaction_source = needforaction_source;
	}
	/**
	 * @return the needforaction_status
	 */
	public Byte getNeedforaction_status() {
		return needforaction_status;
	}
	/**
	 * @param needforaction_status the needforaction_status to set
	 */
	public void setNeedforaction_status(Byte needforaction_status) {
		this.needforaction_status = needforaction_status;
	}
	/**
	 * @return the needforaction_text
	 */
	public String getNeedforaction_text() {
		return needforaction_text;
	}
	/**
	 * @param needforaction_text the needforaction_text to set
	 */
	public void setNeedforaction_text(String needforaction_text) {
		this.needforaction_text = needforaction_text;
	}
	/**
	 * @return the notes
	 */
	public String getNotes() {
		return notes;
	}
	/**
	 * @param notes the notes to set
	 */
	public void setNotes(String notes) {
		this.notes = notes;
	}
	/**
	 * @return the overallStatus
	 */
	public Byte getOverallStatus() {
		return overallStatus;
	}
	/**
	 * @param overallStatus the overallStatus to set
	 */
	public void setOverallStatus(Byte overallStatus) {
		this.overallStatus = overallStatus;
	}
	/**
	 * @return the phase
	 */
	public Byte getPhase() {
		return phase;
	}
	/**
	 * @param phase the phase to set
	 */
	public void setPhase(Byte phase) {
		this.phase = phase;
	}
	/**
	 * @return the recipientId
	 */
	public Long getRecipientId() {
		return recipientId;
	}
	/**
	 * @param recipientId the recipientId to set
	 */
	public void setRecipientId(Long recipientId) {
		this.recipientId = recipientId;
	}
	/**
	 * @return the riskmonitoring_action
	 */
	public String getRiskmonitoring_action() {
		return riskmonitoring_action;
	}
	/**
	 * @param riskmonitoring_action the riskmonitoring_action to set
	 */
	public void setRiskmonitoring_action(String riskmonitoring_action) {
		this.riskmonitoring_action = riskmonitoring_action;
	}
	/**
	 * @return the riskmonitoring_source
	 */
	public String getRiskmonitoring_source() {
		return riskmonitoring_source;
	}
	/**
	 * @param riskmonitoring_source the riskmonitoring_source to set
	 */
	public void setRiskmonitoring_source(String riskmonitoring_source) {
		this.riskmonitoring_source = riskmonitoring_source;
	}
	/**
	 * @return the riskmonitoring_status
	 */
	public Byte getRiskmonitoring_status() {
		return riskmonitoring_status;
	}
	/**
	 * @param riskmonitoring_status the riskmonitoring_status to set
	 */
	public void setRiskmonitoring_status(Byte riskmonitoring_status) {
		this.riskmonitoring_status = riskmonitoring_status;
	}
	/**
	 * @return the riskmonitoring_text
	 */
	public String getRiskmonitoring_text() {
		return riskmonitoring_text;
	}
	/**
	 * @param riskmonitoring_text the riskmonitoring_text to set
	 */
	public void setRiskmonitoring_text(String riskmonitoring_text) {
		this.riskmonitoring_text = riskmonitoring_text;
	}
	/**
	 * @return the senderId
	 */
	public Long getSenderId() {
		return senderId;
	}
	/**
	 * @param senderId the senderId to set
	 */
	public void setSenderId(Long senderId) {
		this.senderId = senderId;
	}
	/**
	 * @return the sort
	 */
	public Byte getSort() {
		return sort;
	}
	/**
	 * @param sort the sort to set
	 */
	public void setSort(Byte sort) {
		this.sort = sort;
	}
	/**
	 * @return the trend
	 */
	public Byte getTrend() {
		return trend;
	}
	/**
	 * @param trend the trend to set
	 */
	public void setTrend(Byte trend) {
		this.trend = trend;
	}
	/**
	 * @return the trendstatus
	 */
	public Byte getTrendstatus() {
		return trendstatus;
	}
	/**
	 * @param trendstatus the trendstatus to set
	 */
	public void setTrendstatus(Byte trendstatus) {
		this.trendstatus = trendstatus;
	}
	/**
	 * @return the untilDateString
	 */
	public String getValidUntil() {
		return validUntil;
	}
	/**
	 * @param untilDateString the untilDateString to set
	 */
	public void setValidUntil(String untilDateString) {
		this.validUntil = untilDateString;
	}
	
	
	
	
	/* (non-Javadoc)
	 * @see org.apache.struts.action.ActionForm#reset(org.apache.struts.action.ActionMapping, javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public void reset(ActionMapping arg0, HttpServletRequest arg1) {
		// do nothing
	}
	/* (non-Javadoc)
	 * @see org.apache.struts.action.ActionForm#validate(org.apache.struts.action.ActionMapping, javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public ActionErrors validate(ActionMapping arg0, HttpServletRequest arg1) {
		// nothing to do here
		return super.validate(arg0, arg1);
	}
	
	
}
