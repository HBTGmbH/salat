package org.tb.order.domain;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.common.LocalDateRange;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.domain.DurationMinutesConverter;
import org.tb.common.util.DateUtils;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Suborder extends AuditedEntity implements Serializable {

    public static enum VisitorDirection { PARENT, SUBORDERS }

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CUSTOMERORDER_ID")
    private Customerorder customerorder;

    /**
     * list of employeeorders, associated to this suborder
     */
    @OneToMany(mappedBy = "suborder")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Employeeorder> employeeorders;

    @OneToMany(mappedBy = "parentorder", cascade = CascadeType.PERSIST)
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
    @Lob
    @Column(columnDefinition = "text")
    private String description;
    private String shortdescription;
    private Boolean standard;
    private Boolean commentnecessary;
    private LocalDate fromDate;
    private LocalDate untilDate;

    @Convert(converter = DurationMinutesConverter.class)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Duration debitMinutes;

    private Byte debithoursunit;
    /**
     * Hide in select boxes
     */
    private Boolean hide;

    /**
     * Default-Flag for projectbased Training
     */
    private Boolean trainingFlag;

    /**
     * Flag for fixed price proposal
     */
    private Boolean fixedPrice;

    /**
     * Overrides order type in customer order if set.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "orderType", columnDefinition = "varchar(255)")
    private OrderType orderType;

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

    public Boolean getCommentnecessary() {
        return Objects.requireNonNullElse(commentnecessary, false);
    }

    public String getShortdescription() {
        if (shortdescription == null || shortdescription.isEmpty()) {
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

    public LocalDate getFromDate() {
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

    public String getFormattedUntilDate() {
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
        LocalDate from = getFromDate();
        LocalDate until = getUntilDate();
        if (from != null && until != null) {
            result += " (" + DateUtils.format(from) + " - " + DateUtils.format(until) + ")";
        }
        return result;
    }

    public String getCompleteOrderSignAndDescription() {
        return getCompleteOrderSign() + " - " + getShortdescription();
    }

    /**
     * @return Returns true, if the {@link Suborder} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        LocalDate now = DateUtils.today();
        return untilDate == null || !now.isAfter(untilDate);
    }

    public LocalDateRange getValidity() {
        LocalDate from = getFromDate();
        LocalDate until = getUntilDate();
        return new LocalDateRange(from, until);
    }

    public boolean isValidAt(LocalDate date) {
        return !date.isBefore(getFromDate()) && (getUntilDate() == null || !date.isAfter(getUntilDate()));
    }

    public LocalDate getEffectiveUntilDate() {
        if (untilDate == null) {
            return customerorder.getUntilDate();
        }
        return untilDate;
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
            return !this.getFromDate().isBefore(parentorder.getFromDate())
                    && (parentorder.getUntilDate() == null
                    || this.getUntilDate() != null
                    && parentorder.getUntilDate() != null
                    && !this.getUntilDate().isAfter(parentorder.getUntilDate()));
        } else {
            return !this.getFromDate().isBefore(customerorder.getFromDate())
                    && (customerorder.getUntilDate() == null
                    || this.getUntilDate() != null
                    && customerorder.getUntilDate() != null
                    && !this.getUntilDate().isAfter(customerorder.getUntilDate()));
        }
    }

    /**
     * @return Returns true, if the valitidy period fits to the validity period of the customer order
     */
    public boolean validityPeriodFitsToCustomerOrder() {
        return !getFromDate().isBefore(getCustomerorder().getFromDate()) &&
                (getCustomerorder().getUntilDate() == null ||
                        getUntilDate() != null && !getUntilDate().isAfter(getCustomerorder().getUntilDate()));
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

    public void acceptVisitor(SuborderVisitor visitor) {
        acceptVisitor(visitor, VisitorDirection.SUBORDERS);
    }

    public void acceptVisitor(SuborderVisitor visitor, VisitorDirection direction) {
        if(direction == VisitorDirection.PARENT && parentorder != null) {
            parentorder.acceptVisitor(visitor, direction);
        }
        visitor.visitSuborder(this);
        if(direction == VisitorDirection.SUBORDERS && suborders != null) {
            for (Suborder suborder : suborders) {
                suborder.acceptVisitor(visitor, direction);
            }
        }
    }

    @Override
    public String toString() {
        return "Suborder_" + getId() + ": (" + sign + " " + description + ")";
    }

    public Duration getDebithours() {
        return debitMinutes; // its a Duration - hours or minutes make no difference
    }

    public void setDebithours(Duration value) {
        debitMinutes = value; // its a Duration - hours or minutes make no difference
    }

    public List<Suborder> withParents() {
        List<Suborder> result = new LinkedList<>();
        acceptVisitor((suborder) -> {
            result.add(suborder);
        }, VisitorDirection.PARENT);
        return result.reversed();
    }

    public String getCompleteOrderSign() {
        StringBuilder result = new StringBuilder();
        acceptVisitor((suborder) -> {
            if(result.isEmpty()) {
                result.append(suborder.getCustomerorder().getSign());
            }
            result.append("/");
            result.append(suborder.getSign());
        }, VisitorDirection.PARENT);
        return result.toString();
    }

    public String getCompleteOrderDescription(boolean shortDescription, boolean useCustomerDescription) {
        StringBuilder result = new StringBuilder();
        acceptVisitor((suborder) -> {
            if(result.isEmpty()) {
                if(useCustomerDescription && !isEmpty(suborder.getCustomerorder().getOrder_customer())) {
                    result.append(suborder.getCustomerorder().getOrder_customer());
                } else if(shortDescription && !isEmpty(suborder.getCustomerorder().getShortdescription())) {
                    result.append(suborder.getCustomerorder().getSign()).append(" ");
                    result.append(suborder.getCustomerorder().getShortdescription());
                } else {
                    result.append(suborder.getCustomerorder().getSign()).append(" ");
                    result.append(suborder.getCustomerorder().getDescription());
                }
            }
            result.append(" / ");
            if(useCustomerDescription && !isEmpty(suborder.getSuborder_customer())) {
                result.append(suborder.getSuborder_customer());
            } else if(shortDescription && !isEmpty(suborder.getShortdescription())) {
                result.append(suborder.getSign()).append(" ");
                result.append(suborder.getShortdescription());
            } else {
                result.append(suborder.getSign()).append(" ");
                result.append(suborder.getDescription());
            }
        }, VisitorDirection.PARENT);
        return result.toString();
    }

    public OrderType getEffectiveOrderType() {
        if(orderType != null) {
            return orderType;
        }
        return customerorder.getOrderType();
    }

}
