package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for updating a single timereport within the 'daily' display
 * 
 * @author oda
 *
 */
public class UpdateDailyReportForm extends ActionForm {
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L; // -3593978643330934662L;
    private String day;
    private String month;
    private String year;
    private String action;
    
    private String comment;
    private String order;
    private String suborder;
    private String status;
    private Double costs;
    private Boolean training;
    private int selectedDurationHour;
    private int selectedDurationMinute;
    
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
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
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
    
    public int getSelectedDurationHour() {
        return selectedDurationHour;
    }
    
    public void setSelectedDurationHour(int selectedDurationHour) {
        this.selectedDurationHour = selectedDurationHour;
    }
    
    public int getSelectedDurationMinute() {
        return selectedDurationMinute;
    }
    
    public void setSelectedDurationMinute(int selectedDurationMinute) {
        this.selectedDurationMinute = selectedDurationMinute;
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
        training = false;
    }
    
    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        
        // actually, no checks here
        return errors;
    }
    
}
