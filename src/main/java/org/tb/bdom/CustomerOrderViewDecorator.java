package org.tb.bdom;

import org.tb.GlobalConstants;
import org.tb.persistence.TimereportDAO;

import java.util.Date;
import java.util.List;

public class CustomerOrderViewDecorator extends Customerorder {

    private static final long serialVersionUID = 1L; // 456L;

    private TimereportDAO timereportDAO;
    private Customerorder customerOrder;


    public CustomerOrderViewDecorator(TimereportDAO timereportDAO, Customerorder customerOrder) {
        this.timereportDAO = timereportDAO;
        this.customerOrder = customerOrder;
    }

    public Double getDifference() {

        if ((this.customerOrder.getDebithours() != null && this.customerOrder.getDebithours() > 0.0)
                && (this.customerOrder.getDebithoursunit() == null || this.customerOrder.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {

            Double rounded, notRounded;
            notRounded = (this.customerOrder.getDebithours() - getDuration());
            rounded = Math.round(notRounded * 100) / 100.0;

            return rounded;
        } else {
            return null;
        }
    }

    public double getDuration() {
        Long durationMinutes = timereportDAO.getTotalDurationMinutesForCustomerOrder(customerOrder.getId());

        double totalTime = durationMinutes.doubleValue() / GlobalConstants.MINUTES_PER_HOUR;

        /* round totalTime */
        totalTime *= 100.0;
        long roundedTime = Math.round(totalTime);
        totalTime = roundedTime / 100.0;

        /* return result */
        return totalTime;
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        return customerOrder.equals(obj);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getCreated()
     */
    @Override
    public Date getCreated() {
        return customerOrder.getCreated();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setCreated(java.util.Date)
     */
    @Override
    public void setCreated(Date created) {
        customerOrder.setCreated(created);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getCreatedby()
     */
    @Override
    public String getCreatedby() {
        return customerOrder.getCreatedby();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setCreatedby(java.lang.String)
     */
    @Override
    public void setCreatedby(String createdby) {
        customerOrder.setCreatedby(createdby);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getCurrency()
     */
    @Override
    public String getCurrency() {
        return customerOrder.getCurrency();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setCurrency(java.lang.String)
     */
    @Override
    public void setCurrency(String currency) {
        customerOrder.setCurrency(currency);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getCurrentlyValid()
     */
    @Override
    public boolean getCurrentlyValid() {
        return customerOrder.getCurrentlyValid();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getCustomer()
     */
    @Override
    public Customer getCustomer() {
        return customerOrder.getCustomer();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setCustomer(org.tb.bdom.Customer)
     */
    @Override
    public void setCustomer(Customer customer) {
        customerOrder.setCustomer(customer);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getDebithours()
     */
    @Override
    public Double getDebithours() {
        return customerOrder.getDebithours();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setDebithours(java.lang.Double)
     */
    @Override
    public void setDebithours(Double debithours) {
        customerOrder.setDebithours(debithours);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getDebithoursunit()
     */
    @Override
    public Byte getDebithoursunit() {
        return customerOrder.getDebithoursunit();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setDebithoursunit(java.lang.Byte)
     */
    @Override
    public void setDebithoursunit(Byte debithoursunit) {
        customerOrder.setDebithoursunit(debithoursunit);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getDescription()
     */
    @Override
    public String getDescription() {
        return customerOrder.getDescription();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setDescription(java.lang.String)
     */
    @Override
    public void setDescription(String description) {
        customerOrder.setDescription(description);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getFromDate()
     */
    @Override
    public java.sql.Date getFromDate() {
        return customerOrder.getFromDate();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setFromDate(java.sql.Date)
     */
    @Override
    public void setFromDate(java.sql.Date fromDate) {
        customerOrder.setFromDate(fromDate);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getHide()
     */
    @Override
    public Boolean getHide() {
        return customerOrder.getHide();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setHide(java.lang.Boolean)
     */
    @Override
    public void setHide(Boolean hide) {
        customerOrder.setHide(hide);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getHourly_rate()
     */
    @Override
    public Double getHourly_rate() {
        return customerOrder.getHourly_rate();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setHourly_rate(java.lang.Double)
     */
    @Override
    public void setHourly_rate(Double hourly_rate) {
        customerOrder.setHourly_rate(hourly_rate);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getId()
     */
    @Override
    public long getId() {
        return customerOrder.getId();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setId(long)
     */
    @Override
    public void setId(long id) {
        customerOrder.setId(id);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getLastupdate()
     */
    @Override
    public Date getLastupdate() {
        return customerOrder.getLastupdate();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setLastupdate(java.util.Date)
     */
    @Override
    public void setLastupdate(Date lastupdate) {
        customerOrder.setLastupdate(lastupdate);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getLastupdatedby()
     */
    @Override
    public String getLastupdatedby() {
        return customerOrder.getLastupdatedby();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setLastupdatedby(java.lang.String)
     */
    @Override
    public void setLastupdatedby(String lastupdatedby) {
        customerOrder.setLastupdatedby(lastupdatedby);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getOrder_customer()
     */
    @Override
    public String getOrder_customer() {
        return customerOrder.getOrder_customer();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setOrder_customer(java.lang.String)
     */
    @Override
    public void setOrder_customer(String orderCustomer) {
        customerOrder.setOrder_customer(orderCustomer);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getRespEmpHbtContract()
     */
    @Override
    public Employee getRespEmpHbtContract() {
        return customerOrder.getRespEmpHbtContract();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setRespEmpHbtContract(org.tb.bdom.Employee)
     */
    @Override
    public void setRespEmpHbtContract(Employee respEmpHbtContract) {
        customerOrder.setRespEmpHbtContract(respEmpHbtContract);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getResponsible_customer_contractually()
     */
    @Override
    public String getResponsible_customer_contractually() {
        return customerOrder.getResponsible_customer_contractually();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setResponsible_customer_contractually(java.lang.String)
     */
    @Override
    public void setResponsible_customer_contractually(String responsible_customer_contractually) {
        customerOrder.setResponsible_customer_contractually(responsible_customer_contractually);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getResponsible_customer_technical()
     */
    @Override
    public String getResponsible_customer_technical() {
        return customerOrder.getResponsible_customer_technical();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setResponsible_customer_technical(java.lang.String)
     */
    @Override
    public void setResponsible_customer_technical(String responsible_customer_technical) {
        customerOrder.setResponsible_customer_technical(responsible_customer_technical);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getResponsible_hbt()
     */
    @Override
    public Employee getResponsible_hbt() {
        return customerOrder.getResponsible_hbt();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setResponsible_hbt(org.tb.bdom.Employee)
     */
    @Override
    public void setResponsible_hbt(Employee responsible_hbt) {
        customerOrder.setResponsible_hbt(responsible_hbt);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getShortdescription()
     */
    @Override
    public String getShortdescription() {
        return customerOrder.getShortdescription();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setShortdescription(java.lang.String)
     */
    @Override
    public void setShortdescription(String shortdescription) {
        customerOrder.setShortdescription(shortdescription);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getSign()
     */
    @Override
    public String getSign() {
        return customerOrder.getSign();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setSign(java.lang.String)
     */
    @Override
    public void setSign(String sign) {
        customerOrder.setSign(sign);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getSignAndDescription()
     */
    @Override
    public String getSignAndDescription() {
        return customerOrder.getSignAndDescription();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getStatusreport()
     */
    @Override
    public Integer getStatusreport() {
        return customerOrder.getStatusreport();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setStatusreport(java.lang.Integer)
     */
    @Override
    public void setStatusreport(Integer statusreport) {
        customerOrder.setStatusreport(statusreport);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getSuborders()
     */
    @Override
    public List<Suborder> getSuborders() {
        return customerOrder.getSuborders();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setSuborders(java.util.List)
     */
    @Override
    public void setSuborders(List<Suborder> suborders) {
        customerOrder.setSuborders(suborders);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getUntilDate()
     */
    @Override
    public java.sql.Date getUntilDate() {
        return customerOrder.getUntilDate();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setUntilDate(java.sql.Date)
     */
    @Override
    public void setUntilDate(java.sql.Date untilDate) {
        customerOrder.setUntilDate(untilDate);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#getUpdatecounter()
     */
    @Override
    public Integer getUpdatecounter() {
        return customerOrder.getUpdatecounter();
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#setUpdatecounter(java.lang.Integer)
     */
    @Override
    public void setUpdatecounter(Integer updatecounter) {
        customerOrder.setUpdatecounter(updatecounter);
    }

    /* (non-Javadoc)
     * @see org.tb.bdom.Customerorder#hashCode()
     */
    @Override
    public int hashCode() {
        return customerOrder.hashCode();
    }


}
