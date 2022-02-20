package org.tb.bdom;

import static javax.persistence.TemporalType.DATE;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.persistence.Entity;
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
import org.tb.GlobalConstants;
import org.tb.bdom.comparators.SubOrderComparator;
import org.tb.util.DateUtils;

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
    @Temporal(DATE)
    private Date fromDate;
    @Temporal(DATE)
    private Date untilDate;
    private String sign;
    private String description;
    private String shortdescription;
    private String currency;
    private Double hourly_rate;
    private Double debithours;
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
        Date untilDate = getUntilDate();
        if (untilDate != null) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
            return simpleDateFormat.format(untilDate);
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
        return isValidAt(DateUtils.now());
    }

    public boolean isValidAt(java.util.Date date) {
        return !date.before(getFromDate()) && (getUntilDate() == null || !date.after(getUntilDate()));
    }

}
