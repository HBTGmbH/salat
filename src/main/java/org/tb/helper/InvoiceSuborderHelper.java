package org.tb.helper;

import static org.tb.util.TimeFormatUtils.decimalFormatHours;
import static org.tb.util.TimeFormatUtils.decimalFormatMinutes;
import static org.tb.util.TimeFormatUtils.timeFormatHours;
import static org.tb.util.TimeFormatUtils.timeFormatMinutes;

import org.tb.bdom.*;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;

import java.util.Date;
import java.util.List;

public class InvoiceSuborderHelper extends Suborder {

    private static final long serialVersionUID = 1L;
    private final TimereportDAO timereportDAO;
    private final Date fromDate;
    private final Date untilDate;
    private final boolean invoicebox;
    private Suborder suborder;
    private List<InvoiceTimereportHelper> invoiceTimereportViewHelperList;
    private boolean visible;
    private int layer;
    private InvoiceSuborderActualHoursVisitor visitor = null;

    public InvoiceSuborderHelper(Suborder suborder, TimereportDAO timereportDAO, Date fromDate, Date untilDate, boolean invoicebox) {
        if (suborder == null) {
            throw new IllegalArgumentException("suborder must not be null!");
        }
        this.timereportDAO = timereportDAO;
        this.suborder = suborder;
        this.visible = true;
        this.fromDate = fromDate;
        this.untilDate = untilDate;
        this.invoicebox = invoicebox;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public Suborder getSuborder() {
        return suborder;
    }

    public void setSuborder(Suborder suborder) {
        this.suborder = suborder;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getDuration() {
        if (this.visitor == null) {
            visitor = new InvoiceSuborderActualHoursVisitor(timereportDAO, fromDate, untilDate, invoicebox);
            acceptVisitor(visitor);
        }
        return visitor.getTotalTime();
    }

    public long getDurationInMinutes() {
        if (this.visitor == null) {
            visitor = new InvoiceSuborderActualHoursVisitor(timereportDAO, fromDate, untilDate, invoicebox);
            acceptVisitor(visitor);
        }
        return visitor.getTotalMinutes();
    }

    public String getActualhours() {
        return getActualhoursHelper(false);
    }

    public String getActualhoursPrint() {
        return getActualhoursHelper(true);
    }

    private String getActualhoursHelper(boolean print) {
        long actualminutes = getTotalActualminutesHelper(print);
        return timeFormatMinutes(actualminutes) + " (" + decimalFormatMinutes(actualminutes) + ")";
    }

    public long getTotalActualminutes() {
        return getTotalActualminutesHelper(false);
    }

    public long getTotalActualminutesPrint() {
        return getTotalActualminutesHelper(true);
    }

    private long getTotalActualminutesHelper(boolean print) {
        long actualminutes = 0;
        for (InvoiceTimereportHelper invoiceTimereportViewHelper : invoiceTimereportViewHelperList) {
            if (!print) {
                actualminutes += invoiceTimereportViewHelper.getDurationminutes();
                actualminutes += invoiceTimereportViewHelper.getDurationhours() * 60;
            } else if (invoiceTimereportViewHelper.isVisible()) {
                actualminutes += invoiceTimereportViewHelper.getDurationminutes();
                actualminutes += invoiceTimereportViewHelper.getDurationhours() * 60;
            }
        }
        return actualminutes;
    }

    public List<InvoiceTimereportHelper> getInvoiceTimereportViewHelperList() {
        return invoiceTimereportViewHelperList;
    }

    public void setInvoiceTimereportViewHelperList(List<InvoiceTimereportHelper> invoiceTimereportViewHelperList) {
        this.invoiceTimereportViewHelperList = invoiceTimereportViewHelperList;
    }

    public void acceptVisitor(SuborderVisitor visitor) {
        suborder.acceptVisitor(visitor);
    }

    public boolean equals(Object obj) {
        return suborder.equals(obj);
    }

    public List<Suborder> getAllChildren() {
        return suborder.getAllChildren();
    }

    public List<Timereport> getAllTimeReportsInvalidForDates(Date begin, Date end, TimereportDAO timereportDAO) {
        return suborder.getAllTimeReportsInvalidForDates(begin, end, timereportDAO);
    }

    public Boolean getCommentnecessary() {
        return suborder.getCommentnecessary();
    }

    public void setCommentnecessary(Boolean commentnecessary) {
        suborder.setCommentnecessary(commentnecessary);
    }

    public Date getCreated() {
        return suborder.getCreated();
    }

    public void setCreated(Date created) {
        suborder.setCreated(created);
    }

    public String getCreatedby() {
        return suborder.getCreatedby();
    }

    public void setCreatedby(String createdby) {
        suborder.setCreatedby(createdby);
    }

    public String getCurrency() {
        return suborder.getCurrency();
    }

    public void setCurrency(String currency) {
        suborder.setCurrency(currency);
    }

    public boolean getCurrentlyValid() {
        return suborder.getCurrentlyValid();
    }

    public Customerorder getCustomerorder() {
        return suborder.getCustomerorder();
    }

    public void setCustomerorder(Customerorder order) {
        suborder.setCustomerorder(order);
    }

    public Double getDebithours() {
        return suborder.getDebithours();
    }

    public void setDebithours(Double debithours) {
        suborder.setDebithours(debithours);
    }

    public String getDebithoursString() {
        String result = "";
        if (suborder.getDebithours() != null && suborder.getDebithours() != 0.0) {
            result = timeFormatHours(suborder.getDebithours());
            // add decimal value - helps the backoffice
            result += " (" + decimalFormatHours(suborder.getDebithours()) + ")";
        }
        return result;
    }

    public Byte getDebithoursunit() {
        return suborder.getDebithoursunit();
    }

    public void setDebithoursunit(Byte debithoursunit) {
        suborder.setDebithoursunit(debithoursunit);
    }

    public String getDescription() {
        return suborder.getDescription();
    }

    public void setDescription(String description) {
        suborder.setDescription(description);
    }

    public String getSuborder_customer() {
        return suborder.getSuborder_customer();
    }

    public void setSuborder_customer(String suborder_customer) {
        suborder.setSuborder_customer(suborder_customer);
    }

    public List<Employeeorder> getEmployeeorders() {
        return suborder.getEmployeeorders();
    }

    public void setEmployeeorders(List<Employeeorder> employeeorders) {
        suborder.setEmployeeorders(employeeorders);
    }

    public Date getFromDate() {
        return suborder.getFromDate();
    }

    public void setFromDate(Date fromDate) {
        suborder.setFromDate(fromDate);
    }

    public boolean isHide() {
        return suborder.isHide();
    }

    public void setHide(Boolean hide) {
        suborder.setHide(hide);
    }

    public Double getHourly_rate() {
        return suborder.getHourly_rate();
    }

    public void setHourly_rate(Double hourly_rate) {
        suborder.setHourly_rate(hourly_rate);
    }

    public long getId() {
        return suborder.getId();
    }

    public void setId(long id) {
        suborder.setId(id);
    }

    public char getInvoice() {
        return suborder.getInvoice();
    }

    public void setInvoice(char invoice) {
        suborder.setInvoice(invoice);
    }

    public Character getInvoiceChar() {
        return suborder.getInvoiceChar();
    }

    public String getInvoiceString() {
        return suborder.getInvoiceString();
    }

    public Date getLastupdate() {
        return suborder.getLastupdate();
    }

    public void setLastupdate(Date lastupdate) {
        suborder.setLastupdate(lastupdate);
    }

    public String getLastupdatedby() {
        return suborder.getLastupdatedby();
    }

    public void setLastupdatedby(String lastupdatedby) {
        suborder.setLastupdatedby(lastupdatedby);
    }

    public Boolean getNoEmployeeOrderContent() {
        return suborder.getNoEmployeeOrderContent();
    }

    public void setNoEmployeeOrderContent(Boolean noEmployeeOrderContent) {
        suborder.setNoEmployeeOrderContent(noEmployeeOrderContent);
    }

    public Suborder getParentorder() {
        return suborder.getParentorder();
    }

    public void setParentorder(Suborder parentorder) {
        suborder.setParentorder(parentorder);
    }

    public String getShortdescription() {
        return suborder.getShortdescription();
    }

    public void setShortdescription(String shortdescription) {
        suborder.setShortdescription(shortdescription);
    }

    public String getSign() {
        return suborder.getSign();
    }

    public void setSign(String sign) {
        suborder.setSign(sign);
    }

    public String getSignAndDescription() {
        return suborder.getSignAndDescription();
    }

    public Boolean getStandard() {
        return suborder.getStandard();
    }

    public void setStandard(Boolean standard) {
        suborder.setStandard(standard);
    }

    public List<Suborder> getSuborders() {
        return suborder.getSuborders();
    }

    public void setSuborders(List<Suborder> suborders) {
        suborder.setSuborders(suborders);
    }

    public boolean getTimePeriodFitsToUpperElement() {
        return suborder.getTimePeriodFitsToUpperElement();
    }

    public List<Timereport> getTimereports() {
        return suborder.getTimereports();
    }

    public void setTimereports(List<Timereport> timereports) {
        suborder.setTimereports(timereports);
    }

    public Date getUntilDate() {
        return suborder.getUntilDate();
    }

    public void setUntilDate(Date untilDate) {
        suborder.setUntilDate(untilDate);
    }

    public Integer getUpdatecounter() {
        return suborder.getUpdatecounter();
    }

    public void setUpdatecounter(Integer updatecounter) {
        suborder.setUpdatecounter(updatecounter);
    }

    public int hashCode() {
        return suborder.hashCode();
    }

    public void setCustomerOrderForAllDescendants(Customerorder customerOrder,
                                                  SuborderDAO suborderDAO, Employee loginEmployee,
                                                  Suborder rootSuborder) {
        suborder.setCustomerOrderForAllDescendants(customerOrder, suborderDAO,
                loginEmployee, rootSuborder);
    }

    public String toString() {
        return suborder.toString();
    }

    public boolean validityPeriodFitsToCustomerOrder() {
        return suborder.validityPeriodFitsToCustomerOrder();
    }
}
