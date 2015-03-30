package org.tb.web.form;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

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
    
    private static final long serialVersionUID = 1L; // -1101951628777959966L;
    private long id;
    private String referenceday;
    private String sortOfReport;
    private String comment;
    private String order;
    private String suborder;
    private String status;
    private Double hours;
    private Double costs;
    private Boolean training;
    private int selectedHourBegin;
    private int selectedMinuteBegin;
    private int selectedHourEnd;
    private int selectedMinuteEnd;
    private int selectedHourDuration;
    private int selectedMinuteDuration;
    private long orderId;
    private long suborderSignId;
    private long suborderDescriptionId;
    private Long employeeContractId;
    private String jiraTicketKey;
    private String newJiraTicketKey;
    
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
    public Boolean getTraining() {
        return training;
    }
    public void setTraining(Boolean training) {
        this.training = training;
    }
    public Double getHours() {
        return DateUtils.calculateTime(this.selectedHourBegin,
                this.selectedMinuteBegin,
                this.selectedHourEnd,
                this.selectedMinuteEnd);
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
    public Long getEmployeeContractId() {
        return employeeContractId;
    }
    public void setEmployeeContractId(Long employeeContractId) {
        this.employeeContractId = employeeContractId;
    }
    public String getJiraTicketKey() {
        return jiraTicketKey;
    }
    public void setJiraTicketKey(String jiraTicketKey) {
        this.jiraTicketKey = jiraTicketKey;
    }
    public String getNewJiraTicketKey() {
        return newJiraTicketKey;
    }
    public void setNewJiraTicketKey(String newJiraTicketKey) {
        this.newJiraTicketKey = newJiraTicketKey;
    }
    
    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        try {
            Employeecontract loginEmployeecontract = (Employeecontract)request.getSession().getAttribute("loginEmployeeContract");
            employeeContractId = loginEmployeecontract.getId();
        } catch (Exception e) {
            mapping.findForward("login");
        }
        
        reset();
    }
    
    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        // actually, no checks here
        return errors;
    }

	public void reset() {
      jiraTicketKey = "-1";
      newJiraTicketKey = "";
      comment = "";
      order = "";
      suborder = "";
      status = "";
      sortOfReport = "W";
      selectedHourDuration = 0;
      selectedMinuteDuration = 0;
      referenceday = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
      hours = 8.0;
      costs = 0.0;
      training = false;
      numberOfSerialDays = 0;
	}
    
}
