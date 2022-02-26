package org.tb.order;

import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.common.util.DateUtils.format;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.common.AuditedEntity;
import org.tb.common.util.DateUtils;
import org.tb.customer.Customer;
import org.tb.employee.Employee;

/**
 * Bean for table 'customerorder'.
 *
 * @author oda
 */
@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Customerorder extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "CUSTOMER_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Customer customer;

    /**
     * list of suborders, associated to this customerorder
     */
    @OneToMany(mappedBy = "customerorder")
    @Cascade(CascadeType.SAVE_UPDATE)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Suborder> suborders;

    /**
     * Responsible of Customer
     */
    private String responsible_customer_technical;
    private String responsible_customer_contractually;

    /**
     * Responsible employee of HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "RESPONSIBLE_HBT_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employee responsible_hbt;

    /**
     * contractually responsible employee of HBT
     */
    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "RESPONSIBLE_HBT_CONTRACTUALLY_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employee respEmpHbtContract;

    /**
     * Orderer of Customer
     */
    private String order_customer;
    private LocalDate fromDate;
    private LocalDate untilDate;
    private String sign;
    private String description;
    private String shortdescription;
    private int debitMinutes;
    private Byte debithoursunit;
    private Integer statusreport;
    /**
     * Hide in select boxes
     */
    private Boolean hide;

    public Integer getStatusreport() {
        if (statusreport == null) {
            return 0;
        }
        return statusreport;
    }

    public String getFormattedUntilDate() {
        LocalDate untilLocalDate = getUntilDate();
        if (untilLocalDate != null) {
            return format(untilDate);
        }
        return "";
    }

    public List<Suborder> getSuborders() {
        suborders.sort(SubOrderComparator.INSTANCE);
        return suborders;
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

    public String getSignAndDescription() {
        return sign + " - " + getShortdescription();
    }

    public Boolean getHide() {
        if (hide == null) {
            return false;
        }
        return hide;
    }

    /**
     * @return Returns true, if the {@link Customerorder} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        return isValidAt(DateUtils.today());
    }

    public boolean isValidAt(LocalDate date) {
        return !date.isBefore(getFromDate()) && (getUntilDate() == null || !date.isAfter(getUntilDate()));
    }

    protected Double getDebithours() {
        return BigDecimal
            .valueOf(debitMinutes)
            .setScale(2)
            .divide(BigDecimal.valueOf(MINUTES_PER_HOUR))
            .doubleValue();
    }

    public void setDebithours(Double value) {
        debitMinutes = BigDecimal
            .valueOf(value)
            .setScale(2)
            .multiply(BigDecimal.valueOf(MINUTES_PER_HOUR))
            .setScale(0)
            .intValue();
    }

}