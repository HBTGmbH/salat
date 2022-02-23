package org.tb.bdom;

import static javax.persistence.TemporalType.DATE;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.DateUtils;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Suborder extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CUSTOMERORDER_ID")
    private Customerorder customerorder;

    /**
     * list of timereports, associated to this suborder
     */
    @OneToMany(mappedBy = "suborder")
    @Cascade(CascadeType.SAVE_UPDATE)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Timereport> timereports;

    /**
     * list of employeeorders, associated to this suborder
     */
    @OneToMany(mappedBy = "suborder")
    @Cascade(CascadeType.SAVE_UPDATE)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Employeeorder> employeeorders;

    @OneToMany(mappedBy = "parentorder")
    @Cascade(CascadeType.SAVE_UPDATE)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Suborder> suborders;

    /**
     * parentorder
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "PARENTORDER_ID")
    private Suborder parentorder;

    /**
     * Customer subordersign
     */
    private String suborder_customer;
    private char invoice;
    private String sign;
    private String description;
    private String shortdescription;
    private String currency;
    private Double hourly_rate;
    private Boolean standard;
    private Boolean commentnecessary;
    @Temporal(DATE)
    private Date fromDate;
    @Temporal(DATE)
    private Date untilDate;
    private Double debithours;
    private Byte debithoursunit;
    /**
     * Hide in select boxes
     */
    private Boolean hide;

    /**
     * No employee order content for employee orders
     */
    @Column(name = "NOEMPLOYEEORDERCONTENT")
    private Boolean noEmployeeOrderContent;

    /**
     * Default-Flag for projectbased Training
     */
    private Boolean trainingFlag;

    /**
     * Flag for fixed price proposal
     */
    private Boolean fixedPrice;

    public void addSuborder(Suborder child) {
        if (suborders == null) {
            suborders = new LinkedList<>();
        }
        suborders.add(child);
    }

    public void setParentorder(Suborder parentorder) {
        if (parentorder != null && parentorder.getCustomerorder() != customerorder) {
            String msg = "parentorder must have same customerorder. Expected customerorderId="
                    + customerorder.getId() + ", but was " + parentorder.getCustomerorder().getId();
            throw new IllegalArgumentException(msg);
        }
        this.parentorder = parentorder;
    }

    public Boolean getNoEmployeeOrderContent() {
        return noEmployeeOrderContent != null && noEmployeeOrderContent;
    }

    public Boolean getCommentnecessary() {
        return Objects.requireNonNullElse(commentnecessary, false);
    }

    public String getShortdescription() {
        if (shortdescription == null || shortdescription.equals("")) {
            if (description == null) {
                description = "";
            }
            if (description.length() > 20) {
                return description.substring(0, 17) + "...";
            } else {
                return description;
            }
        }
        return shortdescription;
    }

    public Date getFromDate() {
        if (fromDate == null) {
            if (parentorder != null) {
                return parentorder.getFromDate();
            }
            return getCustomerorder().getFromDate();
        }
        return fromDate;
    }

    /**
     * @return the hide
     */
    public boolean isHide() {
        return hide != null && hide;
    }

    public String getTimeString() {
        String result = DateUtils.format(getFromDate()) + " - ";
        if (getUntilDate() != null) {
            result += DateUtils.format(getUntilDate());
        }
        return result;
    }

    public boolean getOpenEnd() {
        return getUntilDate() == null;
    }

    public Date getUntilDate() {
        if (untilDate == null) {
            if (parentorder != null) {
                return parentorder.getUntilDate();
            }
            return customerorder.getUntilDate();
        }
        return untilDate;
    }

    public String getFormattedUntilDate() {
        Date untilDate = getUntilDate();
        if (untilDate != null) {
            return DateUtils.format(untilDate);
        }
        return "";
    }

    public String getSignAndDescription() {
        return getSign() + " - " + getShortdescription();
    }

    public String getSignAndDescriptionWithExpirationDate() {
        String result = getSign() + " - " + getShortdescription();
        Date from = getFromDate();
        Date until = getUntilDate();
        if (from != null && until != null) {
            result += " (" + DateUtils.format(from) + " - " + DateUtils.format(until) + ")";
        }
        return result;
    }

    /**
     * @return Returns true, if the {@link Suborder} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        java.util.Date now = new java.util.Date();
        return isValidAt(now);
    }

    public boolean isValidAt(java.util.Date date) {
        return !date.before(getFromDate()) && (getUntilDate() == null || !date.after(getUntilDate()));
    }

    /**
     * Checks, if time period fits to the next higher hierachical element.
     *
     * @return Returns true, if
     * - time period fits to hierachical higher {@link Suborder} if existing
     * or
     * - time period fits to associated {@link Customerorder}
     */
    public boolean getTimePeriodFitsToUpperElement() {
        if (parentorder != null) {
            return !this.getFromDate().before(parentorder.getFromDate())
                    && (parentorder.getUntilDate() == null
                    || this.getUntilDate() != null
                    && parentorder.getUntilDate() != null
                    && !this.getUntilDate().after(parentorder.getUntilDate()));
        } else {
            return !this.getFromDate().before(customerorder.getFromDate())
                    && (customerorder.getUntilDate() == null
                    || this.getUntilDate() != null
                    && customerorder.getUntilDate() != null
                    && !this.getUntilDate().after(customerorder.getUntilDate()));
        }
    }

    /**
     * @return Returns true, if the valitidy period fits to the validity period of the customer order
     */
    public boolean validityPeriodFitsToCustomerOrder() {
        return !getFromDate().before(getCustomerorder().getFromDate()) &&
                (getCustomerorder().getUntilDate() == null ||
                        getUntilDate() != null && !getUntilDate().after(getCustomerorder().getUntilDate()));
    }

    public String getInvoiceString() {
        return Character.toString(invoice);
    }

    public Character getInvoiceChar() {
        return invoice;
    }

    /**
     * Gets all children including their children and so on.
     */
    public List<Suborder> getAllChildren() {

        /* build up result list */
        final List<Suborder> allChildren = new LinkedList<>();

        /* create visitor to collect suborders */
        SuborderVisitor allChildrenCollector = allChildren::add;

        /* start visiting */
        acceptVisitor(allChildrenCollector);

        /* return result */
        return allChildren;
    }

    /**
     * Gets all {@link Timereport}s associated to the {@link Suborder} or his children, that are invalid for the given dates.
     */
    public List<Timereport> getAllTimeReportsInvalidForDates(Date begin, Date end, TimereportDAO timereportDAO) {
        /* build up result list */
        final List<Timereport> allInvalidTimeReports = new LinkedList<Timereport>();
        final Date visitorBeginDate = begin;
        final Date visitorEndDate = end;
        final TimereportDAO visitorTimereportDAO = timereportDAO;

        /* create visitor to collect suborders */
        SuborderVisitor allInvalidTimeReportsCollector = suborder -> allInvalidTimeReports.addAll(
            visitorTimereportDAO.getTimereportsBySuborderIdInvalidForDates(
                visitorBeginDate,
                visitorEndDate,
                suborder.getId()
            )
        );

        /* start visiting */
        acceptVisitor(allInvalidTimeReportsCollector);

        /* return result */
        return allInvalidTimeReports;
    }

    /**
     * Set the {@link Customerorder} for all descendants
     */
    public void setCustomerOrderForAllDescendants(Customerorder customerOrder, SuborderDAO suborderDAO, Employee loginEmployee, Suborder rootSuborder) {

        final Customerorder customerorderToSet = customerOrder;
        final SuborderDAO visitorSuborderDAO = suborderDAO;
        final Employee visitorLoginEmployee = loginEmployee;
        final Suborder visitorRootSuborder = rootSuborder;

        /* create visitor to collect suborders */
        SuborderVisitor customerOrderSetter = suborder -> {
            // do not modify root suborder
            if (visitorRootSuborder.getId() != suborder.getId()) {
                Suborder suborderToModify = visitorSuborderDAO
                        .getSuborderById(suborder.getId());
                if (suborderToModify != null) {
                    suborderToModify.setCustomerorder(customerorderToSet);
                    // save suborder
                    visitorSuborderDAO.save(suborderToModify,
                            visitorLoginEmployee);
                }
            }
        };

        /* start visiting */
        acceptVisitor(customerOrderSetter);

    }

    public void acceptVisitor(SuborderVisitor visitor) {
        visitor.visitSuborder(this);
        for (Suborder suborder : suborders) {
            suborder.acceptVisitor(visitor);
        }
    }

    @Override
    public String toString() {
        return "Suborder_" + getId() + ": (" + sign + " " + description + ")";
    }

    public Suborder copy(boolean copyroot, String creator) {
        Suborder copy = new Suborder();

        // set attrib values in copy
        copy.setCommentnecessary(commentnecessary);
        copy.setCreated(new java.util.Date());
        copy.setCreatedby(creator + "_treecopy");
        copy.setCurrency(currency);
        copy.setCustomerorder(customerorder);
        copy.setDebithours(debithours);
        copy.setDebithoursunit(debithoursunit);
        copy.setDescription(description);
        copy.setFromDate(fromDate);
        copy.setHide(hide);
        copy.setHourly_rate(hourly_rate);
        copy.setInvoice(invoice);
        copy.setNoEmployeeOrderContent(noEmployeeOrderContent);
        copy.setShortdescription(shortdescription);
        copy.setStandard(standard);
        copy.setUntilDate(untilDate);
        copy.setSign(sign);
        copy.setSuborder_customer(suborder_customer);
        copy.setFixedPrice(fixedPrice);
        copy.setTrainingFlag(trainingFlag);

        if (copyroot) {
            copy.setSign("copy_of_" + sign);
        }

        for (Suborder child : suborders) {
            Suborder childCopy = child.copy(false, creator);
            childCopy.setParentorder(copy);
            copy.addSuborder(childCopy);
        }
        return copy;
    }

}
