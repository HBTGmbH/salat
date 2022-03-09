package org.tb.invoice;

import static org.tb.common.util.TimeFormatUtils.decimalFormatMinutes;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.employee.domain.Employee;
import org.tb.order.Customerorder;
import org.tb.order.Employeeorder;
import org.tb.order.Suborder;
import org.tb.order.SuborderDAO;
import org.tb.order.SuborderVisitor;

public class InvoiceSuborderHelper extends Suborder {

    private static final long serialVersionUID = 1L;
    private final TimereportDAO timereportDAO;
    private final LocalDate fromDate;
    private final LocalDate untilDate;
    private final boolean invoicebox;
    private Suborder suborder;
    private List<InvoiceTimereportHelper> invoiceTimereportViewHelperList;
    private boolean visible;
    private int layer;
    private InvoiceSuborderActualHoursVisitor visitor = null;

    public InvoiceSuborderHelper(Suborder suborder, TimereportDAO timereportDAO, LocalDate fromDate, LocalDate untilDate, boolean invoicebox) {
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
        return invoiceTimereportViewHelperList.stream()
            .filter(v -> !print || v.isVisible())
            .map(v -> Duration.ofHours(v.getDurationhours()).plusMinutes(v.getDurationminutes()))
            .reduce(Duration.ZERO, Duration::plus)
            .toMinutes();
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

    public List<Timereport> getAllTimeReportsInvalidForDates(LocalDate begin, LocalDate end, TimereportDAO timereportDAO) {
        return suborder.getAllTimeReportsInvalidForDates(begin, end, timereportDAO);
    }

    public Boolean getCommentnecessary() {
        return suborder.getCommentnecessary();
    }

    public void setCommentnecessary(Boolean commentnecessary) {
        suborder.setCommentnecessary(commentnecessary);
    }

    public LocalDateTime getCreated() {
        return suborder.getCreated();
    }

    public String getCreatedby() {
        return suborder.getCreatedby();
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

    public Duration getDebithours() {
        return suborder.getDebithours();
    }

    public void setDebithours(Duration debithours) {
        suborder.setDebithours(debithours);
    }

    public String getDebithoursString() {
        String result = "";
        if (suborder.getDebithours() != null && !suborder.getDebithours().isZero()) {
            result = DurationUtils.format(suborder.getDebithours());
            // add decimal value - helps the backoffice
            result += " (" + DurationUtils.decimalFormat((suborder.getDebithours())) + ")";
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

    public LocalDate getFromDate() {
        return suborder.getFromDate();
    }

    public void setFromDate(LocalDate fromDate) {
        suborder.setFromDate(fromDate);
    }

    public boolean isHide() {
        return suborder.isHide();
    }

    public void setHide(Boolean hide) {
        suborder.setHide(hide);
    }

    public Long getId() {
        return suborder.getId();
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

    public LocalDateTime getLastupdate() {
        return suborder.getLastupdate();
    }

    public String getLastupdatedby() {
        return suborder.getLastupdatedby();
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

    public LocalDate getUntilDate() {
        return suborder.getUntilDate();
    }

    public void setUntilDate(LocalDate untilDate) {
        suborder.setUntilDate(untilDate);
    }

    public Integer getUpdatecounter() {
        return suborder.getUpdatecounter();
    }

    public void setCustomerOrderForAllDescendants(Customerorder customerOrder,
                                                  SuborderDAO suborderDAO, Employee loginEmployee,
                                                  Suborder rootSuborder) {
        suborder.setCustomerOrderForAllDescendants(customerOrder, suborderDAO,
                loginEmployee, rootSuborder);
    }

    public boolean validityPeriodFitsToCustomerOrder() {
        return suborder.validityPeriodFitsToCustomerOrder();
    }
}
