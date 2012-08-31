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
    
    private static final long serialVersionUID = 1L;
    
    /* day, month, year of startdate */
    private String day;
    private String month;
    private String year;
    
    /* day, month, year of enddate */
    private String lastday;
    private String lastmonth;
    private String lastyear;
    
    /* yyyy-MM-dd of startdate */
    private String startdate;
    /* yyyy-MM-dd of enddate */
    private String enddate;
    
    private long employeeContractId;
    private String comment;
    private String order;
    private String suborder;
    private String status;
    private Double costs;
    private boolean training;
    private int selectedHourBegin;
    private int selectedMinuteBegin;
    private int selectedHourEnd;
    private int selectedMinuteEnd;
    private int selectedWorkHourBegin;
    private int selectedWorkMinuteBegin;
    private int selectedBreakHour;
    private int selectedBreakMinute;
    
    private long orderId;
    private long trOrderId;
    private long suborderId;
    private long trSuborderId;
    
    private String view;
    private Boolean avoidRefresh;
    private Boolean showTraining;
    
    /**
     * @return the selected startdate yyyy-MM-dd
     */
    public String getStartdate() {
        return this.startdate;
    }
    
    /**
     * @param startdate the selected startdate yyyy-MM-dd
     */
    public void setStartdate(String startdate) {
        this.startdate = startdate;
    }
    
    /**
     * @return the selected enddate yyyy-MM-dd
     */
    public String getEnddate() {
        return this.enddate;
    }
    
    /**
     * @param enddate the selected enddate yyyy-MM-dd
     */
    public void setEnddate(String enddate) {
        this.enddate = enddate;
    }
    
    /**
     * @return the selected day of startdate, '01' e.g.
     */
    public String getDay() {
        return this.day;
    }
    
    /**
     * @param day the selected day of startdate, '01' e.g.
     */
    public void setDay(String day) {
        this.day = day;
    }
    
    /**
     * @return the selected month of startdate, 'Jan' e.g. 
     */
    public String getMonth() {
        return this.month;
    }
    
    /**
     * @param month the selected month of startdate, 'Jan' e.g.
     */
    public void setMonth(String month) {
        this.month = month;
    }
    
    /**
     * @return the selected year of startdate, '2009' e.g.
     */
    public String getYear() {
        return this.year;
    }
    
    /**
     * @param year the selected year of startdate, '2009' e.g.
     */
    public void setYear(String year) {
        this.year = year;
    }
    
    /**
     * @return the selected order
     */
    public String getOrder() {
        return this.order;
    }
    
    /**
     * @param order the selected order
     */
    public void setOrder(String order) {
        this.order = order;
    }
    
    /**
     * @return the selected order ID
     */
    public long getOrderId() {
        return this.orderId;
    }
    
    /**
     * @param orderId the selected order ID
     */
    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }
    
    /**
     * @return the entered comment
     */
    public String getComment() {
        return this.comment;
    }
    
    /**
     * @param comment the entered comment
     */
    public void setComment(String comment) {
        this.comment = comment;
    }
    
    /**
     * @return the entered costs, '1.0' e.g.
     */
    public Double getCosts() {
        return this.costs;
    }
    
    /**
     * @param costs the entered costs, '1.0' e.g.
     */
    public void setCosts(Double costs) {
        this.costs = costs;
    }
    
    public boolean isTraining() {
        return training;
    }
    
    public void setTraining(boolean training) {
        this.training = training;
    }
    
    /**
     * @return selectedHourBegin;
     */
    public int getSelectedHourBegin() {
        return this.selectedHourBegin;
    }
    
    /**
     * @param selectedHourBegin
     */
    public void setSelectedHourBegin(int selectedHourBegin) {
        this.selectedHourBegin = selectedHourBegin;
    }
    
    /**
     * @return selectedHourEnd
     */
    public int getSelectedHourEnd() {
        return this.selectedHourEnd;
    }
    
    /**
     * @param selectedHourEnd
     */
    public void setSelectedHourEnd(int selectedHourEnd) {
        this.selectedHourEnd = selectedHourEnd;
    }
    
    /**
     * @return selectedMinuteBegin
     */
    public int getSelectedMinuteBegin() {
        return this.selectedMinuteBegin;
    }
    
    /**
     * @param selectedMinuteBegin
     */
    public void setSelectedMinuteBegin(int selectedMinuteBegin) {
        this.selectedMinuteBegin = selectedMinuteBegin;
    }
    
    /**
     * @return selectedMinuteEnd
     */
    public int getSelectedMinuteEnd() {
        return this.selectedMinuteEnd;
    }
    
    /**
     * @param selectedMinuteEnd
     */
    public void setSelectedMinuteEnd(int selectedMinuteEnd) {
        this.selectedMinuteEnd = selectedMinuteEnd;
    }
    
    /**
     * @return the selectedBreakHour
     */
    public int getSelectedBreakHour() {
        return this.selectedBreakHour;
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
        return this.selectedBreakMinute;
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
        return this.selectedWorkHourBegin;
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
        return this.selectedWorkMinuteBegin;
    }
    
    /**
     * @param selectedWorkMinuteBegin the selectedWorkMinuteBegin to set
     */
    public void setSelectedWorkMinuteBegin(int selectedWorkMinuteBegin) {
        this.selectedWorkMinuteBegin = selectedWorkMinuteBegin;
    }
    
    /**
     * @return status
     */
    public String getStatus() {
        return this.status;
    }
    
    /**
     * @param status
     */
    public void setStatus(String status) {
        this.status = status;
    }
    
    /**
     * @return suborder
     */
    public String getSuborder() {
        return this.suborder;
    }
    
    /**
     * @param suborder
     */
    public void setSuborder(String suborder) {
        this.suborder = suborder;
    }
    
    /**
     * @return trSuborderId
     */
    public long getTrSuborderId() {
        return trSuborderId;
    }
    
    /**
     * @param trSuborderId
     */
    public void setTrSuborderId(long trSuborderId) {
        this.trSuborderId = trSuborderId;
    }
    
    /**
     * @return trOrderId
     */
    public long getTrOrderId() {
        return trOrderId;
    }
    
    /**
     * @param trOrderId
     */
    public void setTrOrderId(long trOrderId) {
        this.trOrderId = trOrderId;
    }
    
    /**
     * @return the view
     */
    public String getView() {
        return this.view;
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
    
    public Boolean getShowTraining() {
        return showTraining;
    }
    
    public void setShowTraining(Boolean showTraining) {
        this.showTraining = showTraining;
    }
    
    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        Employeecontract employeecontract;
        if (null != request.getSession().getAttribute("currentEmployeeContract")) {
            employeecontract = (Employeecontract)request.getSession().getAttribute("currentEmployeeContract");
            employeeContractId = employeecontract.getId();
        } else {
            employeeContractId = -1;
        }
        
        avoidRefresh = false;
        showTraining = false;
        training = false;
    }
    
    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        
        // actually, no checks here
        return errors;
    }
    
}
