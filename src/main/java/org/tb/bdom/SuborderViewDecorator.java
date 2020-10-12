package org.tb.bdom;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import org.tb.GlobalConstants;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;

public class SuborderViewDecorator extends Suborder {
    
    private static final long serialVersionUID = 1L; // 123L;
    
    private final TimereportDAO timereportDAO;
    private final Suborder suborder;
    private Double duration = null;
    private Double durationNotInvoiceable = null;
    
    public SuborderViewDecorator(TimereportDAO timereportDAO, Suborder suborder) {
        this.timereportDAO = timereportDAO;
        this.suborder = suborder;
    }
    
    public Double getDifference() {
        
        if (this.suborder.getDebithours() != null && this.suborder.getDebithours() > 0.0
                && (this.suborder.getDebithoursunit() == null || this.suborder.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
            
            Double rounded, notRounded;
            notRounded = this.suborder.getDebithours() - getDuration();
            rounded = Math.round(notRounded * 100) / 100.0;
            
            return rounded;
        } else {
            return null;
        }
    }
    
    private static void generateListOfDescendants(Suborder so, boolean isInvoiceable, List<Long> listOfDescendents) {
    	if(isInvoiceable != (so.getInvoice() == 'y' || so.getInvoice() == 'Y') ) {
    		return;
    	}
    	listOfDescendents.add(so.getId());
    	if(so.getSuborders() != null) {
    		for(Suborder child : so.getSuborders()) {
    			generateListOfDescendants(child, isInvoiceable, listOfDescendents);
    		}
    	}
    }
    
    public double getDuration() {
    	if(this.duration == null) {
	    	if(suborder.getInvoice() == 'n' || suborder.getInvoice() == 'N') {
	    		return 0;
	    	}
	    	
	    	List<Long> descendants = new ArrayList<>();
	    	generateListOfDescendants(suborder, true, descendants);
	
	    	this.duration = (double)(timereportDAO.getTotalDurationMinutesForSuborders(descendants) * 100 / GlobalConstants.MINUTES_PER_HOUR) / 100;
    	}
    	return this.duration;
    }
    
    public double getDurationNotInvoiceable() {
    	if(this.durationNotInvoiceable == null) {
	    	if(suborder.getInvoice() == 'y' || suborder.getInvoice() == 'Y') {
	    		return 0;
	    	}
	    	
	    	List<Long> descendants = new ArrayList<>();
	    	generateListOfDescendants(suborder, false, descendants);
	
	    	this.durationNotInvoiceable = (double)(timereportDAO.getTotalDurationMinutesForSuborders(descendants) * 100 / GlobalConstants.MINUTES_PER_HOUR) / 100;
    	}
    	return this.durationNotInvoiceable;
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#acceptVisitor(org.tb.bdom.CustomerOrderVisitor)
     */
    @Override
    public void acceptVisitor(SuborderVisitor visitor) {
        suborder.acceptVisitor(visitor);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        return suborder.equals(obj);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getAllChildren()
     */
    @Override
    public List<Suborder> getAllChildren() {
        return suborder.getAllChildren();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getAllTimeReportsInvalidForDates(java.sql.Date, java.sql.Date, org.tb.persistence.TimereportDAO)
     */
    @Override
    public List<Timereport> getAllTimeReportsInvalidForDates(Date begin, Date end, TimereportDAO timereportDAO) {
        return suborder.getAllTimeReportsInvalidForDates(begin, end, timereportDAO);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getCommentnecessary()
     */
    @Override
    public Boolean getCommentnecessary() {
        return suborder.getCommentnecessary();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getCreated()
     */
    @Override
    public java.util.Date getCreated() {
        return suborder.getCreated();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getCreatedby()
     */
    @Override
    public String getCreatedby() {
        return suborder.getCreatedby();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getCurrency()
     */
    @Override
    public String getCurrency() {
        return suborder.getCurrency();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getCurrentlyValid()
     */
    @Override
    public boolean getCurrentlyValid() {
        return suborder.getCurrentlyValid();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getCustomerorder()
     */
    @Override
    public Customerorder getCustomerorder() {
        return suborder.getCustomerorder();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getDebithours()
     */
    @Override
    public Double getDebithours() {
        return suborder.getDebithours();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getDebithoursunit()
     */
    @Override
    public Byte getDebithoursunit() {
        return suborder.getDebithoursunit();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getDescription()
     */
    @Override
    public String getDescription() {
        return suborder.getDescription();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getEmployeeorders()
     */
    @Override
    public List<Employeeorder> getEmployeeorders() {
        return suborder.getEmployeeorders();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getFromDate()
     */
    @Override
    public Date getFromDate() {
        return suborder.getFromDate();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getHide()
     */
    @Override
    public boolean isHide() {
        return suborder.isHide();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getHourly_rate()
     */
    @Override
    public Double getHourly_rate() {
        return suborder.getHourly_rate();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getId()
     */
    @Override
    public long getId() {
        return suborder.getId();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getInvoice()
     */
    @Override
    public char getInvoice() {
        return suborder.getInvoice();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getInvoiceChar()
     */
    @Override
    public Character getInvoiceChar() {
        return suborder.getInvoiceChar();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getInvoiceString()
     */
    @Override
    public String getInvoiceString() {
        return suborder.getInvoiceString();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getLastupdate()
     */
    @Override
    public java.util.Date getLastupdate() {
        return suborder.getLastupdate();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getLastupdatedby()
     */
    @Override
    public String getLastupdatedby() {
        return suborder.getLastupdatedby();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getNoEmployeeOrderContent()
     */
    @Override
    public Boolean getNoEmployeeOrderContent() {
        return suborder.getNoEmployeeOrderContent();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getParentorder()
     */
    @Override
    public Suborder getParentorder() {
        return suborder.getParentorder();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getShortdescription()
     */
    @Override
    public String getShortdescription() {
        return suborder.getShortdescription();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getSign()
     */
    @Override
    public String getSign() {
        return suborder.getSign();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getSignAndDescription()
     */
    @Override
    public String getSignAndDescription() {
        return suborder.getSignAndDescription();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getStandard()
     */
    @Override
    public Boolean getStandard() {
        return suborder.getStandard();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getSuborders()
     */
    @Override
    public List<Suborder> getSuborders() {
        return suborder.getSuborders();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getTimePeriodFitsToUpperElement()
     */
    @Override
    public boolean getTimePeriodFitsToUpperElement() {
        return suborder.getTimePeriodFitsToUpperElement();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getTimereports()
     */
    @Override
    public List<Timereport> getTimereports() {
        return suborder.getTimereports();
    }
    
    /*(non-Javadoc)
     * @see org.tb.bdom.Suborder#getTrainingFlag()
     */
    @Override
    public Boolean getTrainingFlag() {
        return suborder.getTrainingFlag();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getUntilDate()
     */
    @Override
    public Date getUntilDate() {
        return suborder.getUntilDate();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getUpdatecounter()
     */
    @Override
    public Integer getUpdatecounter() {
        return suborder.getUpdatecounter();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#hashCode()
     */
    @Override
    public int hashCode() {
        return suborder.hashCode();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setCommentnecessary(java.lang.Boolean)
     */
    @Override
    public void setCommentnecessary(Boolean commentnecessary) {
        suborder.setCommentnecessary(commentnecessary);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setCreated(java.util.Date)
     */
    @Override
    public void setCreated(java.util.Date created) {
        suborder.setCreated(created);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setCreatedby(java.lang.String)
     */
    @Override
    public void setCreatedby(String createdby) {
        suborder.setCreatedby(createdby);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setCurrency(java.lang.String)
     */
    @Override
    public void setCurrency(String currency) {
        suborder.setCurrency(currency);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setCustomerorder(org.tb.bdom.Customerorder)
     */
    @Override
    public void setCustomerorder(Customerorder order) {
        suborder.setCustomerorder(order);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setCustomerOrderForAllDescendants(org.tb.bdom.Customerorder, org.tb.persistence.SuborderDAO, org.tb.bdom.Employee, org.tb.bdom.Suborder)
     */
    @Override
    public void setCustomerOrderForAllDescendants(Customerorder customerOrder, SuborderDAO suborderDAO, Employee loginEmployee, Suborder rootSuborder) {
        suborder.setCustomerOrderForAllDescendants(customerOrder, suborderDAO,
                loginEmployee, rootSuborder);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setDebithours(java.lang.Double)
     */
    @Override
    public void setDebithours(Double debithours) {
        suborder.setDebithours(debithours);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setDebithoursunit(java.lang.Byte)
     */
    @Override
    public void setDebithoursunit(Byte debithoursunit) {
        suborder.setDebithoursunit(debithoursunit);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setDescription(java.lang.String)
     */
    @Override
    public void setDescription(String description) {
        suborder.setDescription(description);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setEmployeeorders(java.util.List)
     */
    @Override
    public void setEmployeeorders(List<Employeeorder> employeeorders) {
        suborder.setEmployeeorders(employeeorders);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setFromDate(java.sql.Date)
     */
    @Override
    public void setFromDate(Date fromDate) {
        suborder.setFromDate(fromDate);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setHide(java.lang.Boolean)
     */
    @Override
    public void setHide(Boolean hide) {
        suborder.setHide(hide);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setHourly_rate(java.lang.Double)
     */
    @Override
    public void setHourly_rate(Double hourly_rate) {
        suborder.setHourly_rate(hourly_rate);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setId(long)
     */
    @Override
    public void setId(long id) {
        suborder.setId(id);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setInvoice(char)
     */
    @Override
    public void setInvoice(char invoice) {
        suborder.setInvoice(invoice);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setLastupdate(java.util.Date)
     */
    @Override
    public void setLastupdate(java.util.Date lastupdate) {
        suborder.setLastupdate(lastupdate);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setLastupdatedby(java.lang.String)
     */
    @Override
    public void setLastupdatedby(String lastupdatedby) {
        suborder.setLastupdatedby(lastupdatedby);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setNoEmployeeOrderContent(java.lang.Boolean)
     */
    @Override
    public void setNoEmployeeOrderContent(Boolean noEmployeeOrderContent) {
        suborder.setNoEmployeeOrderContent(noEmployeeOrderContent);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setParentorder(org.tb.bdom.Suborder)
     */
    @Override
    public void setParentorder(Suborder parentorder) {
        suborder.setParentorder(parentorder);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setShortdescription(java.lang.String)
     */
    @Override
    public void setShortdescription(String shortdescription) {
        suborder.setShortdescription(shortdescription);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setSign(java.lang.String)
     */
    @Override
    public void setSign(String sign) {
        suborder.setSign(sign);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setStandard(java.lang.Boolean)
     */
    @Override
    public void setStandard(Boolean standard) {
        suborder.setStandard(standard);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setSuborders(java.util.List)
     */
    @Override
    public void setSuborders(List<Suborder> suborders) {
        suborder.setSuborders(suborders);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setTimereports(java.util.List)
     */
    @Override
    public void setTimereports(List<Timereport> timereports) {
        suborder.setTimereports(timereports);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setTimereports(java.util.List)
     */
    @Override
    public void setTrainingFlag(Boolean trainingFlag) {
        suborder.setTrainingFlag(trainingFlag);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setUntilDate(java.sql.Date)
     */
    @Override
    public void setUntilDate(Date untilDate) {
        suborder.setUntilDate(untilDate);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setUpdatecounter(java.lang.Integer)
     */
    @Override
    public void setUpdatecounter(Integer updatecounter) {
        suborder.setUpdatecounter(updatecounter);
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#toString()
     */
    @Override
    public String toString() {
        return suborder.toString();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#validityPeriodFitsToCustomerOrder()
     */
    @Override
    public boolean validityPeriodFitsToCustomerOrder() {
        return suborder.validityPeriodFitsToCustomerOrder();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#getSuborder_customer()
     */
    @Override
    public String getSuborder_customer() {
        return suborder.getSuborder_customer();
    }
    
    /* (non-Javadoc)
     * @see org.tb.bdom.Suborder#setSuborder_customer(java.lang.String)
     */
    @Override
    public void setSuborder_customer(String suborder_customer) {
        suborder.setSuborder_customer(suborder_customer);
    }
    
}
